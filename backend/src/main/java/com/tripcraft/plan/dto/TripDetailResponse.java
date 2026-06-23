package com.tripcraft.plan.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class TripDetailResponse {

    private Long id;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer memberCount;
    private String defaultTransitMode;
    private String ownerNickname;
    private String myRole;   // OWNER | EDITOR | VIEWER
    private List<CandidateItem> candidates;
}
