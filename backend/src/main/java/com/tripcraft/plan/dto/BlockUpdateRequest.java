package com.tripcraft.plan.dto;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
public class BlockUpdateRequest {

    private LocalDate tripDate;
    private LocalTime startTime;
    private Integer durationMinutes;
    private Integer displayOrder;
    private String transitMode;
    private Integer transitDurationMinutes;
    private Integer taxiFare;
}
