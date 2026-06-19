package com.tripcraft.global.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    /**
     * 이미지 파일을 검증하고 저장한 뒤 파일명을 반환한다.
     *
     * @return 저장된 파일명 (UUID.ext) — 호출자가 /uploads/images/{name} 으로 URL 조합
     */
    public String storeImage(MultipartFile file) {
        validateType(file);
        validateSize(file);
        validateMagicBytes(file);

        String ext = resolveExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID() + ext;

        Path dir = Paths.get(uploadDir, "images").toAbsolutePath();
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename).toFile());
        } catch (IOException e) {
            log.error("파일 저장 실패 — filename={}", filename, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장 중 오류가 발생했습니다.");
        }

        log.debug("이미지 저장 완료 — filename={}", filename);
        return filename;
    }

    /**
     * 파일명으로 이미지를 삭제한다. 파일이 없으면 조용히 무시한다.
     */
    public void deleteImage(String filename) {
        if (filename == null || filename.isBlank()) return;
        Path file = Paths.get(uploadDir, "images", filename).toAbsolutePath();
        try {
            Files.deleteIfExists(file);
            log.debug("이미지 삭제 완료 — filename={}", filename);
        } catch (IOException e) {
            log.warn("이미지 삭제 실패 — filename={}", filename, e);
        }
    }

    public String toUrl(String filename) {
        return "/uploads/images/" + filename;
    }

    // ── private ──────────────────────────────────────────────────────────────

    private void validateType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "이미지 파일(JPEG·PNG·GIF·WebP)만 업로드할 수 있습니다.");
        }
    }

    private void validateSize(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "파일 크기는 5 MB 이하여야 합니다.");
        }
    }

    /**
     * 파일 앞 12바이트(magic bytes)로 실제 포맷을 검증한다.
     * Content-Type 헤더는 클라이언트가 임의로 변경할 수 있으므로
     * 서버에서 바이너리 시그니처를 직접 확인해야 한다.
     *
     * <pre>
     * JPEG  : FF D8 FF
     * PNG   : 89 50 4E 47 0D 0A 1A 0A
     * GIF   : 47 49 46 38 (GIF8)
     * WebP  : 52 49 46 46 ?? ?? ?? ?? 57 45 42 50  (RIFF....WEBP)
     * </pre>
     */
    private void validateMagicBytes(MultipartFile file) {
        byte[] header = new byte[12];
        try (InputStream is = file.getInputStream()) {
            int read = is.read(header);
            if (read < 4) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 이미지 파일이 아닙니다.");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일을 읽을 수 없습니다.");
        }

        if (!isJpeg(header) && !isPng(header) && !isGif(header) && !isWebp(header)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 이미지 파일이 아닙니다.");
        }
    }

    private boolean isJpeg(byte[] h) {
        return (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] h) {
        return (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
    }

    private boolean isGif(byte[] h) {
        return h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8';
    }

    private boolean isWebp(byte[] h) {
        // RIFF????WEBP
        return h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
        }
        return ".jpg";
    }
}
