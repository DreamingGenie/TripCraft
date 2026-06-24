package com.tripcraft.plan.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripcraft.global.response.ApiResponse;
import com.tripcraft.plan.dto.TransitResponse;
import com.tripcraft.plan.service.TransitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/transit")
@RequiredArgsConstructor
public class TransitController {

    private final TransitService transitService;

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

    // 좌표 기반 자동차 4옵션(커스텀 장소). attraction /driving-options 와 동일 응답 구조.
    @GetMapping("/by-coords/driving-options")
    public ResponseEntity<ApiResponse<List<TransitResponse>>> getDrivingOptionsByCoords(
            @RequestParam("fromLat") double fromLat, @RequestParam("fromLng") double fromLng,
            @RequestParam("toLat") double toLat, @RequestParam("toLng") double toLng,
            @RequestParam(name = "hour", defaultValue = "9") int hour,
            @RequestParam(name = "optionIndex", required = false) Integer optionIndex) {
        java.math.BigDecimal fLat = java.math.BigDecimal.valueOf(fromLat), fLng = java.math.BigDecimal.valueOf(fromLng);
        java.math.BigDecimal tLat = java.math.BigDecimal.valueOf(toLat),   tLng = java.math.BigDecimal.valueOf(toLng);
        if (optionIndex != null) {
            return ResponseEntity.ok(ApiResponse.ok(
                    transitService.getDrivingOptionByCoords(fLat, fLng, tLat, tLng, hour, optionIndex)
                            .map(List::of).orElse(List.of())));
        }
        return ResponseEntity.ok(ApiResponse.ok(
                transitService.getDrivingOptionsByCoords(fLat, fLng, tLat, tLng, hour)));
    }

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

    @GetMapping("/driving-options")
    public ResponseEntity<ApiResponse<List<TransitResponse>>> getDrivingOptions(
            @RequestParam(name = "fromId") Long fromId,
            @RequestParam(name = "toId") Long toId,
            @RequestParam(name = "hour", defaultValue = "9") int hour,
            @RequestParam(name = "optionIndex", required = false) Integer optionIndex) {
        if (optionIndex != null) {
            return ResponseEntity.ok(ApiResponse.ok(
                    transitService.getDrivingOption(fromId, toId, hour, optionIndex)
                            .map(List::of).orElse(List.of())));
        }
        return ResponseEntity.ok(ApiResponse.ok(transitService.getDrivingOptions(fromId, toId, hour)));
    }

    @PostMapping("/select-driving")
    public ResponseEntity<ApiResponse<Void>> applyDrivingOption(
            @RequestParam(name = "fromId") Long fromId,
            @RequestParam(name = "toId") Long toId,
            @RequestParam(name = "hour", defaultValue = "9") int hour,
            @RequestParam(name = "optionIndex") int optionIndex) {
        transitService.applyDrivingOption(fromId, toId, hour, optionIndex);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/detail")
    public ResponseEntity<ApiResponse<JsonNode>> getPathDetail(
            @RequestParam(name = "fromId") Long fromId,
            @RequestParam(name = "toId") Long toId,
            @RequestParam(name = "hour", defaultValue = "9") int hour) {

        return transitService.getPathDetail(fromId, toId, hour)
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)))
                .orElse(ResponseEntity.ok(ApiResponse.ok(null)));
    }

    @GetMapping("/segments")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> getLaneSegments(
            @RequestParam(name = "fromId") Long fromId,
            @RequestParam(name = "toId") Long toId,
            @RequestParam(name = "hour", defaultValue = "9") int hour) {
        return ResponseEntity.ok(ApiResponse.ok(transitService.getLaneSegments(fromId, toId, hour)));
    }

    // 통합 경로 구간(어트랙션) — 구간별 색/도보/역마커용
    @GetMapping("/route-segments")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> getRouteSegments(
            @RequestParam(name = "fromId") Long fromId,
            @RequestParam(name = "toId") Long toId,
            @RequestParam(name = "hour", defaultValue = "9") int hour) {
        return ResponseEntity.ok(ApiResponse.ok(transitService.getRouteSegments(fromId, toId, hour)));
    }

    // 통합 경로 구간(커스텀 좌표)
    @GetMapping("/by-coords/route-segments")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> getRouteSegmentsByCoords(
            @RequestParam("fromLat") double fromLat, @RequestParam("fromLng") double fromLng,
            @RequestParam("toLat") double toLat, @RequestParam("toLng") double toLng,
            @RequestParam(name = "hour", defaultValue = "9") int hour) {
        return ResponseEntity.ok(ApiResponse.ok(transitService.getRouteSegmentsByCoords(
                java.math.BigDecimal.valueOf(fromLat), java.math.BigDecimal.valueOf(fromLng),
                java.math.BigDecimal.valueOf(toLat), java.math.BigDecimal.valueOf(toLng), hour)));
    }

    @GetMapping("/walking-coords")
    public ResponseEntity<ApiResponse<List<double[]>>> getWalkingCoords(
            @RequestParam double startLat, @RequestParam double startLng,
            @RequestParam double endLat,   @RequestParam double endLng) {
        return ResponseEntity.ok(ApiResponse.ok(transitService.getWalkingCoords(startLat, startLng, endLat, endLng)));
    }
}
