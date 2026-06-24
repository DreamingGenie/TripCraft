package com.tripcraft.plan.mapper;

import com.tripcraft.plan.domain.TripCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Mapper
public interface TripCandidateMapper {

    Optional<TripCandidate> findById(Long id);

    List<TripCandidate> findByTripId(Long tripId);

    boolean existsBlockByCandidateId(Long candidateId);

    /** 같은 일정 보관함에 동일 좌표의 커스텀 장소가 이미 있는지(좌표 기반 중복 방지) */
    boolean existsCustomByCoords(@Param("tripId") Long tripId,
                                 @Param("lat") BigDecimal lat,
                                 @Param("lng") BigDecimal lng);

    void insert(TripCandidate candidate);

    void deleteById(Long id);

    void deleteByTripId(Long tripId);

    void deleteByMemberId(Long memberId);
}
