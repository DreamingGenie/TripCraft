package com.tripcraft.member.controller;

import com.tripcraft.global.attach.domain.Attach;
import com.tripcraft.global.attach.mapper.AttachMapper;
import com.tripcraft.global.response.ApiResponse;
import com.tripcraft.global.storage.FileStorageService;
import com.tripcraft.member.domain.Member;
import com.tripcraft.member.dto.UpdateNicknameRequest;
import com.tripcraft.member.dto.UpdatePasswordRequest;
import com.tripcraft.member.dto.WithdrawRequest;
import com.tripcraft.member.mapper.MemberMapper;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

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

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @Valid @RequestBody WithdrawRequest request,
            @AuthenticationPrincipal Long memberId) {
        memberService.withdraw(memberId, request.getPassword());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
