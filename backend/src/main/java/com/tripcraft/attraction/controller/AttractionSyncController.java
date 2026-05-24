package com.tripcraft.attraction.controller;

import com.tripcraft.attraction.service.TourApiSyncService;
import com.tripcraft.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/attractions")
@RequiredArgsConstructor
public class AttractionSyncController {

    private final TourApiSyncService syncService;

    /** 전체 수집 (모든 지역 × 콘텐츠 타입). 수 분 소요. */
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
    @PostMapping("/sync/partial")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncPartial(
            @RequestParam int areaCode,
            @RequestParam int contentTypeId) {
        log.info("TourAPI 부분 수집 시작: areaCode={}, contentTypeId={}", areaCode, contentTypeId);
        TourApiSyncService.SyncResult result = syncService.syncByArea(areaCode, contentTypeId);
        log.info("TourAPI 부분 수집 완료: {}건, {}ms", result.total(), result.elapsedMs());
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "total", result.total(),
            "elapsedMs", result.elapsedMs()
        )));
    }
}
