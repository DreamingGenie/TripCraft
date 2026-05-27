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
    private Integer transportType;
    private Integer durationMinutes;
    private String transportMode;
    private LocalDateTime cachedAt;
}
