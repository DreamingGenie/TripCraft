package com.tripcraft.plan.mapper;

import com.tripcraft.plan.domain.TransitCache;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface TransitCacheMapper {

    Optional<TransitCache> findByKey(@Param("fromAttractionId") Long fromAttractionId,
                                     @Param("toAttractionId") Long toAttractionId,
                                     @Param("departureHour") Integer departureHour,
                                     @Param("requestMode") String requestMode);

    void insert(TransitCache cache);

    void updateSummary(TransitCache cache);
}
