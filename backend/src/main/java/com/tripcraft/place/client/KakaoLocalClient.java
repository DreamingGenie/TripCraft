package com.tripcraft.place.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripcraft.place.dto.PlaceSearchItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Kakao Local 키워드 검색(REST키 = kakao.client-id, KakaoAK 헤더). 좌표·분류 변환. */
@Slf4j
@Component
public class KakaoLocalClient {

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${kakao.client-id:}")
    private String restKey;

    private static final String URL = "https://dapi.kakao.com/v2/local/search/keyword.json";

    public List<PlaceSearchItem> searchKeyword(String query) {
        if (restKey == null || restKey.isBlank() || query == null || query.isBlank()) return List.of();
        String uri = UriComponentsBuilder.fromHttpUrl(URL)
                .queryParam("query", query)
                .queryParam("size", 15)
                .encode()
                .toUriString();
        try {
            String body = restClient.get()
                    .uri(uri)
                    .header("Authorization", "KakaoAK " + restKey)
                    .retrieve()
                    .body(String.class);
            JsonNode docs = objectMapper.readTree(body).path("documents");
            List<PlaceSearchItem> items = new ArrayList<>();
            for (JsonNode d : docs) {
                String name = d.path("place_name").asText("");
                String addr = d.path("road_address_name").asText("");
                if (addr.isBlank()) addr = d.path("address_name").asText("");
                String cat = mapCategory(d.path("category_group_code").asText(""));
                BigDecimal lng = parseDecimal(d.path("x").asText(""));
                BigDecimal lat = parseDecimal(d.path("y").asText(""));
                items.add(new PlaceSearchItem(name, addr, cat, lat, lng));
            }
            return items;
        } catch (Exception e) {
            log.warn("Kakao Local 검색 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private BigDecimal parseDecimal(String s) {
        try { return (s == null || s.isBlank()) ? null : new BigDecimal(s); }
        catch (NumberFormatException e) { return null; }
    }

    /** Kakao category_group_code → 앱 6분류(미매칭은 관광지). 최종 분류는 사용자가 폼에서 조정 가능. */
    private String mapCategory(String code) {
        return switch (code) {
            case "FD6", "CE7" -> "음식점";
            case "AD5" -> "숙박";
            case "CT1" -> "문화시설";
            case "MT1", "CS2" -> "쇼핑";
            default -> "관광지";
        };
    }
}
