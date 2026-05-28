package com.tripcraft.plan.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransitResponse {
    private Integer durationMinutes;
    private String transportMode;
    private Integer transferCount;
    private Integer fare;
    private Integer totalWalkM;
}
