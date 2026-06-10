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
    private String routeCoords;   // 파싱된 좌표 JSON [[lng,lat],...], null = API 결과 없음
    private String rawResponse;   // ODsay loadLane 원본 응답 JSON
    private LocalDateTime createdAt;
}
