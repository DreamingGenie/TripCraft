package com.tripcraft.plan.mapper;

import com.tripcraft.plan.domain.Trip;
import com.tripcraft.plan.dto.TripBlockSummaryResponse.BlockRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TripMapper {

    Optional<Trip> findById(Long id);

    List<Trip> findByMemberId(Long memberId);

    boolean existsPostByTripId(Long tripId);

    List<BlockRow> findBlocksSummary(Long tripId);

    void insert(Trip trip);

    void update(Trip trip);

    void deleteById(Long id);

    void deleteByMemberId(Long memberId);

    void updateDefaultTransitMode(@Param("id") Long id, @Param("mode") String mode);

    List<Integer> findVisitedSidoCodes(@Param("memberId") Long memberId);
}
