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
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OdsayClient {

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${odsay.api-key}")
    private String apiKey;

    private static final String BASE_URL = "https://api.odsay.com/v1/api/searchPubTransPathT";

    public Optional<OdsayResult> findTransitPath(BigDecimal fromLat, BigDecimal fromLng,
                                                  BigDecimal toLat, BigDecimal toLng) {
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        // SearchType=0: 도시내+도시간 통합 탐색 (전국)
        String url = BASE_URL + "?lang=0&SearchType=0&SX=" + fromLng + "&SY=" + fromLat
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
                return Optional.empty();
            }

            JsonNode paths = root.path("result").path("path");
            if (paths.isEmpty()) return Optional.empty();

            JsonNode first = paths.get(0);
            JsonNode info = first.path("info");

            int totalTime      = info.path("totalTime").asInt(0);
            // 도시간 경로는 totalPayment, 도시내 경로는 payment 사용
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

            String mode = extractMode(first);
            if (mode == null) {
                log.debug("ODsay 경로가 도보 전용 — 결과 무시");
                return Optional.empty();
            }
            String pathDetail = objectMapper.writeValueAsString(first);
            return Optional.of(new OdsayResult(totalTime, mode, transferCount, fare, totalDistanceM, totalWalkM, pathDetail));
        } catch (Exception e) {
            log.warn("ODsay 호출 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * trafficType: 1=지하철 2=버스 3=도보 4=기차(KTX/SRT/무궁화) 5=항공 6=시외/고속버스
     * 비-도보 구간이 없으면 null 반환 → 호출 측에서 Optional.empty() 처리
     */
    private String extractMode(JsonNode path) {
        boolean hasRail = false;
        boolean hasSubway = false;
        boolean hasExpressBus = false;
        boolean hasBus = false;
        for (JsonNode sub : path.path("subPath")) {
            int type = sub.path("trafficType").asInt();
            if (type == 4) hasRail = true;
            if (type == 1) hasSubway = true;
            if (type == 6) hasExpressBus = true;
            if (type == 2) hasBus = true;
        }
        if (hasRail)        return "RAIL";
        if (hasSubway)      return "SUBWAY";
        if (hasExpressBus)  return "EXPRESSBUS";
        if (hasBus)         return "BUS";
        return null;
    }

    public record OdsayResult(
            int durationMinutes,
            String transportMode,
            int transferCount,
            int fare,
            int totalDistanceM,
            int totalWalkM,
            String pathDetail
    ) {}
}
