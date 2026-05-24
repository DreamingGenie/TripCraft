package com.tripcraft.plan.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class TripSummary {

    private Long id;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer memberCount;
    private int candidateCount;
}
