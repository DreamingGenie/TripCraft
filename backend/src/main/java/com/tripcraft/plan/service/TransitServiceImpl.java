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
import com.tripcraft.plan.domain.TransitCache;
import com.tripcraft.plan.dto.TransitResponse;
import com.tripcraft.plan.mapper.TransitCacheMapper;
import com.tripcraft.plan.mapper.TripBlockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransitServiceImpl implements TransitService {

    private final TransitCacheMapper transitCacheMapper;
    private final TripBlockMapper tripBlockMapper;
    private final AttractionMapper attractionMapper;
    private final OdsayClient odsayClient;
    private final TMapClient tMapClient;
    private final ObjectMapper objectMapper;

    private static final String MODE_PUBLIC_TRANSIT = "PUBLIC_TRANSIT";
    private static final String MODE_DRIVING = "DRIVING";
    private static final String MODE_WALKING = "WALKING";

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
            return Optional.of(TransitResponse.builder()
                    .durationMinutes(c.getDurationMinutes())
                    .transportMode(c.getTransportMode())
                    .transferCount(c.getTransferCount())
                    .fare(c.getFare())
                    .totalWalkM(c.getTotalWalkM())
                    .totalDistanceM(c.getTotalDistanceM())
                    .taxiFare(c.getTaxiFare())
                    .routeCoords(c.getRouteCoords())
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

        // PUBLIC_TRANSIT: 기존 ODsay 로직
        List<OdsayClient.OdsayResult> results = odsayClient.findTransitPath(fromLat, fromLng, toLat, toLng);
        if (results.isEmpty()) {
            saveNoneCache(fromId, toId, departureHour, resolvedMode);
            return Optional.of(TransitResponse.builder()
                    .transportMode("NONE")
                    .transferCount(0)
                    .fare(0)
                    .totalWalkM(0)
                    .build());
        }

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
        cache.setPathDetail("{}");
        cache.setTaxiFare(result.taxiFare());
        cache.setRouteCoords(result.routeCoords());

        try {
            transitCacheMapper.insert(cache);
            log.debug("TMap {}캐시 저장: total={}분, {}m, from={}, to={}", resolvedMode, result.durationMinutes(), result.totalDistanceM(), fromId, toId);
        } catch (Exception e) {
            log.warn("TMap 캐시 저장 실패: {}", e.getMessage());
        }

        String walkRoadSummary = buildRoadSummary(result.segments());
        String walkSegJson;
        try { walkSegJson = objectMapper.writeValueAsString(result.segments()); }
        catch (Exception e) { walkSegJson = "[]"; }

        return Optional.of(TransitResponse.builder()
                .durationMinutes(result.durationMinutes())
                .transportMode(resolvedMode)
                .transferCount(0)
                .fare(0)
                .totalWalkM(0)
                .totalDistanceM(result.totalDistanceM())
                .taxiFare(result.taxiFare())
                .routeCoords(result.routeCoords())
                .roadSummary(walkRoadSummary)
                .routeSegmentsJson(walkSegJson)
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

            cached.setDurationMinutes(totalMinutes);
            cached.setTransportMode(transportMode);
            cached.setTransferCount(transferCount);
            cached.setFare(fare);
            cached.setTotalDistanceM(totalDistanceM);
            cached.setTotalWalkM(totalWalkM);
            transitCacheMapper.updateSummary(cached);
            tripBlockMapper.updateTransitByAttractionPair(fromId, toId, totalMinutes, transportMode);
            log.debug("경로 선택 저장: pathIndex={}, total={}분, mode={}, from={}, to={}",
                    pathIndex, totalMinutes, transportMode, fromId, toId);

            return Optional.of(TransitResponse.builder()
                    .durationMinutes(totalMinutes)
                    .transportMode(transportMode)
                    .transferCount(transferCount)
                    .fare(fare)
                    .totalWalkM(totalWalkM)
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

    private String extractPublicTransitCoords(String pathDetail) {
        try {
            JsonNode path = objectMapper.readTree(pathDetail);
            JsonNode subPaths = path.path("subPath");
            List<double[]> coords = new ArrayList<>();
            for (int i = 0; i < subPaths.size(); i++) {
                JsonNode sub = subPaths.get(i);
                double startX = sub.path("startX").asDouble(0);
                double startY = sub.path("startY").asDouble(0);
                double endX   = sub.path("endX").asDouble(0);
                double endY   = sub.path("endY").asDouble(0);
                if (startX == 0 || startY == 0) continue;
                if (coords.isEmpty()) coords.add(new double[]{startX, startY});
                if (endX != 0 && endY != 0) coords.add(new double[]{endX, endY});
            }
            return coords.size() >= 2 ? objectMapper.writeValueAsString(coords) : null;
        } catch (Exception e) {
            log.warn("PUBLIC_TRANSIT routeCoords 추출 실패: {}", e.getMessage());
            return null;
        }
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
                        .label(DRIVING_LABELS[i])
                        .build());
            }
        }
        return results;
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
