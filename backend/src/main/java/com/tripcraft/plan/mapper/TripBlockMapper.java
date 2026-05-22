package com.tripcraft.plan.mapper;

import com.tripcraft.plan.domain.TripBlock;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TripBlockMapper {

    Optional<TripBlock> findById(Long id);

    List<TripBlock> findByCandidateId(Long candidateId);

    List<TripBlock> findByTripId(Long tripId);

    void insert(TripBlock block);

    void update(TripBlock block);

    void deleteById(Long id);

    void deleteByCandidateId(Long candidateId);

    void deleteByTripId(Long tripId);
}
