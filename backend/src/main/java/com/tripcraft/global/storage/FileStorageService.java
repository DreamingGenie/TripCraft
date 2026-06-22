package com.tripcraft.global.storage;

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

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    public String save(MultipartFile file, String subDir) throws IOException {
        validateContentType(file);
        validateSize(file);
        validateMagicBytes(file);

        Path dir = Paths.get(uploadDir, subDir).toAbsolutePath();
        Files.createDirectories(dir);

        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.')).toLowerCase()
                : ".jpg";
        String filename = UUID.randomUUID() + ext;
        file.transferTo(dir.resolve(filename).toFile());
        return filename;
    }

    /**
     * 원격에서 받은 이미지 바이트를 저장 (예: 카카오 프로필 이미지).
     * Content-Type·크기·magic bytes를 동일하게 검증한다.
     */
    public String saveBytes(byte[] bytes, String contentType, String subDir) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "빈 이미지입니다.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 크기는 5 MB 이하여야 합니다.");
        }
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 파일(JPEG·PNG·GIF·WebP)만 저장할 수 있습니다.");
        }
        byte[] header = new byte[12];
        System.arraycopy(bytes, 0, header, 0, Math.min(12, bytes.length));
        if (!isJpeg(header) && !isPng(header) && !isGif(header) && !isWebp(header)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 이미지 파일이 아닙니다.");
        }

        Path dir = Paths.get(uploadDir, subDir).toAbsolutePath();
        Files.createDirectories(dir);
        String filename = UUID.randomUUID() + extFor(contentType);
        Files.write(dir.resolve(filename), bytes);
        return filename;
    }

    private String extFor(String contentType) {
        return switch (contentType) {
            case "image/png"  -> ".png";
            case "image/gif"  -> ".gif";
            case "image/webp" -> ".webp";
            default            -> ".jpg";
        };
    }

    public String toHostPath(String subDir, String filename) {
        return Paths.get(uploadDir, subDir, filename).toAbsolutePath().toString();
    }

    public String toUrl(String subDir, String filename) {
        return "/uploads/" + subDir + "/" + filename;
    }

    public void delete(String hostPath) {
        try {
            Files.deleteIfExists(Paths.get(hostPath));
        } catch (IOException ignored) {}
    }

    // ── private ──────────────────────────────────────────────────────────────

    private void validateContentType(MultipartFile file) {
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_TYPES.contains(ct)) {
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
     * 파일 앞 12바이트(magic bytes)로 실제 포맷 검증.
     * Content-Type 헤더는 클라이언트가 임의 변경 가능하므로 바이너리 시그니처를 직접 확인한다.
     *
     * <pre>
     * JPEG  : FF D8 FF
     * PNG   : 89 50 4E 47
     * GIF   : 47 49 46 38  (GIF8)
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
        return h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    }
}
