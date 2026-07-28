package com.tripcraft.attraction.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripcraft.attraction.client.dto.KakaoCategoryPage;
import com.tripcraft.attraction.client.dto.KakaoPlaceItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * 카카오 로컬 REST API 클라이언트.
 *
 * <p>TourAPI({@link TourApiClient})와 달리 인증이 쿼리 파라미터가 아닌
 * {@code Authorization: KakaoAK {REST_API_KEY}} 헤더 방식이라 전용 {@link RestClient}를 구성한다
 * (헤더 없는 공용 {@code restClient} 빈과 분리). 응답 파싱은 방어적 JsonNode 방식으로 TourApiClient를 미러링한다.
 *
 * @see <a href="https://developers.kakao.com/docs/latest/ko/local/dev-guide">카카오 로컬 API</a>
 */
@Slf4j
@Component
public class KakaoLocalClient {

    private static final String CATEGORY_URL = "https://dapi.kakao.com/v2/local/search/category.json";
    private static final int PAGE_SIZE = 15;      // 카카오 최대 15
    private static final int MAX_PAGE  = 3;       // rect당 최대 45건(15×3) 제한

    private final ObjectMapper objectMapper;
    private final KakaoLocalCallLimiter limiter;
    private final RestClient kakaoRestClient;
    private final boolean configured;

    public KakaoLocalClient(ObjectMapper objectMapper,
                            KakaoLocalCallLimiter limiter,
                            @Value("${kakao.local-api-key:}") String apiKey) {
        this.objectMapper = objectMapper;
        this.limiter = limiter;
        this.configured = apiKey != null && !apiKey.isBlank();
        this.kakaoRestClient = RestClient.builder()
            .defaultHeader("Authorization", "KakaoAK " + (apiKey == null ? "" : apiKey.trim()))
            .build();
    }

    public boolean isConfigured() {
        return configured;
    }

    /**
     * 카테고리 그룹코드 + 사각형(rect) 검색. 한 페이지(최대 15건)를 반환한다.
     *
     * @param categoryGroupCode AT4/FD6/CE7/AD5/CT1 등
     * @param rect              "minLng,minLat,maxLng,maxLat"
     * @param page              1~3 (그 이상은 45건 상한으로 무의미)
     */
    public KakaoCategoryPage searchCategory(String categoryGroupCode, String rect, int page) {
        if (page < 1 || page > MAX_PAGE) return KakaoCategoryPage.empty();
        if (!limiter.tryConsume()) return KakaoCategoryPage.empty();

        String url = CATEGORY_URL
            + "?category_group_code=" + categoryGroupCode
            + "&rect=" + URLEncoder.encode(rect, StandardCharsets.UTF_8)
            + "&size=" + PAGE_SIZE
            + "&page=" + page;
        try {
            String response = kakaoRestClient.get().uri(URI.create(url)).retrieve().body(String.class);
            return parseCategory(response);
        } catch (Exception e) {
            log.warn("카카오 category 검색 실패 code={} rect={} page={}: {}",
                categoryGroupCode, rect, page, e.getMessage());
            return KakaoCategoryPage.empty();
        }
    }

    private KakaoCategoryPage parseCategory(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode meta = root.path("meta");
        boolean isEnd = meta.path("is_end").asBoolean(true);
        int totalCount = meta.path("total_count").asInt(0);

        JsonNode documents = root.path("documents");
        if (!documents.isArray() || documents.isEmpty()) {
            return new KakaoCategoryPage(List.of(), isEnd, totalCount);
        }
        List<KakaoPlaceItem> items = new ArrayList<>(documents.size());
        for (JsonNode d : documents) {
            items.add(new KakaoPlaceItem(
                text(d, "id"),
                text(d, "place_name"),
                text(d, "category_group_code"),
                text(d, "x"),
                text(d, "y"),
                text(d, "address_name"),
                text(d, "road_address_name"),
                text(d, "phone")
            ));
        }
        return new KakaoCategoryPage(items, isEnd, totalCount);
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText();
        return (s == null || s.isBlank()) ? null : s;
    }
}
