package com.tripcraft.community.controller;

import com.tripcraft.community.dto.CommentCreateRequest;
import com.tripcraft.community.dto.CommentItem;
import com.tripcraft.community.service.CommentService;
import com.tripcraft.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "댓글", description = "게시글 댓글·대댓글 조회·등록·삭제")
@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** 댓글 목록 조회 — 비로그인도 가능 */
    @Operation(summary = "댓글·대댓글 목록", description = "비로그인 조회 가능")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CommentItem>>> getComments(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.getComments(postId, memberId)));
    }

    /** 댓글 등록 — 로그인 필요 */
    @Operation(summary = "댓글/대댓글 등록", description = "parentId 지정 시 대댓글")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createComment(
            @PathVariable("postId") Long postId,
            @Valid @RequestBody CommentCreateRequest request,
            @AuthenticationPrincipal Long memberId) {
        Long id = commentService.createComment(postId, request, memberId);
        return ResponseEntity.status(201).body(ApiResponse.ok(id));
    }

    /** 댓글 삭제 — 본인 또는 ADMIN (role 판단은 Service 내부에서 SecurityContext로 처리) */
    @Operation(summary = "댓글 삭제", description = "본인 또는 ADMIN")
    @DeleteMapping("/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable("postId") Long postId,
            @PathVariable("commentId") Long commentId,
            @AuthenticationPrincipal Long memberId) {
        commentService.deleteComment(postId, commentId, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
