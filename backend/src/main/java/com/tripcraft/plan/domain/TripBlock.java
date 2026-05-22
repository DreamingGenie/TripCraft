package com.tripcraft.plan.domain;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
public class TripBlock {

    private Long id;
    private Long candidateId;
    private LocalDate tripDate;
    private Integer displayOrder;
    private LocalTime startTime;
    private Integer durationMinutes;
    private Integer transportPreference;
    private String memo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
