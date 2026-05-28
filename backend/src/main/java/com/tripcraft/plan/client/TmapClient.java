package com.tripcraft.plan.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TmapClient {

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${tmap.api-key}")
    private String apiKey;

    private static final String TRANSIT_URL = "https://apis.openapi.sk.com/transit/routes";
    private static final DateTimeFormatter DTTM_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public Optional<TmapResult> findTransitRoute(BigDecimal fromLat, BigDecimal fromLng,
                                                  BigDecimal toLat, BigDecimal toLng,
                                                  int departureHour) {
        Map<String, Object> body = Map.of(
                "startX",     fromLng.toPlainString(),
                "startY",     fromLat.toPlainString(),
                "endX",       toLng.toPlainString(),
                "endY",       toLat.toPlainString(),
                "count",      1,
                "lang",       0,
                "format",     "json",
                "searchDttm", LocalDateTime.now().withHour(departureHour).withMinute(0).format(DTTM_FMT)
        );

        try {
            String resp = restClient.post()
                    .uri(TRANSIT_URL)
                    .header("appKey", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            log.debug("T-Map 응답: {}", resp);
            return parse(resp);
        } catch (Exception e) {
            log.warn("T-Map 호출 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<TmapResult> parse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode itineraries = root.path("metaData").path("plan").path("itineraries");
        if (itineraries.isEmpty()) {
            log.debug("T-Map 경로 없음");
            return Optional.empty();
        }

        JsonNode it = itineraries.get(0);
        int durationMinutes = (int) Math.ceil(it.path("totalTime").asInt(0) / 60.0);
        int transferCount   = it.path("transferCount").asInt(0);
        int fare            = it.path("fare").path("regular").path("totalFare").asInt(0);
        int totalDistanceM  = it.path("totalDistance").asInt(0);

        String primaryMode = extractPrimaryMode(it.path("legs"));
        if (primaryMode == null) {
            log.debug("T-Map 경로가 도보 전용 — 결과 무시");
            return Optional.empty();
        }

        return Optional.of(new TmapResult(durationMinutes, primaryMode, transferCount, fare, totalDistanceM));
    }

    /** 비-도보 구간 중 소요 시간이 가장 긴 수단을 주요 이동 수단으로 반환. 도보만 있으면 null. */
    private String extractPrimaryMode(JsonNode legs) {
        String dominantMode = null;
        int maxSectionTime = 0;
        for (JsonNode leg : legs) {
            String mode = leg.path("mode").asText("");
            if (mode.equalsIgnoreCase("WALK")) continue;
            int sectionTime = leg.path("sectionTime").asInt(0);
            if (sectionTime > maxSectionTime) {
                maxSectionTime = sectionTime;
                dominantMode = mode.toUpperCase();
            }
        }
        return dominantMode;
    }

    public record TmapResult(
            int durationMinutes,
            String transportMode,
            int transferCount,
            int fare,
            int totalDistanceM
    ) {}
}
