package com.tripcraft.global.controller;

import com.tripcraft.global.attach.domain.Attach;
import com.tripcraft.global.attach.mapper.AttachMapper;
import com.tripcraft.global.response.ApiResponse;
import com.tripcraft.global.storage.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@Tag(name = "이미지", description = "게시글 이미지·대표사진 업로드(draft) 관리")
@Slf4j
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private static final String SUB_DIR = "images";

    private final AttachMapper attachMapper;
    private final FileStorageService fileStorageService;

    @Operation(summary = "이미지 업로드", description = "게시글 본문/대표사진(type=cover) draft 업로드")
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false) String type,
            @AuthenticationPrincipal Long memberId) throws IOException {

        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        boolean isCover = "cover".equals(type);

        // 대표사진은 회원당 1장만 유지 — 기존 draft 레코드·파일을 정리해 교체
        if (isCover) {
            deleteCoverDraft(memberId);
        }

        String filename = fileStorageService.save(file, SUB_DIR);

        // 업로드 직후에는 어떤 게시글에도 연결되지 않은 임시 상태로 기록
        // targetId에 memberId를 사용해 나중에 이 회원의 draft를 일괄 연결할 수 있게 함
        Attach attach = new Attach();
        attach.setName(filename);
        attach.setHostName(file.getOriginalFilename() != null ? file.getOriginalFilename() : filename);
        attach.setSize(file.getSize());
        attach.setMimetype(file.getContentType());
        attach.setHostPath(fileStorageService.toHostPath(SUB_DIR, filename));
        attach.setTarget(isCover ? "post_cover_draft" : "post_draft");
        attach.setTargetId(memberId);
        attachMapper.insert(attach);

        log.debug("이미지 업로드 완료 — memberId={}, type={}, filename={}, attachId={}", memberId, type, filename, attach.getId());

        return ResponseEntity.ok(ApiResponse.ok(fileStorageService.toUrl(SUB_DIR, filename)));
    }

    @Operation(summary = "임시 대표사진 정리", description = "확정되지 않은 cover draft 삭제")
    @DeleteMapping("/cover-draft")
    public ResponseEntity<ApiResponse<Void>> deleteCover(@AuthenticationPrincipal Long memberId) {
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        deleteCoverDraft(memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // member의 임시 대표사진(post_cover_draft) attach 레코드와 파일을 함께 정리
    private void deleteCoverDraft(Long memberId) {
        List<Attach> drafts = attachMapper.findByTarget("post_cover_draft", memberId);
        drafts.forEach(a -> {
            if (a.getHostPath() != null && !a.getHostPath().isBlank()) {
                fileStorageService.delete(a.getHostPath());
            }
        });
        attachMapper.deleteByTarget("post_cover_draft", memberId);
    }
}
