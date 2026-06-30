package com.tripcraft.community.controller;

import com.tripcraft.community.dto.PostListPageResponse;
import com.tripcraft.community.service.LikeService;
import com.tripcraft.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "좋아요", description = "내가 좋아요한 게시글 조회 (좋아요 토글은 게시글 API)")
@RestController
@RequestMapping("/api/likes")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @Operation(summary = "내가 좋아요한 글 목록")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PostListPageResponse>> getMyLikes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(likeService.getMyLikes(memberId, page, size)));
    }
}
