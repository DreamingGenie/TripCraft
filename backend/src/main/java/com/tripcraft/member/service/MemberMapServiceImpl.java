package com.tripcraft.member.service;

import com.tripcraft.global.attach.domain.Attach;
import com.tripcraft.global.attach.mapper.AttachMapper;
import com.tripcraft.global.storage.FileStorageService;
import com.tripcraft.member.dto.CoverCropRequest;
import com.tripcraft.member.dto.CoverImageRequest;
import com.tripcraft.member.dto.RegionImageItem;
import com.tripcraft.member.dto.RegionMapItem;
import com.tripcraft.member.dto.RegionStoryItem;
import com.tripcraft.member.mapper.MemberMapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MemberMapServiceImpl implements MemberMapService {

    private static final String LEVEL_SIDO = "SIDO";
    private static final String SUB_DIR = "images";
    private static final String URL_PREFIX = "/uploads/images/";
    private static final double DEFAULT_FOCUS = 50.0;
    private static final double DEFAULT_ZOOM = 1.0;

    // 본문 <img src="/uploads/images/...">
    private static final Pattern IMG_SRC =
            Pattern.compile("<img[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    // 경로 traversal 차단: UUID.ext 형태만 허용
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final MemberMapMapper memberMapMapper;
    private final AttachMapper attachMapper;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(readOnly = true)
    public List<RegionMapItem> getMap(Long memberId) {
        return memberMapMapper.findRegionStatuses(memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegionStoryItem> getRegionStories(Long memberId, int sidoCode) {
        return memberMapMapper.findRegionStories(memberId, sidoCode);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegionImageItem> getPostImages(Long memberId, int sidoCode, long postId) {
        String content = memberMapMapper.findPostContent(memberId, sidoCode, postId);
        if (content == null) {
            return List.of();   // 내 글·해당 시도 아님
        }
        List<String> urls = new ArrayList<>();
        // 커버(attach) 먼저
        for (Attach a : attachMapper.findByTarget("post_cover", postId)) {
            addUnique(urls, fileStorageService.toUrl(SUB_DIR, a.getName()));
        }
        // 본문 이미지(content의 <img src>) — attach 전환 여부와 무관하게 진실
        for (String u : extractImageUrls(content)) {
            addUnique(urls, u);
        }
        return urls.stream().map(RegionImageItem::new).toList();
    }

    @Override
    @Transactional
    public void setCoverFromImage(Long memberId, CoverImageRequest request) {
        String level = level(request.getRegionLevel());
        String name = imageName(request.getImageUrl());
        Long sourcePostId = memberMapMapper.findPostIdByImage(memberId, request.getRegionCode(), name);
        if (sourcePostId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "해당 지역의 내 여행이야기 사진이 아닙니다.");
        }

        // 지도 전용 사본으로 복사 → 출처 글 삭제와 무관하게 유지
        String filename = copyToImages(fileStorageService.toHostPath(SUB_DIR, name), mimetypeFromName(name));

        replaceCover(memberId, level, request.getRegionCode(),
                fileStorageService.toUrl(SUB_DIR, filename),
                fileStorageService.toHostPath(SUB_DIR, filename),
                sourcePostId,
                coalesce(request.getFocusX(), DEFAULT_FOCUS),
                coalesce(request.getFocusY(), DEFAULT_FOCUS),
                coalesce(request.getZoom(), DEFAULT_ZOOM));
    }

    @Override
    @Transactional
    public void uploadCover(Long memberId, int sidoCode, MultipartFile file) {
        String filename;
        try {
            filename = fileStorageService.save(file, SUB_DIR);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 저장에 실패했습니다.");
        }
        replaceCover(memberId, LEVEL_SIDO, sidoCode,
                fileStorageService.toUrl(SUB_DIR, filename),
                fileStorageService.toHostPath(SUB_DIR, filename),
                null, DEFAULT_FOCUS, DEFAULT_FOCUS, DEFAULT_ZOOM);
    }

    @Override
    @Transactional
    public void updateCrop(Long memberId, CoverCropRequest request) {
        int rows = memberMapMapper.updateCrop(memberId, level(request.getRegionLevel()),
                request.getRegionCode(), request.getFocusX(), request.getFocusY(), request.getZoom());
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "지정된 표지 사진이 없습니다.");
        }
    }

    @Override
    @Transactional
    public void resetCover(Long memberId, int sidoCode) {
        String oldPath = memberMapMapper.findCoverHostPath(memberId, LEVEL_SIDO, sidoCode);
        memberMapMapper.deleteCover(memberId, LEVEL_SIDO, sidoCode);
        deleteFileQuietly(oldPath);
    }

    // ── private ──────────────────────────────────────────────────────────────

    /** 표지 교체: 새 사본 upsert 후 이전 사본 파일 정리. */
    private void replaceCover(Long memberId, String level, int regionCode,
                              String imageUrl, String hostPath, Long sourcePostId,
                              double focusX, double focusY, double zoom) {
        String oldPath = memberMapMapper.findCoverHostPath(memberId, level, regionCode);
        memberMapMapper.upsertCover(memberId, level, regionCode, imageUrl, hostPath,
                sourcePostId, focusX, focusY, zoom);
        if (oldPath != null && !oldPath.equals(hostPath)) {
            deleteFileQuietly(oldPath);
        }
    }

    /** content에서 /uploads/images/ 로 시작하는 <img src> 만 추출(순서 유지). */
    private List<String> extractImageUrls(String content) {
        List<String> urls = new ArrayList<>();
        Matcher m = IMG_SRC.matcher(content);
        while (m.find()) {
            String src = m.group(1);
            if (src.startsWith(URL_PREFIX)) {
                addUnique(urls, src);
            }
        }
        return urls;
    }

    /** URL → 안전한 파일명 추출(경로 traversal 차단). */
    private String imageName(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(URL_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 이미지 경로입니다.");
        }
        String name = imageUrl.substring(URL_PREFIX.length());
        if (!SAFE_NAME.matcher(name).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 이미지 이름입니다.");
        }
        return name;
    }

    private String copyToImages(String srcHostPath, String mimetype) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(srcHostPath));
            return fileStorageService.saveBytes(bytes, mimetype, SUB_DIR);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 복사에 실패했습니다.");
        }
    }

    private String mimetypeFromName(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private void addUnique(List<String> list, String v) {
        if (!list.contains(v)) list.add(v);
    }

    private void deleteFileQuietly(String hostPath) {
        if (hostPath != null && !hostPath.isBlank()) {
            fileStorageService.delete(hostPath);
        }
    }

    private String level(String raw) {
        return raw == null || raw.isBlank() ? LEVEL_SIDO : raw;
    }

    private double coalesce(Double v, double fallback) {
        return v == null ? fallback : v;
    }
}
