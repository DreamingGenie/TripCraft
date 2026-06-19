package com.tripcraft.attraction.service;

import com.tripcraft.attraction.dto.AttractionDetailDto;
import com.tripcraft.attraction.dto.AttractionPageResponse;
import com.tripcraft.attraction.dto.NearbyAttraction;
import com.tripcraft.attraction.dto.RegionWithSigunguDto;

import java.math.BigDecimal;
import java.util.List;

public interface AttractionService {

    AttractionPageResponse search(String keyword, String region, String sigungu,
                                   String category, int page, int size, Long memberId);

    List<RegionWithSigunguDto> getRegionsWithSigungu();

    String getSigunguName(Integer sidoCode, Integer sigunguCode);

    AttractionDetailDto getById(Long id);

    /** 좌표 기준 반경 radiusKm 내 가까운 관광지 최대 limit개 (excludeId 제외). */
    List<NearbyAttraction> findNearby(BigDecimal lat, BigDecimal lng, Long excludeId, double radiusKm, int limit);
}
