package com.tripcraft.member.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 방문 지도: 시도별 상태 한 칸. status=VISITED(후기·사진)|PLANNED(계획만). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegionMapItem {

    private Integer regionCode;
    private String status;        // VISITED | PLANNED
    private Long storyCount;       // 그 지역 내 여행이야기 수
    private String imageUrl;       // 표지 사진(선택 사본 우선, 없으면 최신 후기). PLANNED면 null
    private Long sourcePostId;     // 출처 글(글로 이동 링크). 없으면 null
    private Boolean pinned;        // 사용자가 직접 지정한 표지(crop 조정·해제 가능) 여부
    private Double focusX;         // crop 초점 X(%) — object-position
    private Double focusY;         // crop 초점 Y(%)
    private Double zoom;           // crop 확대 배율
}
