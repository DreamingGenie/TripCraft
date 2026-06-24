package com.tripcraft.plan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tripcraft.attraction.domain.Attraction;
import com.tripcraft.attraction.mapper.AttractionMapper;
import com.tripcraft.plan.client.OdsayClient;
import com.tripcraft.plan.client.TMapClient;
import com.tripcraft.plan.domain.LanePolyline;
import com.tripcraft.plan.domain.TransitCache;
import com.tripcraft.plan.dto.TransitResponse;
import com.tripcraft.plan.mapper.LanePolylineMapper;
import com.tripcraft.plan.mapper.TransitCacheMapper;
import com.tripcraft.plan.mapper.TripBlockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransitServiceImpl implements TransitService {

    private final TransitCacheMapper transitCacheMapper;
    private final LanePolylineMapper lanePolylineMapper;
    private final TripBlockMapper tripBlockMapper;
    private final AttractionMapper attractionMapper;
    private final OdsayClient odsayClient;
    private final TMapClient tMapClient;
    private final ObjectMapper objectMapper;

    private static final String MODE_PUBLIC_TRANSIT = "PUBLIC_TRANSIT";
    private static final String MODE_DRIVING = "DRIVING";
    private static final String MODE_WALKING = "WALKING";

    // 좌표 기반(커스텀 장소) 결과 in-memory 캐시.
    // by-coords 는 attraction 처럼 DB 캐시가 없어, 지도 draw·모달 open 마다 ODsay/TMap 을 생호출 →
    // 외부 API 레이트리밋에 걸려 간헐적 빈 응답(빈 모달·직선 fallback) 발생. 이를 막기 위한 세션 캐시.
    // 성공 결과만 저장한다(빈 결과는 미저장 → 다음 호출에 재시도). 단일 인스턴스 전제.
    private final java.util.Map<String, List<RouteEnrichment>> publicCoordCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Optional<TransitResponse>> tmapCoordCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int COORD_CACHE_MAX = 1000;

    private static String coordKey(BigDecimal a, BigDecimal b, BigDecimal c, BigDecimal d) {
        return String.format("%.5f,%.5f>%.5f,%.5f", a.doubleValue(), b.doubleValue(), c.doubleValue(), d.doubleValue());
    }
    private void putTmapCoord(String key, Optional<TransitResponse> v) {
        if (tmapCoordCache.size() > COORD_CACHE_MAX) tmapCoordCache.clear();
        tmapCoordCache.put(key, v);
    }

    @Override
    public Optional<TransitResponse> getTransitTime(Long fromId, Long toId, int departureHour, String mode) {
        String resolvedMode = (mode == null || mode.isBlank()) ? MODE_PUBLIC_TRANSIT : mode;

        Optional<TransitCache> cached = transitCacheMapper.findByKey(fromId, toId, departureHour, resolvedMode);
        if (cached.isPresent()) {
            TransitCache c = cached.get();
            if ("NONE".equals(c.getTransportMode())) {
                return Optional.of(TransitResponse.builder()
                        .transportMode("NONE")
                        .transferCount(0)
                        .fare(0)
                        .totalWalkM(0)
                        .build());
            }
            String roadSummary = "";
            String segJson = "[]";
            if (MODE_WALKING.equals(c.getTransportMode()) && c.getPathDetail() != null) {
                try {
                    JsonNode detail = objectMapper.readTree(c.getPathDetail());
                    roadSummary = detail.path("roadSummary").asText("");
                    segJson = objectMapper.writeValueAsString(detail.path("segments"));
                } catch (Exception ignored) {}
            }
            return Optional.of(TransitResponse.builder()
                    .durationMinutes(c.getDurationMinutes())
                    .transportMode(c.getTransportMode())
                    .transferCount(c.getTransferCount())
                    .fare(c.getFare())
                    .totalWalkM(c.getTotalWalkM())
                    .totalDistanceM(c.getTotalDistanceM())
                    .taxiFare(c.getTaxiFare())
                    .routeCoords(c.getRouteCoords())
                    .roadSummary(roadSummary.isEmpty() ? null : roadSummary)
                    .routeSegmentsJson(segJson)
                    .build());
        }

        Attraction from = attractionMapper.findById(fromId).orElse(null);
        Attraction to   = attractionMapper.findById(toId).orElse(null);
        if (from == null || to == null
                || from.getLatitude() == null || from.getLongitude() == null
                || to.getLatitude() == null   || to.getLongitude() == null) {
            return Optional.empty();
        }

        BigDecimal fromLat = from.getLatitude(), fromLng = from.getLongitude();
        BigDecimal toLat   = to.getLatitude(),   toLng   = to.getLongitude();

        if (MODE_DRIVING.equals(resolvedMode)) {
            return fetchAndCacheTMapRoute(fromId, toId, departureHour, fromLat, fromLng, toLat, toLng, true);
        }
        if (MODE_WALKING.equals(resolvedMode)) {
            return fetchAndCacheTMapRoute(fromId, toId, departureHour, fromLat, fromLng, toLat, toLng, false);
        }

        // PUBLIC_TRANSIT: ODsay 경로 보강(좌표 기반 — attraction·커스텀 공통)
        List<RouteEnrichment> enriched = enrichPublicTransit(fromLat, fromLng, toLat, toLng);
        if (enriched.isEmpty()) {
            saveNoneCache(fromId, toId, departureHour, resolvedMode);
            return Optional.of(TransitResponse.builder()
                    .transportMode("NONE")
                    .transferCount(0)
                    .fare(0)
                    .totalWalkM(0)
                    .build());
        }

        RouteEnrichment best = enriched.get(0);
        int totalMinutes = best.totalWithLocal();

        TransitCache cache = new TransitCache();
        cache.setFromAttractionId(fromId);
        cache.setToAttractionId(toId);
        cache.setDepartureHour(departureHour);
        cache.setRequestMode(resolvedMode);
        cache.setDurationMinutes(totalMinutes);
        cache.setTransportMode(best.result().transportMode());
        cache.setTransferCount(best.result().transferCount());
        cache.setFare(best.result().fare());
        cache.setTotalDistanceM(best.result().totalDistanceM());
        cache.setTotalWalkM(best.result().totalWalkM());
        cache.setPathDetail(buildPathDetail(enriched));
        String publicRouteCoords = extractPublicTransitCoords(best.result().pathDetail());
        cache.setRouteCoords(publicRouteCoords);

        try {
            transitCacheMapper.insert(cache);
            log.debug("TransitCache 저장: total={}분, from={}, to={}", totalMinutes, fromId, toId);
        } catch (Exception e) {
            log.warn("TransitCache 저장 실패: {}", e.getMessage());
        }

        return Optional.of(TransitResponse.builder()
                .durationMinutes(totalMinutes)
                .transportMode(best.result().transportMode())
                .transferCount(best.result().transferCount())
                .fare(best.result().fare())
                .totalWalkM(best.result().totalWalkM())
                .routeCoords(publicRouteCoords)
                .build());
    }

    /**
     * 좌표 쌍에 대한 ODsay 대중교통 경로를 보강한다(attraction·커스텀 공통).
     * 도시간(pathType>=11) 경로는 출발지→출발역, 도착역→목적지 로컬 구간을 findLocalPath 로 합산해
     * 역간 경로만 표시되던 문제를 보정한다.
     */
    private List<RouteEnrichment> enrichPublicTransit(BigDecimal fromLat, BigDecimal fromLng,
                                                      BigDecimal toLat, BigDecimal toLng) {
        String key = coordKey(fromLat, fromLng, toLat, toLng);
        List<RouteEnrichment> cached = publicCoordCache.get(key);
        if (cached != null) return cached;
        List<RouteEnrichment> fresh = enrichPublicTransitLive(fromLat, fromLng, toLat, toLng);
        if (!fresh.isEmpty()) {   // 성공 결과만 캐시(레이트리밋 빈 결과는 저장하지 않음)
            if (publicCoordCache.size() > COORD_CACHE_MAX) publicCoordCache.clear();
            publicCoordCache.put(key, fresh);
        }
        return fresh;
    }

    private List<RouteEnrichment> enrichPublicTransitLive(BigDecimal fromLat, BigDecimal fromLng,
                                                          BigDecimal toLat, BigDecimal toLng) {
        List<OdsayClient.OdsayResult> results = odsayClient.findTransitPath(fromLat, fromLng, toLat, toLng);
        List<RouteEnrichment> enriched = new ArrayList<>();
        for (OdsayClient.OdsayResult r : results) {
            try {
                if (r.pathType() < 11) {
                    enriched.add(new RouteEnrichment(r, r.durationMinutes(), null, null));
                } else {
                    JsonNode subPaths = objectMapper.readTree(r.pathDetail()).path("subPath");
                    BigDecimal startLat = null, startLng = null, endLat = null, endLng = null;
                    for (int j = 0; j < subPaths.size(); j++) {
                        if (subPaths.get(j).path("trafficType").asInt() != 3) {
                            startLng = BigDecimal.valueOf(subPaths.get(j).path("startX").asDouble());
                            startLat = BigDecimal.valueOf(subPaths.get(j).path("startY").asDouble());
                            break;
                        }
                    }
                    for (int j = subPaths.size() - 1; j >= 0; j--) {
                        if (subPaths.get(j).path("trafficType").asInt() != 3) {
                            endLng = BigDecimal.valueOf(subPaths.get(j).path("endX").asDouble());
                            endLat = BigDecimal.valueOf(subPaths.get(j).path("endY").asDouble());
                            break;
                        }
                    }
                    if (startLat == null || endLat == null) {
                        log.warn("도시간 경로[{}] 비-도보 subPath 없음", r.pathIndex());
                        continue;
                    }
                    final BigDecimal fSLat = startLat, fSLng = startLng, fELat = endLat, fELng = endLng;
                    OdsayClient.LocalPathResult localFrom = odsayClient.findLocalPath(fromLat, fromLng, startLat, startLng)
                            .orElseGet(() -> new OdsayClient.LocalPathResult(
                                    odsayClient.haversineMinutes(fromLat, fromLng, fSLat, fSLng, 30.0), true, null));
                    OdsayClient.LocalPathResult localTo = odsayClient.findLocalPath(endLat, endLng, toLat, toLng)
                            .orElseGet(() -> new OdsayClient.LocalPathResult(
                                    odsayClient.haversineMinutes(fELat, fELng, toLat, toLng, 30.0), true, null));
                    int total = localFrom.minutes() + r.durationMinutes() + localTo.minutes();
                    log.debug("도시간 경로[{}] 총 소요: {}분 (로컬{}+역간{}+로컬{})",
                            r.pathIndex(), total, localFrom.minutes(), r.durationMinutes(), localTo.minutes());
                    enriched.add(new RouteEnrichment(r, total, localFrom, localTo));
                }
            } catch (Exception e) {
                log.warn("경로[{}] 보강 실패: {}", r.pathIndex(), e.getMessage());
            }
        }
        return enriched;
    }

    // ── 좌표 기반(커스텀 장소): 무캐시 라이브. 기존 attraction 경로(getTransitTime)는 미변경 ──
    @Override
    public Optional<TransitResponse> getTransitByCoords(BigDecimal fromLat, BigDecimal fromLng,
                                                        BigDecimal toLat, BigDecimal toLng,
                                                        int departureHour, String mode) {
        if (fromLat == null || fromLng == null || toLat == null || toLng == null) return Optional.empty();
        String resolvedMode = (mode == null || mode.isBlank()) ? MODE_PUBLIC_TRANSIT : mode;

        if (MODE_DRIVING.equals(resolvedMode) || MODE_WALKING.equals(resolvedMode)) {
            boolean isTaxi = MODE_DRIVING.equals(resolvedMode);
            String cacheKey = coordKey(fromLat, fromLng, toLat, toLng)
                    + (isTaxi ? "#drive-00@" + departureHour : "#walk");
            Optional<TransitResponse> hit = tmapCoordCache.get(cacheKey);
            if (hit != null) return hit;
            TMapClient.TMapDrivingResult result = isTaxi
                    ? tMapClient.fetchTaxiRoute(fromLat, fromLng, toLat, toLng, "00", departureHour)
                    : tMapClient.fetchWalkingRoute(fromLat, fromLng, toLat, toLng);
            if (result == null) return Optional.of(noneTransit());   // 실패는 캐시 안 함(재시도)
            String roadSummary = buildRoadSummary(result.segments());
            String segJson;
            try { segJson = objectMapper.writeValueAsString(result.segments()); }
            catch (Exception e) { segJson = "[]"; }
            Optional<TransitResponse> out = Optional.of(TransitResponse.builder()
                    .durationMinutes(result.durationMinutes())
                    .transportMode(resolvedMode)
                    .transferCount(0).fare(0)
                    .totalDistanceM(result.totalDistanceM())
                    .taxiFare(result.taxiFare())
                    .routeCoords(result.routeCoords())
                    .roadSummary(roadSummary == null || roadSummary.isEmpty() ? null : roadSummary)
                    .routeSegmentsJson(segJson)
                    .build());
            putTmapCoord(cacheKey, out);
            return out;
        }

        // PUBLIC_TRANSIT: 도시간 로컬 보강 포함 best (attraction 경로와 동일)
        List<RouteEnrichment> enriched = enrichPublicTransit(fromLat, fromLng, toLat, toLng);
        if (enriched.isEmpty()) return Optional.of(noneTransit());
        RouteEnrichment best = enriched.get(0);
        return Optional.of(TransitResponse.builder()
                .durationMinutes(best.totalWithLocal())
                .transportMode(best.result().transportMode())
                .transferCount(best.result().transferCount())
                .fare(best.result().fare())
                .totalWalkM(best.result().totalWalkM())
                .routeCoords(buildEnrichedRouteCoords(best))   // 접근 구간 포함 폴리라인
                .build());
    }

    // ── 좌표 기반 대중교통 경로 단계(detail) — attraction getPathDetail 과 동일 구조, 무캐시 라이브 ──
    @Override
    public Optional<JsonNode> getTransitDetailByCoords(BigDecimal fromLat, BigDecimal fromLng,
                                                       BigDecimal toLat, BigDecimal toLng, int departureHour) {
        if (fromLat == null || fromLng == null || toLat == null || toLng == null) return Optional.empty();
        List<RouteEnrichment> enriched = enrichPublicTransit(fromLat, fromLng, toLat, toLng);
        if (enriched.isEmpty()) return Optional.empty();
        try {
            return Optional.of(objectMapper.readTree(buildPathDetail(enriched)));
        } catch (JsonProcessingException e) {
            log.warn("by-coords path_detail 파싱 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ── 좌표 기반 자동차 4옵션 — attraction getDrivingOptions 와 동일 구조, 무캐시 라이브 ──
    @Override
    public List<TransitResponse> getDrivingOptionsByCoords(BigDecimal fromLat, BigDecimal fromLng,
                                                           BigDecimal toLat, BigDecimal toLng, int departureHour) {
        if (fromLat == null || fromLng == null || toLat == null || toLng == null) return List.of();
        List<TransitResponse> results = new ArrayList<>();
        for (int i = 0; i < DRIVING_SEARCH_OPTIONS.length; i++) {
            buildDrivingResponse(fromLat, fromLng, toLat, toLng, DRIVING_SEARCH_OPTIONS[i], DRIVING_LABELS[i], departureHour)
                    .ifPresent(results::add);
        }
        return results;
    }

    @Override
    public Optional<TransitResponse> getDrivingOptionByCoords(BigDecimal fromLat, BigDecimal fromLng,
                                                              BigDecimal toLat, BigDecimal toLng,
                                                              int departureHour, int optionIndex) {
        if (fromLat == null || fromLng == null || toLat == null || toLng == null) return Optional.empty();
        if (optionIndex < 0 || optionIndex >= DRIVING_SEARCH_OPTIONS.length) return Optional.empty();
        return buildDrivingResponse(fromLat, fromLng, toLat, toLng,
                DRIVING_SEARCH_OPTIONS[optionIndex], DRIVING_LABELS[optionIndex], departureHour);
    }

    /** 단일 자동차 옵션을 TMap 으로 라이브 계산해 TransitResponse 로 빌드(좌표 in-memory 캐시). */
    private Optional<TransitResponse> buildDrivingResponse(BigDecimal fromLat, BigDecimal fromLng,
                                                           BigDecimal toLat, BigDecimal toLng,
                                                           String searchOption, String label, int departureHour) {
        String cacheKey = coordKey(fromLat, fromLng, toLat, toLng) + "#drive-" + searchOption + "@" + departureHour;
        Optional<TransitResponse> hit = tmapCoordCache.get(cacheKey);
        if (hit != null) return hit;
        TMapClient.TMapDrivingResult result = tMapClient.fetchTaxiRoute(fromLat, fromLng, toLat, toLng, searchOption, departureHour);
        if (result == null || result.durationMinutes() <= 0) return Optional.empty();   // 실패는 캐시 안 함
        String roadSummary = buildRoadSummary(result.segments());
        String segJson;
        try { segJson = objectMapper.writeValueAsString(result.segments()); }
        catch (Exception e) { segJson = "[]"; }
        Optional<TransitResponse> out = Optional.of(TransitResponse.builder()
                .durationMinutes(result.durationMinutes())
                .transportMode(MODE_DRIVING)
                .taxiFare(result.taxiFare())
                .tollFare(result.tollFare())
                .totalDistanceM(result.totalDistanceM())
                .roadSummary(roadSummary)
                .routeSegmentsJson(segJson)
                .routeCoords(result.routeCoords())
                .label(label)
                .build());
        putTmapCoord(cacheKey, out);
        return out;
    }

    private TransitResponse noneTransit() {
        return TransitResponse.builder().transportMode("NONE").transferCount(0).fare(0).totalWalkM(0).build();
    }

    private Optional<TransitResponse> fetchAndCacheTMapRoute(Long fromId, Long toId, int departureHour,
                                                              BigDecimal fromLat, BigDecimal fromLng,
                                                              BigDecimal toLat, BigDecimal toLng,
                                                              boolean isTaxi) {
        TMapClient.TMapDrivingResult result = isTaxi
                ? tMapClient.fetchTaxiRoute(fromLat, fromLng, toLat, toLng, "00", departureHour)
                : tMapClient.fetchWalkingRoute(fromLat, fromLng, toLat, toLng);

        String resolvedMode = isTaxi ? MODE_DRIVING : MODE_WALKING;

        if (result == null) {
            saveNoneCache(fromId, toId, departureHour, resolvedMode);
            return Optional.of(TransitResponse.builder()
                    .transportMode("NONE")
                    .transferCount(0)
                    .fare(0)
                    .totalWalkM(0)
                    .build());
        }

        String roadSummary = buildRoadSummary(result.segments());
        String segJson;
        try { segJson = objectMapper.writeValueAsString(result.segments()); }
        catch (Exception e) { segJson = "[]"; }

        ObjectNode pathDetailNode = objectMapper.createObjectNode();
        pathDetailNode.put("roadSummary", roadSummary);
        try { pathDetailNode.set("segments", objectMapper.readTree(segJson)); }
        catch (Exception ignored) {}

        TransitCache cache = new TransitCache();
        cache.setFromAttractionId(fromId);
        cache.setToAttractionId(toId);
        cache.setDepartureHour(departureHour);
        cache.setRequestMode(resolvedMode);
        cache.setDurationMinutes(result.durationMinutes());
        cache.setTransportMode(resolvedMode);
        cache.setTransferCount(0);
        cache.setFare(0);
        cache.setTotalDistanceM(result.totalDistanceM());
        cache.setTotalWalkM(0);
        cache.setTaxiFare(result.taxiFare());
        cache.setRouteCoords(result.routeCoords());
        try { cache.setPathDetail(objectMapper.writeValueAsString(pathDetailNode)); }
        catch (Exception e) { cache.setPathDetail("{}"); }

        try {
            transitCacheMapper.insert(cache);
            log.debug("TMap {}캐시 저장: total={}분, {}m, from={}, to={}", resolvedMode, result.durationMinutes(), result.totalDistanceM(), fromId, toId);
        } catch (Exception e) {
            log.warn("TMap 캐시 저장 실패: {}", e.getMessage());
        }

        return Optional.of(TransitResponse.builder()
                .durationMinutes(result.durationMinutes())
                .transportMode(resolvedMode)
                .transferCount(0)
                .fare(0)
                .totalWalkM(0)
                .totalDistanceM(result.totalDistanceM())
                .taxiFare(result.taxiFare())
                .routeCoords(result.routeCoords())
                .roadSummary(roadSummary)
                .routeSegmentsJson(segJson)
                .build());
    }

    @Override
    public Optional<TransitResponse> selectPath(Long fromId, Long toId, int departureHour, int pathIndex) {
        Optional<TransitCache> cachedOpt = transitCacheMapper.findByKey(fromId, toId, departureHour, MODE_PUBLIC_TRANSIT);
        if (cachedOpt.isEmpty()) return Optional.empty();
        TransitCache cached = cachedOpt.get();
        try {
            JsonNode pathDetail = objectMapper.readTree(cached.getPathDetail());
            JsonNode intercityPaths = pathDetail.path("intercityPaths");
            if (pathIndex < 0 || pathIndex >= intercityPaths.size()) return Optional.empty();

            JsonNode selected = intercityPaths.get(pathIndex);
            JsonNode info = selected.path("info");
            int pathType = selected.path("pathType").asInt(0);

            int interCityMinutes = info.path("totalTime").asInt(0);
            int localFromMin = pathType < 11 ? 0 : selected.path("localFrom").path("minutes").asInt(0);
            int localToMin   = pathType < 11 ? 0 : selected.path("localTo").path("minutes").asInt(0);
            int totalMinutes = pathType < 11 ? interCityMinutes : localFromMin + interCityMinutes + localToMin;

            String transportMode = extractModeFromPath(selected);
            if (transportMode == null) return Optional.empty();

            int fare = info.path("totalPayment").asInt(0);
            if (fare == 0) fare = info.path("payment").asInt(0);
            int totalDistanceM = info.path("totalDistance").asInt(0);
            int totalWalkM     = info.path("totalWalk").asInt(0);
            int transitCount   = info.path("transitCount").asInt(-1);
            int transferCount  = transitCount >= 0
                    ? Math.max(0, transitCount - 1)
                    : Math.max(0, info.path("busTransitCount").asInt(0) + info.path("subwayTransitCount").asInt(0) - 1);

            // 선택된 경로의 폴리라인을 새로 계산해 route_coords 갱신
            String newRouteCoords = extractPublicTransitCoords(objectMapper.writeValueAsString(selected));
            cached.setDurationMinutes(totalMinutes);
            cached.setTransportMode(transportMode);
            cached.setTransferCount(transferCount);
            cached.setFare(fare);
            cached.setTotalDistanceM(totalDistanceM);
            cached.setTotalWalkM(totalWalkM);
            cached.setRouteCoords(newRouteCoords);
            transitCacheMapper.updateSummary(cached);
            tripBlockMapper.updateTransitByAttractionPair(fromId, toId, totalMinutes, transportMode, pathIndex);
            log.debug("경로 선택 저장: pathIndex={}, total={}분, mode={}, from={}, to={}",
                    pathIndex, totalMinutes, transportMode, fromId, toId);

            return Optional.of(TransitResponse.builder()
                    .durationMinutes(totalMinutes)
                    .transportMode(transportMode)
                    .transferCount(transferCount)
                    .fare(fare)
                    .totalWalkM(totalWalkM)
                    .routeCoords(newRouteCoords)
                    .build());
        } catch (Exception e) {
            log.warn("경로 선택 실패 from={}, to={}: {}", fromId, toId, e.getMessage());
            return Optional.empty();
        }
    }

    private String extractModeFromPath(JsonNode path) {
        List<String> modes = new ArrayList<>();
        for (JsonNode sub : path.path("subPath")) {
            String mode = switch (sub.path("trafficType").asInt()) {
                case 1 -> "SUBWAY";
                case 2 -> "BUS";
                case 4 -> "RAIL";
                case 5 -> "EXPRESSBUS";
                case 6 -> "INTERCITYBUS";
                default -> null;
            };
            if (mode != null && (modes.isEmpty() || !modes.getLast().equals(mode))) modes.add(mode);
        }
        return modes.isEmpty() ? null : String.join(",", modes);
    }

    @Override
    public Optional<JsonNode> getPathDetail(Long fromAttractionId, Long toAttractionId, int departureHour) {
        return transitCacheMapper.findByKey(fromAttractionId, toAttractionId, departureHour, MODE_PUBLIC_TRANSIT)
                .map(c -> {
                    try {
                        return objectMapper.readTree(c.getPathDetail());
                    } catch (JsonProcessingException e) {
                        log.warn("path_detail 파싱 실패: {}", e.getMessage());
                        return null;
                    }
                });
    }

    private void saveNoneCache(Long fromId, Long toId, int departureHour, String requestMode) {
        TransitCache none = new TransitCache();
        none.setFromAttractionId(fromId);
        none.setToAttractionId(toId);
        none.setDepartureHour(departureHour);
        none.setRequestMode(requestMode);
        none.setDurationMinutes(0);
        none.setTransportMode("NONE");
        none.setTransferCount(0);
        none.setFare(0);
        none.setTotalDistanceM(0);
        none.setTotalWalkM(0);
        none.setPathDetail("{}");
        try {
            transitCacheMapper.insert(none);
            log.debug("NONE 캐시 저장: from={}, to={}, mode={}", fromId, toId, requestMode);
        } catch (Exception e) {
            log.warn("NONE 캐시 저장 실패: {}", e.getMessage());
        }
    }

    /**
     * PUBLIC_TRANSIT 경로의 폴리라인 좌표를 구성한다.
     * ODsay searchPubTransPathT 응답의 info.mapObj 값을 그대로 loadLane API에 전달.
     * - DB 캐시 우선, 없으면 API 호출 후 저장
     * - loadLane 실패 시 passStopList 정류장 좌표로 fallback
     * - passStopList도 없으면 구간 시작·끝 좌표 fallback
     */
    private String extractPublicTransitCoords(String pathDetail) {
        try {
            JsonNode path   = objectMapper.readTree(pathDetail);
            String   mapObj = path.path("info").path("mapObj").asText("");

            // 1) loadLane (DB 캐시 → API 호출) — info.mapObj 기반
            if (!mapObj.isEmpty()) {
                String laneCoords = fetchLanePolyline(mapObj, "0:0@" + mapObj);
                if (laneCoords != null) return laneCoords;
            }

            // 2) passStopList 정류장 좌표 fallback
            List<double[]> fallback = new ArrayList<>();
            for (JsonNode sub : path.path("subPath")) {
                if (sub.path("trafficType").asInt() == 3) continue;
                for (JsonNode st : sub.path("passStopList").path("stations")) {
                    double x = st.path("x").asDouble(0);
                    double y = st.path("y").asDouble(0);
                    if (x != 0 && y != 0) fallback.add(new double[]{x, y});
                }
            }
            if (!fallback.isEmpty()) {
                log.debug("loadLane 없어 passStopList fallback: mapObj={}", mapObj);
                return objectMapper.writeValueAsString(fallback);
            }

            // 3) 구간 시작·끝 좌표 fallback
            List<double[]> endpoints = new ArrayList<>();
            for (JsonNode sub : path.path("subPath")) {
                double sx = sub.path("startX").asDouble(0), sy = sub.path("startY").asDouble(0);
                double ex = sub.path("endX").asDouble(0),   ey = sub.path("endY").asDouble(0);
                if (sx != 0 && sy != 0) endpoints.add(new double[]{sx, sy});
                if (ex != 0 && ey != 0) endpoints.add(new double[]{ex, ey});
            }
            return endpoints.size() >= 2 ? objectMapper.writeValueAsString(endpoints) : null;
        } catch (Exception e) {
            log.warn("PUBLIC_TRANSIT routeCoords 구성 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * DB 캐시에서 lane polyline 조회, 없으면 ODsay loadLane 호출 후 저장.
     * 결과가 없는 경우도 저장해 불필요한 재시도를 방지한다.
     */
    private String fetchLanePolyline(String cacheKey, String mapObject) {
        Optional<LanePolyline> cached = lanePolylineMapper.findByKey(cacheKey);
        if (cached.isPresent()) {
            log.debug("LanePolyline 캐시 히트: key={}", cacheKey);
            return cached.get().getRouteCoords();
        }
        OdsayClient.LaneResult result = odsayClient.loadLane(mapObject);
        LanePolyline lp = new LanePolyline();
        lp.setMapObjectKey(cacheKey);
        lp.setRouteCoords(result != null ? result.routeCoords() : null);
        lp.setRawResponse(result != null ? result.rawResponse() : null);
        try {
            lanePolylineMapper.insert(lp);
            log.debug("LanePolyline 저장: key={}, 좌표={}", cacheKey, result != null ? "있음" : "없음");
        } catch (Exception e) {
            log.warn("LanePolyline 저장 실패: {}", e.getMessage());
        }
        return result != null ? result.routeCoords() : null;
    }

    /**
     * by-coords 대중교통 경로의 지도 폴리라인.
     * 출발지→역(localFrom) + 본 구간(시내=loadLane 폴리라인 / 도시간=역 끝점) + 역→목적지(localTo)를 이어붙여
     * 커스텀 장소에서도 접근 구간까지 지도에 선으로 표시되게 한다.
     */
    private String buildEnrichedRouteCoords(RouteEnrichment best) {
        List<double[]> all = new ArrayList<>();
        if (best.localFrom() != null && best.localFrom().pathNode() != null)
            appendPathNodeCoords(all, best.localFrom().pathNode());
        appendCoordsJson(all, extractPublicTransitCoords(best.result().pathDetail()));
        if (best.localTo() != null && best.localTo().pathNode() != null)
            appendPathNodeCoords(all, best.localTo().pathNode());
        try {
            if (all.size() >= 2) return objectMapper.writeValueAsString(all);
        } catch (Exception ignored) {}
        return extractPublicTransitCoords(best.result().pathDetail());
    }

    private void appendCoordsJson(List<double[]> out, String coordsJson) {
        if (coordsJson == null) return;
        try {
            for (JsonNode pt : objectMapper.readTree(coordsJson)) {
                if (pt.size() >= 2) addCoord(out, pt.get(0).asDouble(), pt.get(1).asDouble());
            }
        } catch (Exception ignored) {}
    }

    private void appendPathNodeCoords(List<double[]> out, JsonNode pathNode) {
        for (JsonNode sub : pathNode.path("subPath")) {
            double sx = sub.path("startX").asDouble(0), sy = sub.path("startY").asDouble(0);
            if (sx != 0 && sy != 0) addCoord(out, sx, sy);
            for (JsonNode st : sub.path("passStopList").path("stations")) {
                double x = st.path("x").asDouble(0), y = st.path("y").asDouble(0);
                if (x != 0 && y != 0) addCoord(out, x, y);
            }
            double ex = sub.path("endX").asDouble(0), ey = sub.path("endY").asDouble(0);
            if (ex != 0 && ey != 0) addCoord(out, ex, ey);
        }
    }

    private void addCoord(List<double[]> out, double x, double y) {
        if (!out.isEmpty()) {
            double[] last = out.get(out.size() - 1);
            if (last[0] == x && last[1] == y) return;   // 연속 중복 제거
        }
        out.add(new double[]{x, y});
    }

    private String buildPathDetail(List<RouteEnrichment> enriched) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode intercityPaths = objectMapper.createArrayNode();
            for (RouteEnrichment e : enriched) {
                ObjectNode pathNode = objectMapper.readTree(e.result().pathDetail()).deepCopy();
                if (e.localFrom() != null) {
                    pathNode.set("localFrom", buildLocalNode(e.localFrom()));
                    pathNode.set("localTo",   buildLocalNode(e.localTo()));
                }
                intercityPaths.add(pathNode);
            }
            root.set("intercityPaths", intercityPaths);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("path_detail JSON 빌드 실패: {}", e.getMessage());
            return "{}";
        }
    }

    private ObjectNode buildLocalNode(OdsayClient.LocalPathResult local) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("minutes", local.minutes());
        node.put("estimated", local.estimated());
        if (local.pathNode() != null) {
            node.set("subPath", local.pathNode().path("subPath"));
        }
        return node;
    }

    private static final String[] DRIVING_LABELS = {"추천", "최단시간", "무료도로", "최소거리"};
    private static final String[] DRIVING_SEARCH_OPTIONS = {"00", "02", "01", "10"};
    private static final String[] DRIVING_REQUEST_MODES = {"DRIVING_00", "DRIVING_02", "DRIVING_01", "DRIVING_10"};

    @Override
    public List<TransitResponse> getDrivingOptions(Long fromId, Long toId, int departureHour) {
        // 캐시 확인
        List<TransitResponse> cached = new ArrayList<>();
        for (int i = 0; i < DRIVING_REQUEST_MODES.length; i++) {
            Optional<TransitCache> c = transitCacheMapper.findByKey(fromId, toId, departureHour, DRIVING_REQUEST_MODES[i]);
            if (c.isEmpty()) { cached = null; break; }
            cached.add(buildDrivingResponseFromCache(c.get()));
        }
        if (cached != null) return cached;

        Attraction from = attractionMapper.findById(fromId).orElse(null);
        Attraction to   = attractionMapper.findById(toId).orElse(null);
        if (from == null || to == null
                || from.getLatitude() == null || from.getLongitude() == null
                || to.getLatitude() == null   || to.getLongitude() == null) {
            return List.of();
        }
        BigDecimal fromLat = from.getLatitude(), fromLng = from.getLongitude();
        BigDecimal toLat   = to.getLatitude(),   toLng   = to.getLongitude();

        List<TransitResponse> results = new ArrayList<>();
        for (int i = 0; i < DRIVING_SEARCH_OPTIONS.length; i++) {
            TMapClient.TMapDrivingResult result = tMapClient.fetchTaxiRoute(fromLat, fromLng, toLat, toLng, DRIVING_SEARCH_OPTIONS[i], departureHour);
            if (result != null && result.durationMinutes() > 0) {
                String roadSummary = buildRoadSummary(result.segments());
                String segJson;
                try { segJson = objectMapper.writeValueAsString(result.segments()); }
                catch (Exception e) { segJson = "[]"; }

                ObjectNode detail = objectMapper.createObjectNode();
                detail.put("tollFare", result.tollFare());
                detail.put("roadSummary", roadSummary);
                detail.put("label", DRIVING_LABELS[i]);
                try { detail.set("segments", objectMapper.readTree(segJson)); }
                catch (Exception ignored) {}

                TransitCache cache = new TransitCache();
                cache.setFromAttractionId(fromId);
                cache.setToAttractionId(toId);
                cache.setDepartureHour(departureHour);
                cache.setRequestMode(DRIVING_REQUEST_MODES[i]);
                cache.setDurationMinutes(result.durationMinutes());
                cache.setTransportMode(MODE_DRIVING);
                cache.setTransferCount(0);
                cache.setFare(0);
                cache.setTotalDistanceM(result.totalDistanceM());
                cache.setTotalWalkM(0);
                cache.setTaxiFare(result.taxiFare());
                cache.setRouteCoords(result.routeCoords());
                try { cache.setPathDetail(objectMapper.writeValueAsString(detail)); }
                catch (Exception e) { cache.setPathDetail("{}"); }
                try { transitCacheMapper.insert(cache); }
                catch (Exception e) { log.warn("DRIVING 옵션 캐시 저장 실패 ({}): {}", DRIVING_REQUEST_MODES[i], e.getMessage()); }

                results.add(TransitResponse.builder()
                        .durationMinutes(result.durationMinutes())
                        .transportMode(MODE_DRIVING)
                        .taxiFare(result.taxiFare())
                        .tollFare(result.tollFare())
                        .totalDistanceM(result.totalDistanceM())
                        .roadSummary(roadSummary)
                        .routeSegmentsJson(segJson)
                        .routeCoords(result.routeCoords())
                        .label(DRIVING_LABELS[i])
                        .build());
            }
        }
        return results;
    }

    @Override
    public Optional<TransitResponse> getDrivingOption(Long fromId, Long toId, int departureHour, int optionIndex) {
        if (optionIndex < 0 || optionIndex >= DRIVING_SEARCH_OPTIONS.length) return Optional.empty();

        Optional<TransitCache> cached = transitCacheMapper.findByKey(fromId, toId, departureHour, DRIVING_REQUEST_MODES[optionIndex]);
        if (cached.isPresent()) {
            return Optional.of(buildDrivingResponseFromCache(cached.get()));
        }

        Attraction from = attractionMapper.findById(fromId).orElse(null);
        Attraction to   = attractionMapper.findById(toId).orElse(null);
        if (from == null || to == null
                || from.getLatitude() == null || from.getLongitude() == null
                || to.getLatitude() == null   || to.getLongitude() == null) {
            return Optional.empty();
        }

        TMapClient.TMapDrivingResult result = tMapClient.fetchTaxiRoute(
                from.getLatitude(), from.getLongitude(), to.getLatitude(), to.getLongitude(),
                DRIVING_SEARCH_OPTIONS[optionIndex], departureHour);
        if (result == null || result.durationMinutes() <= 0) return Optional.empty();

        String roadSummary = buildRoadSummary(result.segments());
        String segJson;
        try { segJson = objectMapper.writeValueAsString(result.segments()); }
        catch (Exception e) { segJson = "[]"; }

        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("tollFare", result.tollFare());
        detail.put("roadSummary", roadSummary);
        detail.put("label", DRIVING_LABELS[optionIndex]);
        try { detail.set("segments", objectMapper.readTree(segJson)); }
        catch (Exception ignored) {}

        TransitCache cache = new TransitCache();
        cache.setFromAttractionId(fromId);
        cache.setToAttractionId(toId);
        cache.setDepartureHour(departureHour);
        cache.setRequestMode(DRIVING_REQUEST_MODES[optionIndex]);
        cache.setDurationMinutes(result.durationMinutes());
        cache.setTransportMode(MODE_DRIVING);
        cache.setTransferCount(0);
        cache.setFare(0);
        cache.setTotalDistanceM(result.totalDistanceM());
        cache.setTotalWalkM(0);
        cache.setTaxiFare(result.taxiFare());
        cache.setRouteCoords(result.routeCoords());
        try { cache.setPathDetail(objectMapper.writeValueAsString(detail)); }
        catch (Exception e) { cache.setPathDetail("{}"); }
        try { transitCacheMapper.insert(cache); }
        catch (Exception e) { log.warn("DRIVING 단일 옵션 캐시 저장 실패 ({}): {}", DRIVING_REQUEST_MODES[optionIndex], e.getMessage()); }

        return Optional.of(TransitResponse.builder()
                .durationMinutes(result.durationMinutes())
                .transportMode(MODE_DRIVING)
                .taxiFare(result.taxiFare())
                .tollFare(result.tollFare())
                .totalDistanceM(result.totalDistanceM())
                .roadSummary(roadSummary)
                .routeSegmentsJson(segJson)
                .routeCoords(result.routeCoords())
                .label(DRIVING_LABELS[optionIndex])
                .build());
    }

    // ── 통합 경로 구간(segment) — 어트랙션·커스텀 공통. 구간별 색/도보/역마커용 ──
    // 각 구간: { c: class(1지하철 2버스 3도보 4열차), t: 호선코드/버스종류, name: 승차역, end: 하차역, p: [[lng,lat],...] }

    @Override
    public List<Map<String, Object>> getRouteSegments(Long fromId, Long toId, int departureHour) {
        Attraction from = attractionMapper.findById(fromId).orElse(null);
        Attraction to   = attractionMapper.findById(toId).orElse(null);
        if (from == null || to == null
                || from.getLatitude() == null || to.getLatitude() == null) return List.of();
        return routeSegmentsForCoords(from.getLatitude(), from.getLongitude(), to.getLatitude(), to.getLongitude());
    }

    @Override
    public List<Map<String, Object>> getRouteSegmentsByCoords(BigDecimal fromLat, BigDecimal fromLng,
                                                              BigDecimal toLat, BigDecimal toLng, int departureHour) {
        if (fromLat == null || fromLng == null || toLat == null || toLng == null) return List.of();
        return routeSegmentsForCoords(fromLat, fromLng, toLat, toLng);
    }

    private List<Map<String, Object>> routeSegmentsForCoords(BigDecimal fromLat, BigDecimal fromLng,
                                                             BigDecimal toLat, BigDecimal toLng) {
        List<RouteEnrichment> enriched = enrichPublicTransit(fromLat, fromLng, toLat, toLng);
        if (enriched.isEmpty()) return List.of();
        return buildRouteSegments(enriched.get(0), fromLat, fromLng, toLat, toLng);
    }

    private List<Map<String, Object>> buildRouteSegments(RouteEnrichment best,
            BigDecimal fromLat, BigDecimal fromLng, BigDecimal toLat, BigDecimal toLng) {
        // 1) 대중교통(지하철·버스·열차) 구간만 순서대로 수집
        List<Map<String, Object>> transit = new ArrayList<>();
        try {
            if (best.localFrom() != null && best.localFrom().pathNode() != null)
                transit.addAll(segmentsFromPath(best.localFrom().pathNode()));
            transit.addAll(segmentsFromPath(objectMapper.readTree(best.result().pathDetail())));
            if (best.localTo() != null && best.localTo().pathNode() != null)
                transit.addAll(segmentsFromPath(best.localTo().pathNode()));
        } catch (Exception e) {
            log.warn("route segments 빌드 실패: {}", e.getMessage());
        }
        // 2) 구간 사이/앞뒤 갭을 도보선으로 잇는다(출발지→역, 환승 도보, 역→목적지). ODsay walk 좌표가 비어도 표시됨.
        List<Map<String, Object>> out = new ArrayList<>();
        double[] prev = { fromLng.doubleValue(), fromLat.doubleValue() };
        for (Map<String, Object> s : transit) {
            @SuppressWarnings("unchecked")
            List<double[]> p = (List<double[]>) s.get("p");
            if (p == null || p.isEmpty()) continue;
            double[] start = p.get(0);
            if (distMeters(prev, start) > 25) out.add(walkSeg(prev, start));
            out.add(s);
            prev = p.get(p.size() - 1);
        }
        double[] dest = { toLng.doubleValue(), toLat.doubleValue() };
        if (distMeters(prev, dest) > 25) out.add(walkSeg(prev, dest));
        return out;
    }

    private Map<String, Object> walkSeg(double[] a, double[] b) {
        List<double[]> p = new ArrayList<>();
        p.add(new double[]{a[0], a[1]});
        p.add(new double[]{b[0], b[1]});
        return seg(3, 0, p, null, null, null);
    }

    private double distMeters(double[] a, double[] b) {   // a,b = [lng,lat]
        double R = 6371000.0;
        double dLat = Math.toRadians(b[1] - a[1]), dLon = Math.toRadians(b[0] - a[0]);
        double s = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(a[1])) * Math.cos(Math.toRadians(b[1]))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(s), Math.sqrt(1 - s));
    }

    // 대중교통(지하철·버스·열차) 구간만 — 도보는 buildRouteSegments 에서 갭으로 처리
    private List<Map<String, Object>> segmentsFromPath(JsonNode pathNode) {
        List<Map<String, Object>> out = new ArrayList<>();
        String mapObj = pathNode.path("info").path("mapObj").asText("");
        List<Map<String, Object>> laneSegs = loadLaneSegments(mapObj);   // 대중교통 lane 폴리라인(순서대로)
        int laneIdx = 0;
        for (JsonNode sub : pathNode.path("subPath")) {
            int tt = sub.path("trafficType").asInt(0);
            if (tt == 3) continue;   // 도보는 갭으로 처리
            JsonNode lane = sub.path("lane").path(0);
            int c = tt == 1 ? 1 : (tt == 4 ? 4 : 2);
            int t = tt == 1 ? lane.path("subwayCode").asInt(0) : lane.path("type").asInt(0);
            String name = sub.path("startName").asText(null);
            String end  = sub.path("endName").asText(null);
            String ln   = tt == 1 ? lane.path("name").asText(null) : lane.path("busNo").asText(null);   // 노선/버스 번호
            List<double[]> coords;
            if (laneIdx < laneSegs.size()) {
                @SuppressWarnings("unchecked")
                List<double[]> p = (List<double[]>) laneSegs.get(laneIdx).get("p");
                coords = p;
                laneIdx++;
            } else {
                coords = stopsOrEndpoints(sub);
            }
            if (coords.size() >= 2) out.add(seg(c, t, coords, name, end, ln));
        }
        return out;
    }

    private Map<String, Object> seg(int c, int t, List<double[]> p, String name, String end, String ln) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("c", c); m.put("t", t); m.put("p", p);
        if (name != null && !name.isBlank()) m.put("name", name);
        if (end != null && !end.isBlank()) m.put("end", end);
        if (ln != null && !ln.isBlank()) m.put("ln", ln);
        return m;
    }

    private List<double[]> endpoints(JsonNode sub) {
        List<double[]> pts = new ArrayList<>();
        double sx = sub.path("startX").asDouble(0), sy = sub.path("startY").asDouble(0);
        double ex = sub.path("endX").asDouble(0),   ey = sub.path("endY").asDouble(0);
        if (sx != 0 && sy != 0) pts.add(new double[]{sx, sy});
        if (ex != 0 && ey != 0) pts.add(new double[]{ex, ey});
        return pts;
    }

    private List<double[]> stopsOrEndpoints(JsonNode sub) {
        List<double[]> pts = new ArrayList<>();
        double sx = sub.path("startX").asDouble(0), sy = sub.path("startY").asDouble(0);
        if (sx != 0 && sy != 0) pts.add(new double[]{sx, sy});
        for (JsonNode st : sub.path("passStopList").path("stations")) {
            double x = st.path("x").asDouble(0), y = st.path("y").asDouble(0);
            if (x != 0 && y != 0) pts.add(new double[]{x, y});
        }
        double ex = sub.path("endX").asDouble(0), ey = sub.path("endY").asDouble(0);
        if (ex != 0 && ey != 0) pts.add(new double[]{ex, ey});
        return pts;
    }

    private List<Map<String, Object>> loadLaneSegments(String mapObj) {
        if (mapObj == null || mapObj.isEmpty()) return List.of();
        Optional<LanePolyline> lane = lanePolylineMapper.findByKey(mapObj);
        if (lane.isEmpty()) {
            fetchLanePolyline(mapObj, "0:0@" + mapObj);   // 캐시에 채움(loadLane 호출)
            lane = lanePolylineMapper.findByKey(mapObj);
        }
        if (lane.isEmpty() || lane.get().getRawResponse() == null) return List.of();
        return parseSegmentsFromRaw(lane.get().getRawResponse());
    }

    @Override
    public List<Map<String, Object>> getLaneSegments(Long fromId, Long toId, int departureHour) {
        Optional<TransitCache> cacheOpt = transitCacheMapper.findByKey(fromId, toId, departureHour, MODE_PUBLIC_TRANSIT);
        if (cacheOpt.isEmpty()) return List.of();
        try {
            JsonNode pd = objectMapper.readTree(cacheOpt.get().getPathDetail());
            // path_detail 구조: {"intercityPaths": [{..., "info": {"mapObj": "..."}}]}
            String mapObj = pd.path("intercityPaths").get(0).path("info").path("mapObj").asText("");
            if (mapObj.isEmpty()) return List.of();

            Optional<LanePolyline> laneOpt = lanePolylineMapper.findByKey(mapObj);
            if (laneOpt.isEmpty() || laneOpt.get().getRawResponse() == null) return List.of();

            return parseSegmentsFromRaw(laneOpt.get().getRawResponse());
        } catch (Exception e) {
            log.warn("getLaneSegments 실패 from={} to={}: {}", fromId, toId, e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> parseSegmentsFromRaw(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            List<Map<String, Object>> segments = new ArrayList<>();
            for (JsonNode lane : root.path("result").path("lane")) {
                int modeClass = lane.path("class").asInt(2);
                int laneType  = lane.path("type").asInt(0);   // 지하철=호선코드, 버스=버스종류
                List<double[]> coords = new ArrayList<>();
                for (JsonNode section : lane.path("section")) {
                    for (JsonNode pos : section.path("graphPos")) {
                        double x = pos.path("x").asDouble(0);
                        double y = pos.path("y").asDouble(0);
                        if (x != 0 && y != 0) coords.add(new double[]{x, y});
                    }
                }
                if (!coords.isEmpty()) {
                    Map<String, Object> seg = new java.util.LinkedHashMap<>();
                    seg.put("c", modeClass);
                    seg.put("t", laneType);   // 호선/버스종류 색 구분용
                    seg.put("p", coords);
                    segments.add(seg);
                }
            }
            return segments;
        } catch (Exception e) {
            log.warn("parseSegmentsFromRaw 실패: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<double[]> getWalkingCoords(double startLat, double startLng, double endLat, double endLng) {
        TMapClient.TMapDrivingResult result = tMapClient.fetchWalkingRoute(
            BigDecimal.valueOf(startLat), BigDecimal.valueOf(startLng),
            BigDecimal.valueOf(endLat),   BigDecimal.valueOf(endLng)
        );
        if (result == null || result.routeCoords() == null) return List.of();
        try {
            JsonNode coords = objectMapper.readTree(result.routeCoords());
            List<double[]> list = new ArrayList<>();
            for (JsonNode point : coords) list.add(new double[]{point.get(0).asDouble(), point.get(1).asDouble()});
            return list;
        } catch (Exception e) {
            log.warn("getWalkingCoords 파싱 실패: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void applyDrivingOption(Long fromId, Long toId, int departureHour, int optionIndex) {
        if (optionIndex < 0 || optionIndex >= DRIVING_REQUEST_MODES.length) return;
        // 선택된 옵션의 route_coords를 DRIVING 캐시에 덮어써 지도에 반영되도록 함
        Optional<TransitCache> optionCache = transitCacheMapper.findByKey(fromId, toId, departureHour, DRIVING_REQUEST_MODES[optionIndex]);
        if (optionCache.isEmpty()) return;
        Optional<TransitCache> drivingCache = transitCacheMapper.findByKey(fromId, toId, departureHour, MODE_DRIVING);
        if (drivingCache.isEmpty()) return;
        TransitCache cache = drivingCache.get();
        cache.setRouteCoords(optionCache.get().getRouteCoords());
        transitCacheMapper.updateRouteCoords(cache);
        log.debug("DRIVING route_coords 갱신: optionIndex={}, from={}, to={}", optionIndex, fromId, toId);
    }

    private TransitResponse buildDrivingResponseFromCache(TransitCache c) {
        String roadSummary = "";
        String segJson = "[]";
        int tollFare = 0;
        String label = "";
        try {
            JsonNode detail = objectMapper.readTree(c.getPathDetail());
            tollFare = detail.path("tollFare").asInt(0);
            roadSummary = detail.path("roadSummary").asText("");
            label = detail.path("label").asText("");
            segJson = objectMapper.writeValueAsString(detail.path("segments"));
        } catch (Exception e) {
            log.warn("DRIVING 캐시 path_detail 파싱 실패: {}", e.getMessage());
        }
        return TransitResponse.builder()
                .durationMinutes(c.getDurationMinutes())
                .transportMode(MODE_DRIVING)
                .taxiFare(c.getTaxiFare())
                .tollFare(tollFare)
                .totalDistanceM(c.getTotalDistanceM())
                .roadSummary(roadSummary)
                .routeSegmentsJson(segJson)
                .routeCoords(c.getRouteCoords())
                .label(label)
                .build();
    }

    private String buildRoadSummary(List<TMapClient.RouteSegment> segments) {
        java.util.Comparator<TMapClient.RouteSegment> byDistDesc =
                java.util.Comparator.comparingInt(TMapClient.RouteSegment::distanceM).reversed();

        List<String> selected = new ArrayList<>();
        segments.stream()
                .filter(s -> s.roadType() == 1)
                .sorted(byDistDesc)
                .limit(4)
                .map(TMapClient.RouteSegment::name)
                .forEach(selected::add);

        if (selected.size() < 4) {
            java.util.Set<String> already = new java.util.HashSet<>(selected);
            segments.stream()
                    .filter(s -> s.roadType() != 1)
                    .sorted(byDistDesc)
                    .map(TMapClient.RouteSegment::name)
                    .filter(already::add)
                    .limit(4 - selected.size())
                    .forEach(selected::add);
        }

        java.util.Set<String> selectedSet = new java.util.HashSet<>(selected);
        return segments.stream()
                .filter(s -> selectedSet.contains(s.name()))
                .map(TMapClient.RouteSegment::name)
                .distinct()
                .collect(java.util.stream.Collectors.joining(" → "));
    }

    private record RouteEnrichment(
            OdsayClient.OdsayResult result,
            int totalWithLocal,
            OdsayClient.LocalPathResult localFrom,
            OdsayClient.LocalPathResult localTo
    ) {}
}
