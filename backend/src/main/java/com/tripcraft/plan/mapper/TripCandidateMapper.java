package com.tripcraft.plan.mapper;

import com.tripcraft.plan.domain.TripCandidate;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TripCandidateMapper {

    Optional<TripCandidate> findById(Long id);

    List<TripCandidate> findByTripId(Long tripId);

    boolean existsBlockByCandidateId(Long candidateId);

    void insert(TripCandidate candidate);

    void deleteById(Long id);

    void deleteByTripId(Long tripId);

    void deleteByMemberId(Long memberId);
}
