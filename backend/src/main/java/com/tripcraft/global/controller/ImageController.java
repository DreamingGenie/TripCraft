package com.tripcraft.global.controller;

import com.tripcraft.global.response.ApiResponse;
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

/**
 * 게시글 본문 이미지 업로드 API
 *
 * <ul>
 *   <li>허용 타입: JPEG · PNG · GIF · WebP</li>
 *   <li>최대 크기: 5 MB</li>
 *   <li>저장 위치: {upload.dir}/images/{uuid}.{ext}</li>
 *   <li>응답 URL : /uploads/images/{uuid}.{ext}  (WebMvcConfig에서 정적 서빙)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB

    @Value("${upload.dir:./uploads}")
    private String uploadDir;

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

        // 절대 경로로 확정 — 상대 경로를 그대로 쓰면 transferTo()가
        // Tomcat 임시 디렉토리 기준으로 해석해 FileNotFoundException 발생
        Path dir = Paths.get(uploadDir, "images").toAbsolutePath();
        Files.createDirectories(dir);

        // 확장자 추출 — 원본 파일명이 없으면 .jpg 사용
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.')).toLowerCase()
                : ".jpg";
        String filename = UUID.randomUUID() + ext;

        file.transferTo(dir.resolve(filename).toFile());
        log.debug("이미지 업로드 완료 — memberId={}, filename={}", memberId, filename);

        return ResponseEntity.ok(ApiResponse.ok("/uploads/images/" + filename));
    }
}
