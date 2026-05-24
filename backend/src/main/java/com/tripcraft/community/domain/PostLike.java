package com.tripcraft.community.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PostLike {

    private Long id;
    private Long postId;
    private Long memberId;
    private LocalDateTime createdAt;
}
