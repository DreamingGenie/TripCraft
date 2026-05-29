package com.tripcraft.attraction.service;

import com.tripcraft.attraction.dto.AttractionPageResponse;
import com.tripcraft.attraction.dto.RegionWithSigunguDto;

import java.util.List;

public interface AttractionService {

    AttractionPageResponse search(String keyword, String region, String sigungu,
                                   String category, int page, int size, Long memberId);

    List<RegionWithSigunguDto> getRegionsWithSigungu();

    String getSigunguName(Integer sidoCode, Integer sigunguCode);
}
