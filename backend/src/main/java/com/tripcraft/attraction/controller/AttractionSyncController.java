package com.tripcraft.attraction.controller;

import com.tripcraft.attraction.client.KakaoLocalClient;
import com.tripcraft.attraction.service.KakaoLocalSyncService;
import com.tripcraft.attraction.service.ReferenceDataSyncService;
import com.tripcraft.attraction.service.TourApiSyncService;
import com.tripcraft.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Tag(name = "관리자 - 관광지 동기화", description = "TourAPI 기반 관광지·참조 데이터 수집 (ADMIN 전용)")
@Slf4j
@RestController
@RequestMapping("/api/admin/attractions")
@RequiredArgsConstructor
public class AttractionSyncController {

    private final TourApiSyncService syncService;
    private final ReferenceDataSyncService referenceDataSyncService;
    private final KakaoLocalSyncService kakaoLocalSyncService;
    private final KakaoLocalClient kakaoLocalClient;

    /** 시도·시군구 참조 데이터 동기화 (TourAPI areaCode2 → sido/sigungu, name 갱신·alias 보존) */
    @Operation(summary = "시도·시군구 참조 동기화", description = "TourAPI areaCode2 → sido/sigungu 갱신(alias 보존)")
    @PostMapping("/sync/regions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncRegions() {
        log.info("참조 데이터(시도·시군구) 동기화 시작");
        ReferenceDataSyncService.SyncResult result = referenceDataSyncService.sync();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "sidoCount", result.sidoCount(),
            "sigunguCount", result.sigunguCount(),
            "elapsedMs", result.elapsedMs()
        )));
    }

    /** 전체 수집 (모든 지역 × 콘텐츠 타입). 수 분 소요. */
    @Operation(summary = "전체 관광지 수집", description = "모든 지역 × 콘텐츠 타입. 수 분 소요")
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncAll() {
        log.info("TourAPI 전체 수집 시작");
        TourApiSyncService.SyncResult result = syncService.syncAll();
        log.info("TourAPI 전체 수집 완료: {}건, {}ms", result.total(), result.elapsedMs());
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "total", result.total(),
            "elapsedMs", result.elapsedMs()
        )));
    }

    /** 특정 지역·콘텐츠 타입만 수집 (테스트용) */
    @Operation(summary = "부분 관광지 수집", description = "특정 지역·콘텐츠 타입만 (테스트용)")
    @PostMapping("/sync/partial")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncPartial(
            @RequestParam(name = "areaCode") int areaCode,
            @RequestParam(name = "contentTypeId") int contentTypeId) {
        log.info("TourAPI 부분 수집 시작: areaCode={}, contentTypeId={}", areaCode, contentTypeId);
        TourApiSyncService.SyncResult result = syncService.syncByArea(areaCode, contentTypeId);
        log.info("TourAPI 부분 수집 완료: {}건, {}ms", result.total(), result.elapsedMs());
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "total", result.total(),
            "elapsedMs", result.elapsedMs()
        )));
    }

    /** 카카오 로컬 API 전체 카테고리 대량 수집 (개선 02 Phase A). 수 분~수십 분 소요. */
    @Operation(summary = "카카오 로컬 전체 수집",
        description = "전국 격자 rect × 카테고리(AT4/FD6/CE7/AD5/CT1) 검색으로 대량 장소 수집(source=KAKAO)")
    @PostMapping("/sync/kakao")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncKakao() {
        requireKakaoConfigured();
        log.info("카카오 로컬 전체 수집 시작");
        KakaoLocalSyncService.SyncResult result = kakaoLocalSyncService.syncAll();
        log.info("카카오 로컬 전체 수집 완료: saved={}, skipped={}, apiCalls={}, {}ms, quotaHit={}",
            result.saved(), result.skipped(), result.apiCalls(), result.elapsedMs(), result.quotaHit());
        return ResponseEntity.ok(ApiResponse.ok(kakaoResultMap(result)));
    }

    /** 카카오 로컬 API 특정 카테고리만 수집 (테스트·부분 수집용) */
    @Operation(summary = "카카오 로컬 부분 수집", description = "특정 카테고리 그룹코드만 (AT4/FD6/CE7/AD5/CT1)")
    @PostMapping("/sync/kakao/partial")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncKakaoPartial(
            @RequestParam(name = "categoryGroupCode") String categoryGroupCode) {
        requireKakaoConfigured();
        log.info("카카오 로컬 부분 수집 시작: categoryGroupCode={}", categoryGroupCode);
        KakaoLocalSyncService.SyncResult result = kakaoLocalSyncService.syncByCategory(categoryGroupCode);
        log.info("카카오 로컬 부분 수집 완료: saved={}, skipped={}, apiCalls={}, {}ms, quotaHit={}",
            result.saved(), result.skipped(), result.apiCalls(), result.elapsedMs(), result.quotaHit());
        return ResponseEntity.ok(ApiResponse.ok(kakaoResultMap(result)));
    }

    private void requireKakaoConfigured() {
        if (!kakaoLocalClient.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "KAKAO_LOCAL_API_KEY 미설정 — 카카오 수집 불가");
        }
    }

    private Map<String, Object> kakaoResultMap(KakaoLocalSyncService.SyncResult result) {
        return Map.of(
            "saved", result.saved(),
            "skipped", result.skipped(),
            "apiCalls", result.apiCalls(),
            "elapsedMs", result.elapsedMs(),
            "quotaHit", result.quotaHit()
        );
    }
}
