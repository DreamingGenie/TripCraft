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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final PostCommentMapper commentMapper;
    private final PostMapper postMapper;

    /**
     * 댓글 목록 조회 — Mapper가 반환한 flat list를 트리 구조로 변환해 반환.
     * 최상위 댓글 리스트에 replies(대댓글 목록)가 포함된다.
     * 부모가 삭제된 고아 대댓글은 최상위로 승격해 유실 방지.
     */
    @Override
    public List<CommentItem> getComments(Long postId, Long memberId) {
        Long queryMemberId = memberId != null ? memberId : 0L;
        List<CommentItem> flat = commentMapper.findByPostId(postId, queryMemberId);

        // id → CommentItem 맵 (삽입 순서 유지)
        Map<Long, CommentItem> map = new LinkedHashMap<>();
        for (CommentItem c : flat) {
            map.put(c.getId(), c);
        }

        // 트리 구성
        List<CommentItem> roots = new ArrayList<>();
        for (CommentItem c : flat) {
            if (c.getParentId() == null) {
                roots.add(c);
            } else {
                CommentItem parent = map.get(c.getParentId());
                if (parent != null) {
                    parent.getReplies().add(c);
                } else {
                    // 부모 댓글이 삭제된 경우 최상위로 승격
                    roots.add(c);
                }
            }
        }
        return roots;
    }

    @Override
    @Transactional
    public Long createComment(Long postId, CommentCreateRequest req, Long memberId) {
        validatePost(postId);

        // 대댓글인 경우 부모 댓글이 같은 게시글에 속하는지 검증
        if (req.getParentId() != null) {
            PostComment parent = commentMapper.findById(req.getParentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "부모 댓글을 찾을 수 없습니다."));
            if (!parent.getPostId().equals(postId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "부모 댓글이 해당 게시글에 속하지 않습니다.");
            }
            // 대댓글의 대댓글은 허용하지 않음 (1단계만 지원)
            if (parent.getParentId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "대댓글에는 답글을 달 수 없습니다.");
            }
        }

        PostComment comment = new PostComment();
        comment.setPostId(postId);
        comment.setMemberId(memberId);
        comment.setParentId(req.getParentId());
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
        // ON DELETE CASCADE로 대댓글도 함께 삭제됨
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
