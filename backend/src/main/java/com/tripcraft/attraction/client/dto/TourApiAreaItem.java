package com.tripcraft.attraction.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

/**
 * TourAPI areaCode2 응답 아이템.
 * 시도 목록(areaCode 미지정) 또는 특정 시도의 시군구 목록(areaCode 지정) 공통.
 * 형식: { "rnum": 1, "code": "1", "name": "서울" }
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourApiAreaItem {

    private String code;
    private String name;
}
