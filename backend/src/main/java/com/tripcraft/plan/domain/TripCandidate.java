package com.tripcraft.plan.domain;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class TripCandidate {

    private Long id;
    private Long tripId;
    private Long attractionId;   // 커스텀 장소면 NULL
    private Integer cityCode;
    private String source;       // MANUAL | FAVORITE | CUSTOM
    private LocalDateTime addedAt;

    // 커스텀 장소(attractionId == null)일 때 사용
    private String placeName;
    private String placeCategory;
    private String placeAddress;
    private BigDecimal placeLat;
    private BigDecimal placeLng;
}
