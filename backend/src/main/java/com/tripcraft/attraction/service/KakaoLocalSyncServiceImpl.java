package com.tripcraft.attraction.service;

import com.tripcraft.attraction.client.KakaoLocalCallLimiter;
import com.tripcraft.attraction.client.KakaoLocalClient;
import com.tripcraft.attraction.client.dto.KakaoCategoryPage;
import com.tripcraft.attraction.client.dto.KakaoPlaceItem;
import com.tripcraft.attraction.domain.Attraction;
import com.tripcraft.attraction.mapper.AttractionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoLocalSyncServiceImpl implements KakaoLocalSyncService {

    private final KakaoLocalClient kakaoLocalClient;
    private final KakaoLocalCallLimiter limiter;
    private final KakaoRegionResolver regionResolver;
    private final AttractionMapper attractionMapper;

    // 전국 bounding box (대략): 경도 124.5~132.0, 위도 33.0~38.7
    private static final double MIN_LNG = 124.5, MAX_LNG = 132.0;
    private static final double MIN_LAT = 33.0,  MAX_LAT = 38.7;
    private static final double INITIAL_STEP = 0.5;  // 격자 초기 셀 크기(도). 밀집지는 quadtree로 세분화
    private static final int    MAX_DEPTH = 4;        // 셀 4분할 최대 깊이
    private static final int    BATCH_SIZE = 500;

    /** 카카오 카테고리 그룹코드 → TourAPI contentTypeId 근사 매핑 */
    private static final Map<String, Integer> CATEGORY_TO_CONTENT_TYPE = Map.of(
        "AT4", 12,  // 관광명소 → 관광지
        "CT1", 14,  // 문화시설 → 문화시설
        "AD5", 32,  // 숙박      → 숙박
        "FD6", 39,  // 음식점    → 음식점
        "CE7", 39   // 카페      → 음식점(근사)
    );

    @Override
    public SyncResult syncAll() {
        Context ctx = new Context();
        long start = System.currentTimeMillis();
        regionResolver.refresh();
        for (String category : SUPPORTED_CATEGORIES) {
            if (ctx.quotaHit) break;
            collectCategory(category, ctx);
        }
        flush(ctx);
        return result(ctx, start);
    }

    @Override
    public SyncResult syncByCategory(String categoryGroupCode) {
        String code = categoryGroupCode == null ? "" : categoryGroupCode.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORY_TO_CONTENT_TYPE.containsKey(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "지원하지 않는 카테고리 그룹코드: " + categoryGroupCode + " (지원: " + SUPPORTED_CATEGORIES + ")");
        }
        Context ctx = new Context();
        long start = System.currentTimeMillis();
        regionResolver.refresh();
        collectCategory(code, ctx);
        flush(ctx);
        return result(ctx, start);
    }

    private void collectCategory(String category, Context ctx) {
        int contentTypeId = CATEGORY_TO_CONTENT_TYPE.get(category);
        log.info("카카오 수집 시작 category={} (contentTypeId={})", category, contentTypeId);
        for (double lng = MIN_LNG; lng < MAX_LNG; lng += INITIAL_STEP) {
            for (double lat = MIN_LAT; lat < MAX_LAT; lat += INITIAL_STEP) {
                if (ctx.quotaHit) return;
                collectCell(lng, lat, Math.min(lng + INITIAL_STEP, MAX_LNG),
                    Math.min(lat + INITIAL_STEP, MAX_LAT), category, contentTypeId, 0, ctx);
            }
        }
        log.info("카카오 수집 종료 category={} saved={} skipped={} apiCalls={}",
            category, ctx.saved, ctx.skipped, ctx.apiCalls);
    }

    /** 한 rect 셀을 조회하고, 45건 상한(capped)에 걸리면 4분할해 재귀 조회한다. */
    private void collectCell(double minLng, double minLat, double maxLng, double maxLat,
                             String category, int contentTypeId, int depth, Context ctx) {
        if (ctx.quotaHit) return;
        String rect = rect(minLng, minLat, maxLng, maxLat);

        boolean reachedEnd = false;
        for (int page = 1; page <= 3; page++) {
            if (limiter.remainingToday() <= 0) { ctx.quotaHit = true; return; }
            KakaoCategoryPage result = kakaoLocalClient.searchCategory(category, rect, page);
            ctx.apiCalls++;
            processItems(result.items(), contentTypeId, ctx);
            if (result.isEnd()) { reachedEnd = true; break; }
        }

        // 3페이지(45건)를 다 쓰고도 끝나지 않음 = 상한 도달 → 셀 세분화
        if (!reachedEnd && depth < MAX_DEPTH) {
            double midLng = (minLng + maxLng) / 2;
            double midLat = (minLat + maxLat) / 2;
            collectCell(minLng, minLat, midLng, midLat, category, contentTypeId, depth + 1, ctx);
            collectCell(midLng, minLat, maxLng, midLat, category, contentTypeId, depth + 1, ctx);
            collectCell(minLng, midLat, midLng, maxLat, category, contentTypeId, depth + 1, ctx);
            collectCell(midLng, midLat, maxLng, maxLat, category, contentTypeId, depth + 1, ctx);
        }
    }

    private void processItems(List<KakaoPlaceItem> items, int contentTypeId, Context ctx) {
        for (KakaoPlaceItem item : items) {
            int[] region = regionResolver.resolve(item.addressName());
            if (region == null) { ctx.skipped++; continue; }
            ctx.buffer.add(toAttraction(item, contentTypeId, region[0], region[1]));
            if (ctx.buffer.size() >= BATCH_SIZE) flush(ctx);
        }
    }

    private Attraction toAttraction(KakaoPlaceItem item, int contentTypeId, int sidoCode, int sigunguCode) {
        Attraction a = new Attraction();
        a.setSource("KAKAO");
        a.setExternalId(item.id());
        a.setContentId(null);
        a.setContentTypeId(contentTypeId);
        a.setTitle(item.placeName());
        a.setSidoCode(sidoCode);
        a.setSigunguCode(sigunguCode);
        a.setAddr1(item.roadAddressName() != null ? item.roadAddressName() : item.addressName());
        a.setLongitude(parseBigDecimal(item.x()));
        a.setLatitude(parseBigDecimal(item.y()));
        a.setTel(item.phone());
        return a;
    }

    private void flush(Context ctx) {
        if (ctx.buffer.isEmpty()) return;
        attractionMapper.insertAll(ctx.buffer);
        ctx.saved += ctx.buffer.size();
        ctx.buffer.clear();
    }

    private SyncResult result(Context ctx, long start) {
        return new SyncResult(ctx.saved, ctx.skipped, ctx.apiCalls,
            System.currentTimeMillis() - start, ctx.quotaHit);
    }

    /** 카카오 rect 파라미터: "왼쪽경도,아래위도,오른쪽경도,위위도" */
    private String rect(double minLng, double minLat, double maxLng, double maxLat) {
        return String.format(Locale.ROOT, "%.6f,%.6f,%.6f,%.6f", minLng, minLat, maxLng, maxLat);
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try { return new BigDecimal(value.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    /** 수집 1회 실행의 가변 상태(카운터 + 업서트 버퍼). */
    private static final class Context {
        final List<Attraction> buffer = new ArrayList<>(BATCH_SIZE);
        int saved = 0;
        int skipped = 0;
        int apiCalls = 0;
        boolean quotaHit = false;
    }
}
