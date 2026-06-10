package com.tripcraft.plan.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class LanePolyline {
    private Long id;
    private String mapObjectKey;
    private String routeCoords;   // JSON [[lng,lat],...], null = API 결과 없음
    private LocalDateTime createdAt;
}
