package com.tripcraft.plan.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class TransitCache {

    private Long id;
    private Long fromAttractionId;
    private Long toAttractionId;
    private Integer departureHour;
    private Integer durationMinutes;
    private String transportMode;
    private Integer transferCount;
    private Integer fare;
    private Integer totalDistanceM;
    private LocalDateTime cachedAt;
}
