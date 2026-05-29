package com.tripcraft.plan.mapper;

import com.tripcraft.plan.domain.TransitCache;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface TransitCacheMapper {

    Optional<TransitCache> findByKey(Long fromAttractionId, Long toAttractionId, Integer departureHour);

    void insert(TransitCache cache);

    void updateSummary(TransitCache cache);
}
