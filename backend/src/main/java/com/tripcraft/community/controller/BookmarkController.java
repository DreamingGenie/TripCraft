package com.tripcraft.community.controller;

import com.tripcraft.community.dto.PostListPageResponse;
import com.tripcraft.community.service.BookmarkService;
import com.tripcraft.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "북마크", description = "게시글 북마크 토글·목록")
@RestController
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    // 북마크 토글 (추가/취소)
    @Operation(summary = "북마크 토글", description = "추가/취소 토글")
    @PostMapping("/api/posts/{postId}/bookmark")
    public ResponseEntity<ApiResponse<Void>> toggleBookmark(
            @PathVariable Long postId,
            @AuthenticationPrincipal Long memberId) {
        bookmarkService.toggleBookmark(postId, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // 내 북마크 목록
    @Operation(summary = "내 북마크 목록", description = "삭제된 게시글 표시 포함")
    @GetMapping("/api/bookmarks/me")
    public ResponseEntity<ApiResponse<PostListPageResponse>> getMyBookmarks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(bookmarkService.getMyBookmarks(memberId, page, size)));
    }
}
