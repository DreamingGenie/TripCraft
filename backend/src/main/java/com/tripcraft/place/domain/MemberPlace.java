package com.tripcraft.place.domain;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class MemberPlace {
    private Long id;
    private Long memberId;
    private String name;
    private String category;      // 관광지·문화시설·레포츠·숙박·쇼핑·음식점
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalDateTime createdAt;
}
