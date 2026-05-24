package com.tripcraft.community.controller;

import com.tripcraft.community.dto.NoticeItem;
import com.tripcraft.community.mapper.NoticeMapper;
import com.tripcraft.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeMapper noticeMapper;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NoticeItem>>> getNotices() {
        List<NoticeItem> items = noticeMapper.findLatest(5).stream()
            .map(n -> new NoticeItem(n.getId(), n.getTitle(), n.getCreatedAt()))
            .toList();
        return ResponseEntity.ok(ApiResponse.ok(items));
    }
}
