package com.tripcraft.community.controller;

import com.tripcraft.community.dto.PostCreateRequest;
import com.tripcraft.community.dto.PostDetail;
import com.tripcraft.community.dto.PostListPageResponse;
import com.tripcraft.community.service.PostService;
import com.tripcraft.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public ResponseEntity<ApiResponse<PostListPageResponse>> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "latest") String sort,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getPosts(page, size, sort, memberId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createPost(
            @RequestBody PostCreateRequest request,
            @AuthenticationPrincipal Long memberId) {
        Long id = postService.createPost(request, memberId);
        return ResponseEntity.status(201).body(ApiResponse.ok(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PostDetail>> getPost(
            @PathVariable Long id,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getPost(id, memberId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable Long id,
            @AuthenticationPrincipal Long memberId) {
        postService.deletePost(id, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{id}/likes")
    public ResponseEntity<ApiResponse<Void>> toggleLike(
            @PathVariable Long id,
            @AuthenticationPrincipal Long memberId) {
        postService.toggleLike(id, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
