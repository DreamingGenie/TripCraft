package com.tripcraft.plan.mapper;

import com.tripcraft.plan.domain.LanePolyline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface LanePolylineMapper {
    Optional<LanePolyline> findByKey(@Param("mapObjectKey") String mapObjectKey);
    void insert(LanePolyline lanePolyline);
}
