package com.tripcraft.plan.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class TripCandidate {

    private Long id;
    private Long tripId;
    private Long attractionId;
    private Integer cityCode;
    private String source;
    private LocalDateTime addedAt;
}
