package com.tripcraft.community.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostDetail {

    private Long id;
    private Long memberId;
    private String title;
    private String content;
    private String authorNickname;
    private Integer viewCount;
    private Integer likeCount;
    private LocalDateTime createdAt;
    private boolean liked;
    private boolean bookmarked;
    private boolean mine;
    private String authorProfileImageUrl;

    // 연결된 일정 요약 (trip_id가 없으면 null)
    private Long tripId;
    private String tripTitle;
    private LocalDate tripStartDate;
    private LocalDate tripEndDate;
    private Integer tripMemberCount;
}
