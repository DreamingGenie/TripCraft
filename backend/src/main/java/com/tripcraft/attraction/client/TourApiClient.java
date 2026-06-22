package com.tripcraft.attraction.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripcraft.attraction.client.dto.TourApiDetailCommonItem;
import com.tripcraft.attraction.client.dto.TourApiDetailImageItem;
import com.tripcraft.attraction.client.dto.TourApiDetailInfoItem;
import com.tripcraft.attraction.client.dto.TourApiAreaItem;
import com.tripcraft.attraction.client.dto.TourApiDetailIntroItem;
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
    private final TourApiCallLimiter limiter;

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

    /** areaCode2: 시도 목록 (areaCode 미지정). 응답 {code, name}. */
    public List<TourApiAreaItem> fetchSidoList() {
        String url = baseUrl + "/areaCode2"
            + "?serviceKey=" + serviceKey
            + "&MobileOS=ETC&MobileApp=TripCraft&_type=json"
            + "&numOfRows=50&pageNo=1";
        try {
            String response = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            return parseListItems(response, TourApiAreaItem.class);
        } catch (Exception e) {
            log.error("areaCode2(시도) 호출 실패: {}", e.getMessage());
            return List.of();
        }
    }

    /** areaCode2: 특정 시도의 시군구 목록 (areaCode 지정). 응답 {code, name}. */
    public List<TourApiAreaItem> fetchSigunguList(int sidoCode) {
        String url = baseUrl + "/areaCode2"
            + "?serviceKey=" + serviceKey
            + "&MobileOS=ETC&MobileApp=TripCraft&_type=json"
            + "&numOfRows=50&pageNo=1"
            + "&areaCode=" + sidoCode;
        try {
            String response = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            return parseListItems(response, TourApiAreaItem.class);
        } catch (Exception e) {
            log.error("areaCode2(시군구) 호출 실패 areaCode={}: {}", sidoCode, e.getMessage());
            return List.of();
        }
    }

    public TourApiDetailCommonItem fetchDetailCommon(String contentId) {
        if (!limiter.tryConsume()) return null;
        String url = baseUrl + "/detailCommon2"
            + "?serviceKey=" + serviceKey
            + "&MobileOS=ETC&MobileApp=TripCraft&_type=json"
            + "&contentId=" + contentId;
        try {
            String response = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(response).path("response");
            if (!"0000".equals(root.path("header").path("resultCode").asText())) return null;
            JsonNode item = parseSingleItem(root);
            return item == null ? null : objectMapper.treeToValue(item, TourApiDetailCommonItem.class);
        } catch (Exception e) {
            log.warn("detailCommon2 실패 contentId={}: {}", contentId, e.getMessage());
            return null;
        }
    }

    public TourApiDetailIntroItem fetchDetailIntro(String contentId, int contentTypeId) {
        if (!limiter.tryConsume()) return null;
        String url = baseUrl + "/detailIntro2"
            + "?serviceKey=" + serviceKey
            + "&MobileOS=ETC&MobileApp=TripCraft&_type=json"
            + "&contentId=" + contentId
            + "&contentTypeId=" + contentTypeId;
        try {
            String response = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(response).path("response");
            if (!"0000".equals(root.path("header").path("resultCode").asText())) return null;
            JsonNode item = parseSingleItem(root);
            return item == null ? null : objectMapper.treeToValue(item, TourApiDetailIntroItem.class);
        } catch (Exception e) {
            log.warn("detailIntro2 실패 contentId={}: {}", contentId, e.getMessage());
            return null;
        }
    }

    public List<TourApiDetailImageItem> fetchDetailImage(String contentId) {
        if (!limiter.tryConsume()) return List.of();
        String url = baseUrl + "/detailImage2"
            + "?serviceKey=" + serviceKey
            + "&MobileOS=ETC&MobileApp=TripCraft&_type=json"
            + "&contentId=" + contentId;
        try {
            String response = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            return parseListItems(response, TourApiDetailImageItem.class);
        } catch (Exception e) {
            log.warn("detailImage2 실패 contentId={}: {}", contentId, e.getMessage());
            return List.of();
        }
    }

    public List<TourApiDetailInfoItem> fetchDetailInfo(String contentId, int contentTypeId) {
        if (!limiter.tryConsume()) return List.of();
        String url = baseUrl + "/detailInfo2"
            + "?serviceKey=" + serviceKey
            + "&MobileOS=ETC&MobileApp=TripCraft&_type=json"
            + "&contentId=" + contentId
            + "&contentTypeId=" + contentTypeId;
        try {
            String response = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            return parseListItems(response, TourApiDetailInfoItem.class);
        } catch (Exception e) {
            log.warn("detailInfo2 실패 contentId={}: {}", contentId, e.getMessage());
            return List.of();
        }
    }

    private JsonNode parseSingleItem(JsonNode root) {
        JsonNode body = root.path("body");
        JsonNode itemsNode = body.path("items");
        if (itemsNode.isTextual() || itemsNode.isMissingNode()) return null;
        JsonNode itemNode = itemsNode.path("item");
        if (itemNode.isMissingNode() || itemNode.isNull()) return null;
        if (itemNode.isArray()) {
            return itemNode.isEmpty() ? null : itemNode.get(0);
        }
        return itemNode.isObject() ? itemNode : null;
    }

    private <T> List<T> parseListItems(String json, Class<T> clazz) throws Exception {
        JsonNode root = objectMapper.readTree(json).path("response");
        if (!"0000".equals(root.path("header").path("resultCode").asText())) return List.of();
        JsonNode body = root.path("body");
        JsonNode itemsNode = body.path("items");
        if (itemsNode.isTextual() || itemsNode.isMissingNode()) return List.of();
        JsonNode itemNode = itemsNode.path("item");
        if (itemNode.isMissingNode() || itemNode.isNull()) return List.of();
        if (itemNode.isObject()) return List.of(objectMapper.treeToValue(itemNode, clazz));
        return objectMapper.treeToValue(itemNode,
            objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
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
