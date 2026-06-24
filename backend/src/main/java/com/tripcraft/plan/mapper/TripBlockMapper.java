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

    /** 해당 일정·날짜의 다음 display_order(= MAX+1). 서버 권위적으로 순서를 할당해 클라이언트 충돌 방지. */
    int nextDisplayOrder(@Param("tripId") Long tripId, @Param("date") LocalDate date);

    void insert(TripBlock block);

    void update(TripBlock block);

    /**
     * 낙관적 락 기반 사용자 편집 UPDATE. WHERE id=? AND version=? 로 갱신하고 version+1.
     * @return 영향받은 행 수(0이면 그 사이 다른 사용자가 먼저 수정 → 충돌)
     */
    int updateWithVersion(TripBlock block);

    /** transit 컬럼만 갱신(version·위치 컬럼 미변경). 재계산이 사용자 편집을 클로버하지 않게. */
    void updateTransitById(@Param("id") Long id,
                           @Param("transitDurationMinutes") Integer transitDurationMinutes,
                           @Param("transitMode") String transitMode,
                           @Param("transitOptionIndex") Integer transitOptionIndex);

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
