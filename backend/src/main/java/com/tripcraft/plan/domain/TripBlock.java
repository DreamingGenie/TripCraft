package com.tripcraft.plan.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
public class TripBlock {

    private Long id;
    private Long candidateId;
    private LocalDate tripDate;
    private Integer displayOrder;
    private LocalTime startTime;
    private Integer durationMinutes;
    private Integer transportPreference = 0;
    private String memo;
    private Integer transitDurationMinutes;
    private String transitMode;
    private Integer transitOptionIndex;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
