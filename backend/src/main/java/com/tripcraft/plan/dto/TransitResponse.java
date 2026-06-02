package com.tripcraft.plan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TransitResponse {
    private Integer durationMinutes;
    private String transportMode;
    private Integer transferCount;
    private Integer fare;
    private Integer totalWalkM;
    private Integer totalDistanceM;
    private Integer taxiFare;
    private Integer tollFare;
    private String routeCoords;
    private String roadSummary;
    private String routeSegmentsJson;
    private String label;
}
