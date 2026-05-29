package com.tripcraft.plan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tripcraft.attraction.domain.Attraction;
import com.tripcraft.attraction.mapper.AttractionMapper;
import com.tripcraft.plan.client.OdsayClient;
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
    private final ObjectMapper objectMapper;

    @Override
    public Optional<TransitResponse> getTransitTime(Long fromId, Long toId, int departureHour) {
        Optional<TransitCache> cached = transitCacheMapper.findByKey(fromId, toId, departureHour);
        if (cached.isPresent()) {
            TransitCache c = cached.get();
            if ("NONE".equals(c.getTransportMode())) {
                return Optional.of(new TransitResponse(null, "NONE", 0, 0, 0));
            }
            return Optional.of(new TransitResponse(
                    c.getDurationMinutes(), c.getTransportMode(),
                    c.getTransferCount(), c.getFare(), c.getTotalWalkM()));
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

        // 1. 경로 검색 (도시내/도시간 모두 포함)
        List<OdsayClient.OdsayResult> results = odsayClient.findTransitPath(fromLat, fromLng, toLat, toLng);
        if (results.isEmpty()) {
            saveNoneCache(fromId, toId, departureHour);
            return Optional.of(new TransitResponse(null, "NONE", 0, 0, 0));
        }

            // 2. 경로별 로컬 구간 독립 계산
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
            saveNoneCache(fromId, toId, departureHour);
            return Optional.of(new TransitResponse(null, "NONE", 0, 0, 0));
        }

        RouteEnrichment best = enriched.get(0);
        int totalMinutes = best.totalWithLocal();

        TransitCache cache = new TransitCache();
        cache.setFromAttractionId(fromId);
        cache.setToAttractionId(toId);
        cache.setDepartureHour(departureHour);
        cache.setDurationMinutes(totalMinutes);
        cache.setTransportMode(best.result().transportMode());
        cache.setTransferCount(best.result().transferCount());
        cache.setFare(best.result().fare());
        cache.setTotalDistanceM(best.result().totalDistanceM());
        cache.setTotalWalkM(best.result().totalWalkM());
        cache.setPathDetail(buildPathDetail(enriched));

        try {
            transitCacheMapper.insert(cache);
            log.debug("TransitCache 저장: total={}분, from={}, to={}", totalMinutes, fromId, toId);
        } catch (Exception e) {
            log.warn("TransitCache 저장 실패: {}", e.getMessage());
        }

        return Optional.of(new TransitResponse(
                totalMinutes, best.result().transportMode(), best.result().transferCount(),
                best.result().fare(), best.result().totalWalkM()));
    }

    @Override
    public Optional<TransitResponse> selectPath(Long fromId, Long toId, int departureHour, int pathIndex) {
        Optional<TransitCache> cachedOpt = transitCacheMapper.findByKey(fromId, toId, departureHour);
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

            return Optional.of(new TransitResponse(totalMinutes, transportMode, transferCount, fare, totalWalkM));
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
        return transitCacheMapper.findByKey(fromAttractionId, toAttractionId, departureHour)
                .map(c -> {
                    try {
                        return objectMapper.readTree(c.getPathDetail());
                    } catch (JsonProcessingException e) {
                        log.warn("path_detail 파싱 실패: {}", e.getMessage());
                        return null;
                    }
                });
    }

    private void saveNoneCache(Long fromId, Long toId, int departureHour) {
        TransitCache none = new TransitCache();
        none.setFromAttractionId(fromId);
        none.setToAttractionId(toId);
        none.setDepartureHour(departureHour);
        none.setDurationMinutes(0);
        none.setTransportMode("NONE");
        none.setTransferCount(0);
        none.setFare(0);
        none.setTotalDistanceM(0);
        none.setTotalWalkM(0);
        none.setPathDetail("{}");
        try {
            transitCacheMapper.insert(none);
            log.debug("NONE 캐시 저장: from={}, to={}", fromId, toId);
        } catch (Exception e) {
            log.warn("NONE 캐시 저장 실패: {}", e.getMessage());
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

    private record RouteEnrichment(
            OdsayClient.OdsayResult result,
            int totalWithLocal,
            OdsayClient.LocalPathResult localFrom,
            OdsayClient.LocalPathResult localTo
    ) {}
}
