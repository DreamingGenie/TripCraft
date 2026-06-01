package com.tripcraft.community.service;

import com.tripcraft.community.dto.CommentCreateRequest;
import com.tripcraft.community.dto.CommentItem;

import java.util.List;

public interface CommentService {

    /** 게시글의 댓글 목록 반환 */
    List<CommentItem> getComments(Long postId, Long memberId);

    /** 댓글 등록, 생성된 댓글 ID 반환 */
    Long createComment(Long postId, CommentCreateRequest req, Long memberId);

    /** 댓글 삭제 (본인 또는 ADMIN만 가능 — role 판단은 SecurityContext에서 내부 처리) */
    void deleteComment(Long postId, Long commentId, Long memberId);
}
