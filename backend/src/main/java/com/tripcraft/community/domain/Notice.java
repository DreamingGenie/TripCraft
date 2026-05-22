package com.tripcraft.community.domain;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Notice {

    private Long id;
    private Long memberId;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
