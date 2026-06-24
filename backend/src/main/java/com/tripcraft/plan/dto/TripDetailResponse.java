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
    private String shareAccess;  // PRIVATE | VIEW | EDIT (소유자에게만 의미)
    private String shareToken;   // 공유 링크 토큰(소유자/링크용)
}
