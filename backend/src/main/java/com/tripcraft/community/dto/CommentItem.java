package com.tripcraft.community.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CommentItem {

    private Long id;
    private Long postId;
    private String authorNickname;
    private String content;
    private LocalDateTime createdAt;
    private boolean mine;
}
