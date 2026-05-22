package com.tripcraft.plan.domain;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class TripCandidate {

    private Long id;
    private Long tripId;
    private Long attractionId;
    private Integer sigunguCode;
    private String source;
    private LocalDateTime createdAt;
}
