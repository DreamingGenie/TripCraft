package com.tripcraft.plan.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripcraft.global.response.ApiResponse;
import com.tripcraft.plan.dto.TransitResponse;
import com.tripcraft.plan.service.TransitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


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

    @GetMapping("/detail")
    public ResponseEntity<ApiResponse<JsonNode>> getPathDetail(
            @RequestParam(name = "fromId") Long fromId,
            @RequestParam(name = "toId") Long toId,
            @RequestParam(name = "hour", defaultValue = "9") int hour) {

        return transitService.getPathDetail(fromId, toId, hour)
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)))
                .orElse(ResponseEntity.ok(ApiResponse.ok(null)));
    }
}
