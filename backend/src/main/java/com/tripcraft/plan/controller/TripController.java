package com.tripcraft.plan.controller;

import com.tripcraft.global.response.ApiResponse;
import com.tripcraft.plan.dto.BlockCreateRequest;
import com.tripcraft.plan.dto.BlockUpdateRequest;
import com.tripcraft.plan.dto.CandidateAddRequest;
import com.tripcraft.plan.dto.TripCreateRequest;
import com.tripcraft.plan.dto.TripDetailResponse;
import com.tripcraft.plan.dto.TripSummary;
import com.tripcraft.plan.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TripSummary>>> getMyTrips(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.getMyTrips(memberId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createTrip(
            @RequestBody TripCreateRequest request,
            @AuthenticationPrincipal Long memberId) {
        Long id = tripService.createTrip(request, memberId);
        return ResponseEntity.status(201).body(ApiResponse.ok(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TripDetailResponse>> getTrip(
            @PathVariable Long id,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.getTripDetail(id, memberId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTrip(
            @PathVariable Long id,
            @AuthenticationPrincipal Long memberId) {
        tripService.deleteTrip(id, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{id}/candidates")
    public ResponseEntity<ApiResponse<Long>> addCandidate(
            @PathVariable Long id,
            @RequestBody CandidateAddRequest request,
            @AuthenticationPrincipal Long memberId) {
        Long candidateId = tripService.addCandidate(id, request.getAttractionId(), memberId);
        return ResponseEntity.status(201).body(ApiResponse.ok(candidateId));
    }

    @DeleteMapping("/{id}/candidates/{candidateId}")
    public ResponseEntity<ApiResponse<Void>> removeCandidate(
            @PathVariable Long id,
            @PathVariable Long candidateId,
            @AuthenticationPrincipal Long memberId) {
        tripService.removeCandidate(id, candidateId, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{id}/blocks")
    public ResponseEntity<ApiResponse<Long>> placeBlock(
            @PathVariable Long id,
            @RequestBody BlockCreateRequest request,
            @AuthenticationPrincipal Long memberId) {
        Long blockId = tripService.placeBlock(id, request, memberId);
        return ResponseEntity.status(201).body(ApiResponse.ok(blockId));
    }

    @PutMapping("/{id}/blocks/{blockId}")
    public ResponseEntity<ApiResponse<Void>> updateBlock(
            @PathVariable Long id,
            @PathVariable Long blockId,
            @RequestBody BlockUpdateRequest request,
            @AuthenticationPrincipal Long memberId) {
        tripService.updateBlock(id, blockId, request, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/{id}/blocks/{blockId}")
    public ResponseEntity<ApiResponse<Void>> removeBlock(
            @PathVariable Long id,
            @PathVariable Long blockId,
            @AuthenticationPrincipal Long memberId) {
        tripService.removeBlock(id, blockId, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PatchMapping("/{tripId}/default-transit-mode")
    public ResponseEntity<ApiResponse<Void>> updateDefaultTransitMode(
            @PathVariable Long tripId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Long memberId) {
        String mode = body.get("mode");
        tripService.updateDefaultTransitMode(tripId, mode, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
