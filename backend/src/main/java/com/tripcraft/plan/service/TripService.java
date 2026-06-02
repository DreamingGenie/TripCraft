package com.tripcraft.plan.service;

import com.tripcraft.plan.dto.BlockCreateRequest;
import com.tripcraft.plan.dto.BlockUpdateRequest;
import com.tripcraft.plan.dto.TripBlockSummaryResponse;
import com.tripcraft.plan.dto.TripCreateRequest;
import com.tripcraft.plan.dto.TripDetailResponse;
import com.tripcraft.plan.dto.TripSummary;

import java.util.List;

public interface TripService {

    List<TripSummary> getMyTrips(Long memberId);

    TripDetailResponse getTripDetail(Long tripId, Long memberId);

    TripBlockSummaryResponse getBlocksSummary(Long tripId);

    Long createTrip(TripCreateRequest request, Long memberId);

    void deleteTrip(Long tripId, Long memberId);

    Long addCandidate(Long tripId, Long attractionId, Long memberId);

    void removeCandidate(Long tripId, Long candidateId, Long memberId);

    Long placeBlock(Long tripId, BlockCreateRequest request, Long memberId);

    void updateBlock(Long tripId, Long blockId, BlockUpdateRequest request, Long memberId);

    void removeBlock(Long tripId, Long blockId, Long memberId);

    void updateDefaultTransitMode(Long tripId, String mode, Long memberId);
}
