package com.tripcraft.community.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CommentItem {

    private Long id;
    private Long postId;
    private Long parentId;           // null = 최상위 댓글
    private String authorNickname;
    private String authorProfileImageUrl;
    private String content;
    private LocalDateTime createdAt;
    private boolean mine;

    /** 대댓글 목록 — Mapper 반환 시에는 비어 있으며, Service에서 트리 구성 후 채워짐 */
    private List<CommentItem> replies = new ArrayList<>();
}
