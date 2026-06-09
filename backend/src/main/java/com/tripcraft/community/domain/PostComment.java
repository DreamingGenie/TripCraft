package com.tripcraft.community.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PostComment {

    private Long id;
    private Long postId;
    private Long memberId;
    private Long parentId;   // null = 최상위 댓글, non-null = 대댓글
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
