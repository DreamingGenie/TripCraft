package com.tripcraft.community.service;

import com.tripcraft.community.domain.PostComment;
import com.tripcraft.community.dto.CommentCreateRequest;
import com.tripcraft.community.dto.CommentItem;
import com.tripcraft.community.mapper.PostCommentMapper;
import com.tripcraft.community.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final PostCommentMapper commentMapper;
    private final PostMapper postMapper;

    @Override
    public List<CommentItem> getComments(Long postId, Long memberId) {
        // 존재하지 않는 postId면 빈 목록 반환으로 충분 — 별도 SELECT 불필요
        Long queryMemberId = memberId != null ? memberId : 0L;
        return commentMapper.findByPostId(postId, queryMemberId);
    }

    @Override
    @Transactional
    public Long createComment(Long postId, CommentCreateRequest req, Long memberId) {
        validatePost(postId);
        PostComment comment = new PostComment();
        comment.setPostId(postId);
        comment.setMemberId(memberId);
        comment.setContent(req.getContent());
        commentMapper.insert(comment);
        return comment.getId();
    }

    @Override
    @Transactional
    public void deleteComment(Long postId, Long commentId, Long memberId) {
        PostComment comment = commentMapper.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."));
        if (!comment.getPostId().equals(postId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "게시글과 댓글이 일치하지 않습니다.");
        }
        boolean isOwner = comment.getMemberId() != null && comment.getMemberId().equals(memberId);
        boolean isAdmin = currentUserIsAdmin();
        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "삭제 권한이 없습니다.");
        }
        commentMapper.deleteById(commentId);
    }

    // ───────── private ─────────

    private void validatePost(Long postId) {
        postMapper.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."));
    }

    private boolean currentUserIsAdmin() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
