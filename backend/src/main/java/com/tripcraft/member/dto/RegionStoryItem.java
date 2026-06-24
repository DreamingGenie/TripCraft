package com.tripcraft.member.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 방문 지도: 표지 후보 여행이야기(글) 한 건. imageCount로 단일/다중 분기. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegionStoryItem {

    private Long postId;
    private String title;
    private LocalDateTime createdAt;
    private String coverImageUrl;  // 목록 썸네일
    private Integer imageCount;     // 1이면 바로 반영, 2+면 사진 선택 화면
}
