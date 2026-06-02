package com.tripcraft.plan.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class Trip {

    private Long id;
    private Long memberId;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer memberCount;
    private Boolean isPublic;
    private String defaultTransitMode = "PUBLIC_TRANSIT";
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
