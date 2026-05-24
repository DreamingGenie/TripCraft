package com.tripcraft.plan.dto;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
public class BlockCreateRequest {

    private Long candidateId;
    private LocalDate tripDate;
    private LocalTime startTime;
    private Integer durationMinutes;
    private Integer displayOrder;
}
