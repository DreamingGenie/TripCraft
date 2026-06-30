package com.tripcraft.plan.controller;

import com.tripcraft.global.response.ApiResponse;
import com.tripcraft.plan.dto.BlockCreateRequest;
import com.tripcraft.plan.dto.BlockUpdateRequest;
import com.tripcraft.plan.dto.CandidateAddRequest;
import com.tripcraft.plan.dto.CollaboratorItem;
import com.tripcraft.plan.dto.TripBlockSummaryResponse;
import com.tripcraft.plan.dto.TripCopyRequest;
import com.tripcraft.plan.dto.TripCreateRequest;
import com.tripcraft.plan.dto.TripDetailResponse;
import com.tripcraft.plan.dto.TripSummary;
import com.tripcraft.plan.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
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

@Tag(name = "여행 일정", description = "일정·협업자·후보군·타임라인 블록·공유")
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @Operation(summary = "내 일정 목록")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TripSummary>>> getMyTrips(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.getMyTrips(memberId)));
    }

    @Operation(summary = "내가 협업자인 일정 목록")
    @GetMapping("/collaborating")
    public ResponseEntity<ApiResponse<List<TripSummary>>> getCollaboratingTrips(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.getCollaboratingTrips(memberId)));
    }

    @Operation(summary = "협업자 목록")
    @GetMapping("/{id}/collaborators")
    public ResponseEntity<ApiResponse<List<CollaboratorItem>>> getCollaborators(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.getCollaborators(id, memberId)));
    }

    @Operation(summary = "협업자 초대", description = "role=EDITOR|VIEWER")
    @PostMapping("/{id}/collaborators")
    public ResponseEntity<ApiResponse<Void>> inviteCollaborator(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Long memberId) {
        Long targetMemberId = Long.valueOf(body.get("memberId").toString());
        String role = body.getOrDefault("role", "EDITOR").toString();
        tripService.inviteCollaborator(id, targetMemberId, role, memberId);
        return ResponseEntity.status(201).body(ApiResponse.ok());
    }

    @Operation(summary = "협업자 제거")
    @DeleteMapping("/{id}/collaborators/{targetMemberId}")
    public ResponseEntity<ApiResponse<Void>> removeCollaborator(
            @PathVariable("id") Long id,
            @PathVariable("targetMemberId") Long targetMemberId,
            @AuthenticationPrincipal Long memberId) {
        tripService.removeCollaborator(id, targetMemberId, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "일정 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createTrip(
            @RequestBody TripCreateRequest request,
            @AuthenticationPrincipal Long memberId) {
        Long id = tripService.createTrip(request, memberId);
        return ResponseEntity.status(201).body(ApiResponse.ok(id));
    }

    @Operation(summary = "일정 상세 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TripDetailResponse>> getTrip(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.getTripDetail(id, memberId)));
    }

    // 링크 접근 레벨 설정 (소유자) → {access, token}
    @Operation(summary = "공유 링크 접근레벨 설정", description = "→ {access, token}")
    @PutMapping("/{id}/share")
    public ResponseEntity<ApiResponse<Map<String, String>>> setShare(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Long memberId) {
        String access = body.getOrDefault("access", "PRIVATE");
        String token = tripService.setShareAccess(id, access, memberId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("access", access, "token", token == null ? "" : token)));
    }

    // 공유 토큰으로 일정 조회 (비로그인 허용 — SecurityConfig permitAll)
    @Operation(summary = "공유 토큰으로 일정 조회", description = "비로그인 허용")
    @GetMapping("/shared/{token}")
    public ResponseEntity<ApiResponse<TripDetailResponse>> getShared(
            @PathVariable("token") String token,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.getSharedTrip(token, memberId)));
    }

    @Operation(summary = "일정 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTrip(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Long memberId) {
        tripService.deleteTrip(id, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "관광지 후보 추가")
    @PostMapping("/{id}/candidates")
    public ResponseEntity<ApiResponse<Long>> addCandidate(
            @PathVariable("id") Long id,
            @RequestBody CandidateAddRequest request,
            @AuthenticationPrincipal Long memberId) {
        Long candidateId = tripService.addCandidate(id, request.getAttractionId(), memberId);
        return ResponseEntity.status(201).body(ApiResponse.ok(candidateId));
    }

    // 커스텀 장소 직접 추가
    @Operation(summary = "커스텀 장소 후보 추가")
    @PostMapping("/{id}/candidates/custom")
    public ResponseEntity<ApiResponse<Long>> addCustomCandidate(
            @PathVariable("id") Long id,
            @RequestBody com.tripcraft.plan.dto.CustomCandidateRequest request,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.status(201).body(ApiResponse.ok(tripService.addCustomCandidate(id, request, memberId)));
    }

    // 내 장소에서 보관함으로 추가
    @Operation(summary = "내 장소에서 후보 추가")
    @PostMapping("/{id}/candidates/from-place/{placeId}")
    public ResponseEntity<ApiResponse<Long>> addCandidateFromMyPlace(
            @PathVariable("id") Long id,
            @PathVariable("placeId") Long placeId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.status(201).body(ApiResponse.ok(tripService.addCandidateFromMyPlace(id, placeId, memberId)));
    }

    @Operation(summary = "후보 삭제", description = "연결된 블록이 있으면 RESTRICT")
    @DeleteMapping("/{id}/candidates/{candidateId}")
    public ResponseEntity<ApiResponse<Void>> removeCandidate(
            @PathVariable("id") Long id,
            @PathVariable("candidateId") Long candidateId,
            @AuthenticationPrincipal Long memberId) {
        tripService.removeCandidate(id, candidateId, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "블록 배치", description = "이동시간 자동 계산")
    @PostMapping("/{id}/blocks")
    public ResponseEntity<ApiResponse<Long>> placeBlock(
            @PathVariable("id") Long id,
            @RequestBody BlockCreateRequest request,
            @AuthenticationPrincipal Long memberId) {
        Long blockId = tripService.placeBlock(id, request, memberId);
        return ResponseEntity.status(201).body(ApiResponse.ok(blockId));
    }

    @Operation(summary = "블록 수정", description = "시간·순서·메모 (낙관적 락)")
    @PutMapping("/{id}/blocks/{blockId}")
    public ResponseEntity<ApiResponse<Void>> updateBlock(
            @PathVariable("id") Long id,
            @PathVariable("blockId") Long blockId,
            @RequestBody BlockUpdateRequest request,
            @AuthenticationPrincipal Long memberId) {
        tripService.updateBlock(id, blockId, request, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "블록 삭제")
    @DeleteMapping("/{id}/blocks/{blockId}")
    public ResponseEntity<ApiResponse<Void>> removeBlock(
            @PathVariable("id") Long id,
            @PathVariable("blockId") Long blockId,
            @AuthenticationPrincipal Long memberId) {
        tripService.removeBlock(id, blockId, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "블록 요약 조회", description = "공유 미리보기용 (공개)")
    @GetMapping("/{id}/blocks-summary")
    public ResponseEntity<ApiResponse<TripBlockSummaryResponse>> getBlocksSummary(
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.getBlocksSummary(id)));
    }

    /** 공유된 일정 가져오기 — 시작일 기준으로 날짜 재계산 후 내 일정으로 복제 */
    @Operation(summary = "공유 일정 복제", description = "시작일 기준 날짜 재계산 후 내 일정으로 복제")
    @PostMapping("/{tripId}/copy")
    public ResponseEntity<ApiResponse<Long>> copyTrip(
            @PathVariable("tripId") Long tripId,
            @Valid @RequestBody TripCopyRequest request,
            @AuthenticationPrincipal Long memberId) {
        Long newTripId = tripService.copyTrip(tripId, request, memberId);
        return ResponseEntity.status(201).body(ApiResponse.ok(newTripId));
    }

    @Operation(summary = "기본 이동수단 변경")
    @PatchMapping("/{tripId}/default-transit-mode")
    public ResponseEntity<ApiResponse<Void>> updateDefaultTransitMode(
            @PathVariable("tripId") Long tripId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Long memberId) {
        String mode = body.get("mode");
        tripService.updateDefaultTransitMode(tripId, mode, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
