package com.tripcraft.global.controller;

import com.tripcraft.community.mapper.AttachMapper;
import com.tripcraft.community.domain.Attach;
import com.tripcraft.global.response.ApiResponse;
import com.tripcraft.global.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final FileStorageService fileStorageService;
    private final AttachMapper attachMapper;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Long memberId) {

        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        String filename = fileStorageService.storeImage(file);

        // 업로드 직후에는 어떤 게시글에도 연결되지 않은 임시 상태로 기록
        Attach attach = new Attach();
        attach.setTarget("post_draft");
        attach.setTargetId(null);
        attach.setName(filename);
        attachMapper.insert(attach);

        return ResponseEntity.ok(ApiResponse.ok(fileStorageService.toUrl(filename)));
    }
}
