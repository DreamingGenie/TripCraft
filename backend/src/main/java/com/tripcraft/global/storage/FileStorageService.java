package com.tripcraft.global.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
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
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 파일(JPEG·PNG·GIF·WebP)만 업로드할 수 있습니다.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 크기는 5 MB 이하여야 합니다.");
        }

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
}
