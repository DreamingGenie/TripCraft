package com.tripcraft.plan.service;

import com.tripcraft.plan.dto.BlockCreateRequest;
import com.tripcraft.plan.dto.BlockUpdateRequest;
import com.tripcraft.plan.dto.CollaboratorItem;
import com.tripcraft.plan.dto.TripBlockSummaryResponse;
import com.tripcraft.plan.dto.TripCopyRequest;
import com.tripcraft.plan.dto.TripCreateRequest;
import com.tripcraft.plan.dto.TripDetailResponse;
import com.tripcraft.plan.dto.TripSummary;

import java.util.List;

public interface TripService {

    List<TripSummary> getMyTrips(Long memberId);

    List<TripSummary> getCollaboratingTrips(Long memberId);

    List<CollaboratorItem> getCollaborators(Long tripId, Long requesterId);

    void inviteCollaborator(Long tripId, Long targetMemberId, String role, Long requesterId);

    void removeCollaborator(Long tripId, Long targetMemberId, Long requesterId);

    TripDetailResponse getTripDetail(Long tripId, Long memberId);

    /** 링크 접근 레벨 설정(소유자). 공개 전환 시 토큰 생성. @return 현재 토큰(없으면 null) */
    String setShareAccess(Long tripId, String access, Long requesterId);

    /** 공유 토큰으로 일정 조회(비로그인 허용). PRIVATE/무효 토큰은 거부. */
    TripDetailResponse getSharedTrip(String token, Long memberId);

    TripBlockSummaryResponse getBlocksSummary(Long tripId);

    Long createTrip(TripCreateRequest request, Long memberId);

    void deleteTrip(Long tripId, Long memberId);

    Long addCandidate(Long tripId, Long attractionId, Long memberId);

    void removeCandidate(Long tripId, Long candidateId, Long memberId);

    Long placeBlock(Long tripId, BlockCreateRequest request, Long memberId);

    void updateBlock(Long tripId, Long blockId, BlockUpdateRequest request, Long memberId);

    void removeBlock(Long tripId, Long blockId, Long memberId);

    void updateDefaultTransitMode(Long tripId, String mode, Long memberId);

    /**
     * 공유된 일정을 복사해 내 새 일정으로 저장.
     * 날짜는 newStartDate 기준으로 원본 일정의 Day 간격을 그대로 유지해 재계산.
     * @return 새로 생성된 일정 ID
     */
    Long copyTrip(Long sourceTripId, TripCopyRequest request, Long memberId);
}
