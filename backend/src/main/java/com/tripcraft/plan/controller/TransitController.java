package com.tripcraft.plan.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripcraft.global.response.ApiResponse;
import com.tripcraft.plan.dto.TransitResponse;
import com.tripcraft.plan.service.TransitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Tag(name = "이동 시간", description = "ODsay·T Map 기반 이동시간·경로 (관광지 id / 좌표)")
@RestController
@RequestMapping("/api/transit")
@RequiredArgsConstructor
public class TransitController {

    private final TransitService transitService;

    @Operation(summary = "두 관광지 간 이동시간", description = "mode=PUBLIC_TRANSIT/DRIVING/WALKING")
    @GetMapping
    public ResponseEntity<ApiResponse<TransitResponse>> getTransitTime(
            @RequestParam(name = "fromId") Long fromId,
            @RequestParam(name = "toId") Long toId,
            @RequestParam(name = "hour", defaultValue = "9") int hour,
            @RequestParam(name = "mode", defaultValue = "PUBLIC_TRANSIT") String mode) {

        return transitService.getTransitTime(fromId, toId, hour, mode)
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)))
                .orElse(ResponseEntity.ok(ApiResponse.ok(null)));
    }

    // 좌표 기반 이동시간(커스텀 장소). 한쪽이라도 attraction id 가 없을 때 사용.
    @Operation(summary = "좌표 기반 이동시간", description = "커스텀 장소(관광지 id 없음)용")
    @GetMapping("/by-coords")
    public ResponseEntity<ApiResponse<TransitResponse>> getTransitByCoords(
            @RequestParam("fromLat") double fromLat, @RequestParam("fromLng") double fromLng,
            @RequestParam("toLat") double toLat, @RequestParam("toLng") double toLng,
            @RequestParam(name = "hour", defaultValue = "9") int hour,
            @RequestParam(name = "mode", defaultValue = "PUBLIC_TRANSIT") String mode) {
        return transitService.getTransitByCoords(
                        java.math.BigDecimal.valueOf(fromLat), java.math.BigDecimal.valueOf(fromLng),
                        java.math.BigDecimal.valueOf(toLat), java.math.BigDecimal.valueOf(toLng), hour, mode)
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)))
                .orElse(ResponseEntity.ok(ApiResponse.ok(null)));
    }

    // 좌표 기반 대중교통 경로 단계(커스텀 장소). attraction /detail 과 동일 응답 구조.
    @Operation(summary = "좌표 기반 경로 단계 상세")
    @GetMapping("/by-coords/detail")
    public ResponseEntity<ApiResponse<JsonNode>> getTransitDetailByCoords(
            @RequestParam("fromLat") double fromLat, @RequestParam("fromLng") double fromLng,
            @RequestParam("toLat") double toLat, @RequestParam("toLng") double toLng,
            @RequestParam(name = "hour", defaultValue = "9") int hour) {
        return transitService.getTransitDetailByCoords(
                        java.math.BigDecimal.valueOf(fromLat), java.math.BigDecimal.valueOf(fromLng),
                        java.math.BigDecimal.valueOf(toLat), java.math.BigDecimal.valueOf(toLng), hour)
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)))
                .orElse(ResponseEntity.ok(ApiResponse.ok(null)));
    }

    // 좌표 기반 자동차 단일 옵션(커스텀 장소). 프론트가 optionIndex 별로 1개씩 조회.
    @Operation(summary = "좌표 기반 자동차 옵션")
    @GetMapping("/by-coords/driving-options")
    public ResponseEntity<ApiResponse<List<TransitResponse>>> getDrivingOptionByCoords(
            @RequestParam("fromLat") double fromLat, @RequestParam("fromLng") double fromLng,
            @RequestParam("toLat") double toLat, @RequestParam("toLng") double toLng,
            @RequestParam(name = "hour", defaultValue = "9") int hour,
            @RequestParam(name = "optionIndex", defaultValue = "0") int optionIndex) {
        return ResponseEntity.ok(ApiResponse.ok(
                transitService.getDrivingOptionByCoords(
                                java.math.BigDecimal.valueOf(fromLat), java.math.BigDecimal.valueOf(fromLng),
                                java.math.BigDecimal.valueOf(toLat), java.math.BigDecimal.valueOf(toLng), hour, optionIndex)
                        .map(List::of).orElse(List.of())));
    }

    @Operation(summary = "대중교통 경로 선택", description = "pathIndex로 경로 확정")
    @PostMapping("/select")
    public ResponseEntity<ApiResponse<TransitResponse>> selectPath(
            @RequestParam(name = "fromId") Long fromId,
            @RequestParam(name = "toId") Long toId,
            @RequestParam(name = "hour", defaultValue = "9") int hour,
            @RequestParam(name = "pathIndex") int pathIndex) {

        return transitService.selectPath(fromId, toId, hour, pathIndex)
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)))
                .orElse(ResponseEntity.ok(ApiResponse.ok(null)));
    }

    // 자동차 단일 옵션. 프론트가 optionIndex(추천·최단·무료·최소) 별로 1개씩 조회.
    @Operation(summary = "자동차 옵션 조회", description = "optionIndex: 추천·최단·무료·최소")
    @GetMapping("/driving-options")
    public ResponseEntity<ApiResponse<List<TransitResponse>>> getDrivingOption(
            @RequestParam(name = "fromId") Long fromId,
            @RequestParam(name = "toId") Long toId,
            @RequestParam(name = "hour", defaultValue = "9") int hour,
            @RequestParam(name = "optionIndex", defaultValue = "0") int optionIndex) {
        return ResponseEntity.ok(ApiResponse.ok(
                transitService.getDrivingOption(fromId, toId, hour, optionIndex)
                        .map(List::of).orElse(List.of())));
    }

    @Operation(summary = "자동차 옵션 적용")
    @PostMapping("/select-driving")
    public ResponseEntity<ApiResponse<Void>> applyDrivingOption(
            @RequestParam(name = "fromId") Long fromId,
            @RequestParam(name = "toId") Long toId,
            @RequestParam(name = "hour", defaultValue = "9") int hour,
            @RequestParam(name = "optionIndex") int optionIndex) {
        transitService.applyDrivingOption(fromId, toId, hour, optionIndex);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "대중교통 경로 단계 상세")
    @GetMapping("/detail")
    public ResponseEntity<ApiResponse<JsonNode>> getPathDetail(
            @RequestParam(name = "fromId") Long fromId,
            @RequestParam(name = "toId") Long toId,
            @RequestParam(name = "hour", defaultValue = "9") int hour) {

        return transitService.getPathDetail(fromId, toId, hour)
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)))
                .orElse(ResponseEntity.ok(ApiResponse.ok(null)));
    }

    // 통합 경로 구간(어트랙션) — 구간별 색/도보/역마커용
    @Operation(summary = "경로 구간 조회", description = "구간별 색·도보·역마커용")
    @GetMapping("/route-segments")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> getRouteSegments(
            @RequestParam(name = "fromId") Long fromId,
            @RequestParam(name = "toId") Long toId,
            @RequestParam(name = "hour", defaultValue = "9") int hour) {
        return ResponseEntity.ok(ApiResponse.ok(transitService.getRouteSegments(fromId, toId, hour)));
    }

    // 통합 경로 구간(커스텀 좌표)
    @Operation(summary = "좌표 기반 경로 구간")
    @GetMapping("/by-coords/route-segments")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> getRouteSegmentsByCoords(
            @RequestParam("fromLat") double fromLat, @RequestParam("fromLng") double fromLng,
            @RequestParam("toLat") double toLat, @RequestParam("toLng") double toLng,
            @RequestParam(name = "hour", defaultValue = "9") int hour) {
        return ResponseEntity.ok(ApiResponse.ok(transitService.getRouteSegmentsByCoords(
                java.math.BigDecimal.valueOf(fromLat), java.math.BigDecimal.valueOf(fromLng),
                java.math.BigDecimal.valueOf(toLat), java.math.BigDecimal.valueOf(toLng), hour)));
    }

    @Operation(summary = "도보 경로 좌표")
    @GetMapping("/walking-coords")
    public ResponseEntity<ApiResponse<List<double[]>>> getWalkingCoords(
            @RequestParam double startLat, @RequestParam double startLng,
            @RequestParam double endLat,   @RequestParam double endLng) {
        return ResponseEntity.ok(ApiResponse.ok(transitService.getWalkingCoords(startLat, startLng, endLat, endLng)));
    }
}
