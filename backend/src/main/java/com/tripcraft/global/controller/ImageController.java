package com.tripcraft.global.controller;

import com.tripcraft.global.attach.domain.Attach;
import com.tripcraft.global.attach.mapper.AttachMapper;
import com.tripcraft.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    private final AttachMapper attachMapper;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Long memberId) throws IOException {

        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 파일(JPEG·PNG·GIF·WebP)만 업로드할 수 있습니다.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 크기는 5 MB 이하여야 합니다.");
        }

        Path dir = Paths.get(uploadDir, "images").toAbsolutePath();
        Files.createDirectories(dir);

        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.')).toLowerCase()
                : ".jpg";
        String filename = UUID.randomUUID() + ext;
        Path filePath = dir.resolve(filename);
        file.transferTo(filePath.toFile());

        // attach 레코드 생성 — 글 저장 완료 시 post_draft → post로 전환
        Attach attach = new Attach();
        attach.setName(filename);
        attach.setHostName(original != null ? original : filename);
        attach.setSize(file.getSize());
        attach.setMimetype(contentType);
        attach.setHostPath(filePath.toString());
        attach.setTarget("post_draft");
        attach.setTargetId(memberId);
        attachMapper.insert(attach);

        log.debug("이미지 업로드 완료 — memberId={}, filename={}, attachId={}", memberId, filename, attach.getId());

        return ResponseEntity.ok(ApiResponse.ok("/uploads/images/" + filename));
    }
}
