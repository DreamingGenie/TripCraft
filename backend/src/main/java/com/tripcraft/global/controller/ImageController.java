package com.tripcraft.global.controller;

import com.tripcraft.global.attach.domain.Attach;
import com.tripcraft.global.attach.mapper.AttachMapper;
import com.tripcraft.global.response.ApiResponse;
import com.tripcraft.global.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private static final String SUB_DIR = "images";

    private final AttachMapper attachMapper;
    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Long memberId) throws IOException {

        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        String filename = fileStorageService.save(file, SUB_DIR);

        Attach attach = new Attach();
        attach.setName(filename);
        attach.setHostName(file.getOriginalFilename() != null ? file.getOriginalFilename() : filename);
        attach.setSize(file.getSize());
        attach.setMimetype(file.getContentType());
        attach.setHostPath(fileStorageService.toHostPath(SUB_DIR, filename));
        attach.setTarget("post_draft");
        attach.setTargetId(memberId);
        attachMapper.insert(attach);

        log.debug("이미지 업로드 완료 — memberId={}, filename={}, attachId={}", memberId, filename, attach.getId());

        return ResponseEntity.ok(ApiResponse.ok(fileStorageService.toUrl(SUB_DIR, filename)));
    }
}
