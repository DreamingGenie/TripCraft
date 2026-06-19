package com.tripcraft.attraction.mapper;

import com.tripcraft.attraction.domain.Attraction;
import com.tripcraft.attraction.dto.NearbyAttraction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Mapper
public interface AttractionMapper {

    Optional<Attraction> findById(Long id);

    List<Attraction> findAll();

    List<Attraction> findByCondition(AttractionSearchCondition condition);

    int countByCondition(AttractionSearchCondition condition);

    List<GroupStatRow> findGroupStats(AttractionSearchCondition condition);

    void insertAll(List<Attraction> attractions);

    void update(Attraction attraction);

    List<Attraction> findWithoutDetailSync(@Param("limit") int limit);

    /** bounding box로 1차 필터 후 ST_Distance_Sphere로 가까운 순 정렬. */
    List<NearbyAttraction> findNearby(@Param("lat") BigDecimal lat, @Param("lng") BigDecimal lng,
                                      @Param("minLat") BigDecimal minLat, @Param("maxLat") BigDecimal maxLat,
                                      @Param("minLng") BigDecimal minLng, @Param("maxLng") BigDecimal maxLng,
                                      @Param("excludeId") Long excludeId, @Param("limit") int limit);
}
