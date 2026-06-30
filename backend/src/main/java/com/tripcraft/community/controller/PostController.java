package com.tripcraft.community.controller;

import com.tripcraft.community.dto.PostCreateRequest;
import com.tripcraft.community.dto.PostDetail;
import com.tripcraft.community.dto.PostListPageResponse;
import com.tripcraft.community.dto.PostUpdateRequest;
import com.tripcraft.community.service.PostService;
import com.tripcraft.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "게시글", description = "여행 일정 공유 게시글 CRUD·좋아요")
@Validated
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "게시글 목록", description = "정렬(latest/popular)·검색·페이지네이션")
    @GetMapping
    public ResponseEntity<ApiResponse<PostListPageResponse>> getPosts(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @Pattern(regexp = "^(latest|popular)$", message = "sort는 latest 또는 popular만 허용됩니다.")
            @RequestParam(name = "sort", defaultValue = "latest") String sort,
            @RequestParam(name = "keyword", required = false) String keyword,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getPosts(page, size, sort, keyword, memberId)));
    }

    @Operation(summary = "내 게시글 목록")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PostListPageResponse>> getMyPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getMyPosts(page, size, memberId)));
    }

    @Operation(summary = "게시글 작성", description = "여행 일정 공유")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createPost(
            @RequestBody PostCreateRequest request,
            @AuthenticationPrincipal Long memberId) {
        Long id = postService.createPost(request, memberId);
        return ResponseEntity.status(201).body(ApiResponse.ok(id));
    }

    @Operation(summary = "게시글 상세", description = "공유 일정 뷰어 포함")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PostDetail>> getPost(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getPost(id, memberId)));
    }

    @Operation(summary = "게시글 수정")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updatePost(
            @PathVariable("id") Long id,
            @Valid @RequestBody PostUpdateRequest request,
            @AuthenticationPrincipal Long memberId) {
        postService.updatePost(id, request, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "게시글 삭제", description = "소프트 딜리트")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Long memberId) {
        postService.deletePost(id, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "좋아요 토글")
    @PostMapping("/{id}/likes")
    public ResponseEntity<ApiResponse<Void>> toggleLike(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Long memberId) {
        postService.toggleLike(id, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
