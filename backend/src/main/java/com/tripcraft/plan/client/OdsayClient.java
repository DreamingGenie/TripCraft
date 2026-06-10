package com.tripcraft.plan.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OdsayClient {

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${odsay.api-key}")
    private String apiKey;

    private static final String BASE_URL      = "https://api.odsay.com/v1/api/searchPubTransPathT";
    private static final String LOAD_LANE_URL = "https://api.odsay.com/v1/api/loadLane";

    /** ODsay 응답의 모든 경로를 파싱해 반환. 도보 전용 경로는 제외. 오류 시 빈 리스트 반환. */
    public List<OdsayResult> findTransitPath(BigDecimal fromLat, BigDecimal fromLng,
                                              BigDecimal toLat, BigDecimal toLng) {
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String url = BASE_URL + "?lang=0&SX=" + fromLng + "&SY=" + fromLat
                + "&EX=" + toLng + "&EY=" + toLat + "&apiKey=" + encodedKey;
        log.debug("ODsay 요청 URL: {}", url);
        try {
            String body = restClient.get()
                    .uri(URI.create(url))
                    .header("Referer", "http://localhost:5173")
                    .retrieve()
                    .body(String.class);

            log.debug("ODsay 응답: {}", body);
            JsonNode root = objectMapper.readTree(body);

            if (root.has("error")) {
                JsonNode err = root.path("error");
                String msg = err.isArray() ? err.get(0).path("message").asText()
                                           : err.path("msg").asText(err.path("message").asText());
                log.warn("ODsay 오류: {}", msg);
                return List.of();
            }

            JsonNode paths = root.path("result").path("path");
            if (paths.isEmpty()) return List.of();

            List<OdsayResult> results = new ArrayList<>();
            for (int i = 0; i < paths.size(); i++) {
                JsonNode pathNode = paths.get(i);
                OdsayResult result = parsePath(i, pathNode);
                if (result != null) results.add(result);
            }
            log.debug("ODsay 유효 경로 {}개 파싱 완료 (전체 {}개)", results.size(), paths.size());
            return results;

        } catch (Exception e) {
            log.warn("ODsay 호출 실패: {}", e.getMessage());
            return List.of();
        }
    }

    /** 도시내 경로(SearchType=0)로 전체 경로 정보 반환.
     *  700m 이내(-98) → Haversine 추정(estimated=true). 도시간 결과·오류 → empty. */
    public Optional<LocalPathResult> findLocalPath(BigDecimal fromLat, BigDecimal fromLng,
                                                    BigDecimal toLat, BigDecimal toLng) {
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String url = BASE_URL + "?lang=0&SearchType=0&SX=" + fromLng + "&SY=" + fromLat
                + "&EX=" + toLng + "&EY=" + toLat + "&apiKey=" + encodedKey;
        try {
            String body = restClient.get()
                    .uri(URI.create(url))
                    .header("Referer", "http://localhost:5173")
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            if (root.has("error")) {
                int code = root.path("error").isArray()
                        ? root.path("error").get(0).path("code").asInt(0)
                        : root.path("error").path("code").asInt(0);
                if (code == -98) {
                    return Optional.of(new LocalPathResult(
                            haversineMinutes(fromLat, fromLng, toLat, toLng, 5.0), true, null));
                }
                return Optional.empty();
            }

            JsonNode paths = root.path("result").path("path");
            if (paths.isEmpty()) return Optional.empty();

            JsonNode first = paths.get(0);
            if (first.path("pathType").asInt(0) >= 11) return Optional.empty();

            int totalTime = first.path("info").path("totalTime").asInt(0);
            if (totalTime <= 0) return Optional.empty();
            return Optional.of(new LocalPathResult(totalTime, false, first));

        } catch (Exception e) {
            log.warn("ODsay 도시내 호출 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Haversine 직선거리 기반 소요 시간 추정. 도시내 API 실패 시 폴백으로 사용. */
    public int haversineMinutes(BigDecimal fromLat, BigDecimal fromLng,
                                 BigDecimal toLat, BigDecimal toLng,
                                 double avgSpeedKmh) {
        double R = 6371.0;
        double dLat = Math.toRadians(toLat.doubleValue() - fromLat.doubleValue());
        double dLon = Math.toRadians(toLng.doubleValue() - fromLng.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(fromLat.doubleValue()))
                 * Math.cos(Math.toRadians(toLat.doubleValue()))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double distKm = R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.max(1, (int) Math.ceil(distKm / avgSpeedKmh * 60 * 1.5));
    }

    private OdsayResult parsePath(int pathIndex, JsonNode pathNode) {
        try {
            String mode = extractMode(pathNode);
            if (mode == null) return null; // 도보 전용 경로 제외

            JsonNode info = pathNode.path("info");
            int totalTime      = info.path("totalTime").asInt(0);
            // 도시간 경로는 totalPayment, 도시내 경로는 payment
            int fare = info.path("totalPayment").asInt(0);
            if (fare == 0) fare = info.path("payment").asInt(0);
            int totalDistanceM = info.path("totalDistance").asInt(0);
            int totalWalkM     = info.path("totalWalk").asInt(0);
            // 도시간 경로는 transitCount, 도시내 경로는 busTransitCount + subwayTransitCount
            int transitCount = info.path("transitCount").asInt(-1);
            int transferCount;
            if (transitCount >= 0) {
                transferCount = Math.max(0, transitCount - 1);
            } else {
                int busCount    = info.path("busTransitCount").asInt(0);
                int subwayCount = info.path("subwayTransitCount").asInt(0);
                transferCount = Math.max(0, busCount + subwayCount - 1);
            }
            int pathType      = pathNode.path("pathType").asInt(0);
            String pathDetail = objectMapper.writeValueAsString(pathNode);
            return new OdsayResult(pathIndex, totalTime, mode, transferCount, fare, totalDistanceM, totalWalkM, pathDetail, pathType);
        } catch (Exception e) {
            log.warn("ODsay 경로 파싱 실패 index={}: {}", pathIndex, e.getMessage());
            return null;
        }
    }

    /**
     * trafficType: 1=지하철 2=버스 3=도보 4=기차(KTX/SRT 등) 5=고속버스 6=시외버스 7=항공
     * 경로에 등장하는 순서대로 모든 비-도보 수단을 콤마로 연결해 반환.
     * 비-도보 구간이 없으면 null 반환.
     */
    private String extractMode(JsonNode path) {
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
            if (mode != null && (modes.isEmpty() || !modes.get(modes.size() - 1).equals(mode))) {
                modes.add(mode);
            }
        }
        return modes.isEmpty() ? null : String.join(",", modes);
    }

    /**
     * ODsay loadLane API — 노선 구간의 실제 폴리라인 좌표를 반환.
     * mapObject 형식: @{idx}:{trafficType}:{routeLocalID}:{startStationID}:{endStationID}
     * 여러 구간은 '@'로 구분해 한 번에 요청 가능.
     * 반환: [[lng, lat], ...] JSON 문자열, 실패 또는 좌표 없으면 null.
     */
    public String loadLane(String mapObject) {
        try {
            String encodedKey    = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            String encodedObject = URLEncoder.encode(mapObject, StandardCharsets.UTF_8);
            String url = LOAD_LANE_URL + "?mapObject=" + encodedObject + "&apiKey=" + encodedKey;
            log.debug("ODsay loadLane 요청: mapObject={}", mapObject);

            String body = restClient.get()
                    .uri(URI.create(url))
                    .header("Referer", "http://localhost:5173")
                    .retrieve()
                    .body(String.class);

            log.debug("ODsay loadLane 응답: {}", body);
            JsonNode root = objectMapper.readTree(body);
            if (root.has("error")) {
                log.warn("ODsay loadLane 오류: {}", root.path("error"));
                return null;
            }

            List<double[]> coords = new ArrayList<>();
            for (JsonNode lane : root.path("result").path("lane")) {
                for (JsonNode section : lane.path("section")) {
                    for (JsonNode pos : section.path("graphPos")) {
                        double x = pos.path("x").asDouble(0);
                        double y = pos.path("y").asDouble(0);
                        if (x != 0 && y != 0) coords.add(new double[]{x, y});
                    }
                }
            }
            if (coords.isEmpty()) return null;
            return objectMapper.writeValueAsString(coords);
        } catch (Exception e) {
            log.warn("ODsay loadLane 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    public record OdsayResult(
            int pathIndex,
            int durationMinutes,
            String transportMode,
            int transferCount,
            int fare,
            int totalDistanceM,
            int totalWalkM,
            String pathDetail,
            int pathType
    ) {}

    public record LocalPathResult(
            int minutes,
            boolean estimated,
            JsonNode pathNode   // null when estimated=true
    ) {}
}
