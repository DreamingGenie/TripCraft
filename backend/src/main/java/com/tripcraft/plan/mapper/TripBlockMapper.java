package com.tripcraft.plan.mapper;

import com.tripcraft.plan.domain.TripBlock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface TripBlockMapper {

    Optional<TripBlock> findById(Long id);

    Optional<Long> findTripIdByBlockId(Long blockId);

    List<TripBlock> findByCandidateId(Long candidateId);

    List<TripBlock> findByTripId(Long tripId);

    List<TripBlock> findByTripIdAndDate(@Param("tripId") Long tripId, @Param("date") LocalDate date);

    void insert(TripBlock block);

    void update(TripBlock block);

    void deleteById(Long id);

    void deleteByCandidateId(Long candidateId);

    void deleteByTripId(Long tripId);

    void deleteByMemberId(Long memberId);

    void updateTransitByAttractionPair(
            @Param("fromAttractionId") Long fromAttractionId,
            @Param("toAttractionId") Long toAttractionId,
            @Param("durationMinutes") Integer durationMinutes,
            @Param("transportMode") String transportMode,
            @Param("optionIndex") Integer optionIndex);
}
