package com.tripcraft.plan.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@AllArgsConstructor
public class BlockItem {

    private Long id;
    private Long candidateId;
    private LocalDate tripDate;
    private Integer displayOrder;
    private LocalTime startTime;
    private Integer durationMinutes;
    private Integer transitDurationMinutes;
    private String transitMode;
    private Integer transitOptionIndex;
    private Integer version;
}
