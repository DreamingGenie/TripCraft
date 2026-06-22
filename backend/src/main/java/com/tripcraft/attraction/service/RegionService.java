package com.tripcraft.attraction.service;

import com.tripcraft.attraction.dto.RegionWithSigunguDto;

import java.util.List;
import java.util.Map;

/**
 * 시도·시군구 표시명 조회. DB(sido/sigungu 테이블)에서 요청 시 조회한다.
 * 표시명은 alias(짧은 이름) 우선, 없으면 name(TourAPI 공식명).
 */
public interface RegionService {

    /** 시도코드 → 표시명 */
    Map<Integer, String> sidoLabelMap();

    /** 시도코드 → (시군구코드 → 표시명) */
    Map<Integer, Map<Integer, String>> sigunguLabelMap();

    /** 표시명·공식명 → 시도코드 (필터 파라미터 해석용) */
    Map<String, Integer> sidoCodeByLabel();

    /** 시도+시군구 전체 (필터 UI) */
    List<RegionWithSigunguDto> getRegionsWithSigungu();
}
