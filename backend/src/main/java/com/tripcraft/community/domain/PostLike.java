package com.tripcraft.community.domain;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PostLike {

    private Long id;
    private Long postId;
    private Long memberId;
    private LocalDateTime createdAt;
}
