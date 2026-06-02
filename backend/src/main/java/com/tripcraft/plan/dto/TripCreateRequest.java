package com.tripcraft.plan.dto;

import lombok.Getter;

import java.time.LocalDate;

@Getter
public class TripCreateRequest {

    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer memberCount;
    private String defaultTransitMode = "PUBLIC_TRANSIT";
}
