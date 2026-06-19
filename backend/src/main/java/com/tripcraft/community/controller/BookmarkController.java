package com.tripcraft.community.controller;

import com.tripcraft.community.dto.PostListPageResponse;
import com.tripcraft.community.service.BookmarkService;
import com.tripcraft.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    // 북마크 토글 (추가/취소)
    @PostMapping("/api/posts/{postId}/bookmark")
    public ResponseEntity<ApiResponse<Void>> toggleBookmark(
            @PathVariable Long postId,
            @AuthenticationPrincipal Long memberId) {
        bookmarkService.toggleBookmark(postId, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // 내 북마크 목록
    @GetMapping("/api/bookmarks/me")
    public ResponseEntity<ApiResponse<PostListPageResponse>> getMyBookmarks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(bookmarkService.getMyBookmarks(memberId, page, size)));
    }
}
