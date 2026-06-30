package com.tripcraft.community.controller;

import com.tripcraft.community.dto.NoticeItem;
import com.tripcraft.community.mapper.NoticeMapper;
import com.tripcraft.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "공지사항", description = "공지 조회 (작성·수정·삭제는 ADMIN 전용)")
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeMapper noticeMapper;

    @Operation(summary = "최신 공지 조회", description = "사이드바용 최신 5건")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NoticeItem>>> getNotices() {
        List<NoticeItem> items = noticeMapper.findLatest(5).stream()
            .map(n -> new NoticeItem(n.getId(), n.getTitle(), n.getCreatedAt()))
            .toList();
        return ResponseEntity.ok(ApiResponse.ok(items));
    }
}
