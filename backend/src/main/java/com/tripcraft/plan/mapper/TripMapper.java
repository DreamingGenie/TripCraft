package com.tripcraft.plan.mapper;

import com.tripcraft.plan.domain.Trip;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TripMapper {

    Optional<Trip> findById(Long id);

    List<Trip> findByMemberId(Long memberId);

    boolean existsPostByTripId(Long tripId);

    void insert(Trip trip);

    void update(Trip trip);

    void deleteById(Long id);
}
