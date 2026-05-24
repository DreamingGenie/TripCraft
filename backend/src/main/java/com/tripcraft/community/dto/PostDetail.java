package com.tripcraft.community.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
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
    private boolean mine;
}
