package com.tripcraft.community.domain;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Post {

    private Long id;
    private Long memberId;
    private Long tripId;
    private String title;
    private String content;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
