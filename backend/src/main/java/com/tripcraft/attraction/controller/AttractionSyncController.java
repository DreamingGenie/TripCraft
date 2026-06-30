package com.tripcraft.attraction.controller;

import com.tripcraft.attraction.service.ReferenceDataSyncService;
import com.tripcraft.attraction.service.TourApiSyncService;
import com.tripcraft.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "관리자 - 관광지 동기화", description = "TourAPI 기반 관광지·참조 데이터 수집 (ADMIN 전용)")
@Slf4j
@RestController
@RequestMapping("/api/admin/attractions")
@RequiredArgsConstructor
public class AttractionSyncController {

    private final TourApiSyncService syncService;
    private final ReferenceDataSyncService referenceDataSyncService;

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
}
