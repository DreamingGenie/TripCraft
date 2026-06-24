package com.tripcraft.plan.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 보관함에 직접 추가하는 커스텀 장소 */
@Getter
@Setter
public class CustomCandidateRequest {
    private String name;
    private String category;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private boolean saveToMyPlaces;   // true 면 내 장소로도 저장
}
