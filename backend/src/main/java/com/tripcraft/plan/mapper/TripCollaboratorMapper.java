package com.tripcraft.plan.mapper;

import com.tripcraft.plan.domain.TripCollaborator;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TripCollaboratorMapper {

    void insert(TripCollaborator collaborator);

    void delete(@Param("tripId") Long tripId, @Param("memberId") Long memberId);

    List<TripCollaborator> findByTripId(Long tripId);

    Optional<TripCollaborator> findByTripAndMember(
            @Param("tripId") Long tripId,
            @Param("memberId") Long memberId);
}
