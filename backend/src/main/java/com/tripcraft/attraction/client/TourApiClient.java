package com.tripcraft.attraction.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripcraft.attraction.client.dto.TourApiItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TourApiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${tour-api.service-key}")
    private String serviceKey;

    @Value("${tour-api.base-url}")
    private String baseUrl;

    public List<TourApiItem> fetchAreaList(int areaCode, int contentTypeId, int pageNo, int numOfRows) {
        String url = buildUrl(areaCode, contentTypeId, pageNo, numOfRows);
        try {
            String response = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            return parseItems(response);
        } catch (Exception e) {
            log.error("TourAPI 호출 실패 areaCode={} contentTypeId={} pageNo={}: {}", areaCode, contentTypeId, pageNo, e.getMessage());
            return List.of();
        }
    }

    private String buildUrl(int areaCode, int contentTypeId, int pageNo, int numOfRows) {
        return baseUrl + "/areaBasedList2"
            + "?serviceKey=" + serviceKey
            + "&MobileOS=ETC"
            + "&MobileApp=TripCraft"
            + "&_type=json"
            + "&areaCode=" + areaCode
            + "&contentTypeId=" + contentTypeId
            + "&numOfRows=" + numOfRows
            + "&pageNo=" + pageNo;
    }

    private List<TourApiItem> parseItems(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json).path("response");
        JsonNode header = root.path("header");
        String resultCode = header.path("resultCode").asText("unknown");
        if (!"0000".equals(resultCode)) {
            log.warn("TourAPI 오류 응답 resultCode={} resultMsg={}", resultCode, header.path("resultMsg").asText());
            return List.of();
        }

        JsonNode body = root.path("body");
        JsonNode itemsNode = body.path("items");

        // items가 빈 문자열 "" 인 경우 (결과 없음)
        if (itemsNode.isTextual() || itemsNode.isMissingNode()) {
            return List.of();
        }

        JsonNode itemNode = itemsNode.path("item");
        if (itemNode.isMissingNode() || itemNode.isNull()) {
            return List.of();
        }
        // 단일 객체인 경우 배열로 감쌈
        if (itemNode.isObject()) {
            return List.of(objectMapper.treeToValue(itemNode, TourApiItem.class));
        }
        return objectMapper.treeToValue(itemNode, new TypeReference<>() {});
    }
}
