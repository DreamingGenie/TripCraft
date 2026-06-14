package com.tripcraft.community.controller;

import com.tripcraft.community.dto.PostListPageResponse;
import com.tripcraft.community.mapper.PostLikeMapper;
import com.tripcraft.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/likes")
@RequiredArgsConstructor
public class LikeController {

    private final PostLikeMapper postLikeMapper;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PostListPageResponse>> getMyLikes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Long memberId) {
        var items = postLikeMapper.findByMemberId(memberId, page * size, size);
        int total = postLikeMapper.countByMemberId(memberId);
        return ResponseEntity.ok(ApiResponse.ok(new PostListPageResponse(items, total, page, size)));
    }
}
