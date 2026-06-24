package com.tripcraft.member.controller;

import com.tripcraft.global.attach.domain.Attach;
import com.tripcraft.global.attach.mapper.AttachMapper;
import com.tripcraft.global.response.ApiResponse;
import com.tripcraft.global.storage.FileStorageService;
import com.tripcraft.member.domain.Member;
import com.tripcraft.member.dto.CoverCropRequest;
import com.tripcraft.member.dto.CoverImageRequest;
import com.tripcraft.member.dto.RegionImageItem;
import com.tripcraft.member.dto.RegionMapItem;
import com.tripcraft.member.dto.RegionStoryItem;
import com.tripcraft.member.dto.UpdateNicknameRequest;
import com.tripcraft.member.dto.UpdatePasswordRequest;
import com.tripcraft.member.dto.WithdrawRequest;
import com.tripcraft.member.mapper.MemberMapper;
import com.tripcraft.member.service.MemberMapService;
import com.tripcraft.member.service.MemberService;
import com.tripcraft.plan.mapper.TripMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private static final String PROFILE_DIR = "profile";
    private static final String PROFILE_TARGET = "profile";

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final TripMapper tripMapper;
    private final AttachMapper attachMapper;
    private final FileStorageService fileStorageService;
    private final MemberService memberService;
    private final MemberMapService memberMapService;

    @PatchMapping("/me/nickname")
    public ResponseEntity<ApiResponse<Void>> updateNickname(
            @Valid @RequestBody UpdateNicknameRequest request,
            @AuthenticationPrincipal Long memberId) {
        memberMapper.findByNickname(request.getNickname()).ifPresent(existing -> {
            if (!existing.getId().equals(memberId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");
            }
        });
        memberMapper.updateNickname(memberId, request.getNickname());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @Valid @RequestBody UpdatePasswordRequest request,
            @AuthenticationPrincipal Long memberId) {
        Member member = memberMapper.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!passwordEncoder.matches(request.getCurrentPassword(), member.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다.");
        }
        memberMapper.updatePassword(memberId, passwordEncoder.encode(request.getNewPassword()));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/me/profile-image")
    public ResponseEntity<ApiResponse<String>> getProfileImage(
            @AuthenticationPrincipal Long memberId) {
        List<Attach> list = attachMapper.findByTarget(PROFILE_TARGET, memberId);
        String url = list.isEmpty() ? null : fileStorageService.toUrl(PROFILE_DIR, list.get(0).getName());
        return ResponseEntity.ok(ApiResponse.ok(url));
    }

    @PostMapping("/me/profile-image")
    public ResponseEntity<ApiResponse<String>> uploadProfileImage(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Long memberId) throws IOException {

        // 기존 프로필 이미지 삭제
        List<Attach> existing = attachMapper.findByTarget(PROFILE_TARGET, memberId);
        for (Attach old : existing) {
            fileStorageService.delete(old.getHostPath());
        }
        attachMapper.deleteByTarget(PROFILE_TARGET, memberId);

        String filename = fileStorageService.save(file, PROFILE_DIR);

        Attach attach = new Attach();
        attach.setName(filename);
        attach.setHostName(file.getOriginalFilename() != null ? file.getOriginalFilename() : filename);
        attach.setSize(file.getSize());
        attach.setMimetype(file.getContentType());
        attach.setHostPath(fileStorageService.toHostPath(PROFILE_DIR, filename));
        attach.setTarget(PROFILE_TARGET);
        attach.setTargetId(memberId);
        attachMapper.insert(attach);

        return ResponseEntity.ok(ApiResponse.ok(fileStorageService.toUrl(PROFILE_DIR, filename)));
    }

    @DeleteMapping("/me/profile-image")
    public ResponseEntity<ApiResponse<Void>> deleteProfileImage(
            @AuthenticationPrincipal Long memberId) {
        List<Attach> existing = attachMapper.findByTarget(PROFILE_TARGET, memberId);
        for (Attach old : existing) {
            fileStorageService.delete(old.getHostPath());
        }
        attachMapper.deleteByTarget(PROFILE_TARGET, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/me/visited-regions")
    public ResponseEntity<ApiResponse<List<Integer>>> getVisitedRegions(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(tripMapper.findVisitedSidoCodes(memberId)));
    }

    // === 방문 지도 (후기 사진 기반) ===

    /** 시도별 방문/예정 상태 + 표지 사진 + crop + 후기 수 (지도 1회 로드). */
    @GetMapping("/me/map")
    public ResponseEntity<ApiResponse<List<RegionMapItem>>> getMap(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(memberMapService.getMap(memberId)));
    }

    /** 한 시도에서 표지로 고를 수 있는 여행이야기(글) 목록(날짜·사진 수 포함). */
    @GetMapping("/me/map/regions/{sidoCode}/stories")
    public ResponseEntity<ApiResponse<List<RegionStoryItem>>> getRegionStories(
            @PathVariable("sidoCode") int sidoCode,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(memberMapService.getRegionStories(memberId, sidoCode)));
    }

    /** 한 글의 사진(커버·본문) 목록. */
    @GetMapping("/me/map/regions/{sidoCode}/posts/{postId}/images")
    public ResponseEntity<ApiResponse<List<RegionImageItem>>> getPostImages(
            @PathVariable("sidoCode") int sidoCode,
            @PathVariable("postId") long postId,
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(memberMapService.getPostImages(memberId, sidoCode, postId)));
    }

    /** 후보 사진을 지도 전용으로 복사해 표지로 지정. */
    @PutMapping("/me/map/cover")
    public ResponseEntity<ApiResponse<Void>> setRegionCover(
            @Valid @RequestBody CoverImageRequest request,
            @AuthenticationPrincipal Long memberId) {
        memberMapService.setCoverFromImage(memberId, request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** 새 사진을 업로드해 표지로 지정(직접 업로드). */
    @PostMapping("/me/map/cover/upload")
    public ResponseEntity<ApiResponse<Void>> uploadRegionCover(
            @RequestParam("regionCode") int regionCode,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Long memberId) {
        memberMapService.uploadCover(memberId, regionCode, file);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** 표지 crop(초점/확대)만 갱신. */
    @PatchMapping("/me/map/cover/crop")
    public ResponseEntity<ApiResponse<Void>> updateRegionCrop(
            @Valid @RequestBody CoverCropRequest request,
            @AuthenticationPrincipal Long memberId) {
        memberMapService.updateCrop(memberId, request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** 지역 표지 해제 → 기본값(최신 후기)으로 복귀. */
    @DeleteMapping("/me/map/cover/{sidoCode}")
    public ResponseEntity<ApiResponse<Void>> resetRegionCover(
            @PathVariable("sidoCode") int sidoCode,
            @AuthenticationPrincipal Long memberId) {
        memberMapService.resetCover(memberId, sidoCode);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> searchMembers(
            @RequestParam("q") String q) {
        if (q == null || q.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
        List<Map<String, Object>> result = memberMapper.searchByNicknameOrEmail(q).stream()
            .map(m -> {
                Map<String, Object> row = new java.util.HashMap<>();
                row.put("id",       m.getId());
                row.put("nickname", m.getNickname());
                row.put("email",    m.getEmail());
                return row;
            })
            .toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @Valid @RequestBody WithdrawRequest request,
            @AuthenticationPrincipal Long memberId) {
        memberService.withdraw(memberId, request.getPassword());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
