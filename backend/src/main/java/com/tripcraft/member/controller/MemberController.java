package com.tripcraft.member.controller;

import com.tripcraft.global.response.ApiResponse;
import com.tripcraft.member.domain.Member;
import com.tripcraft.member.dto.UpdateNicknameRequest;
import com.tripcraft.member.dto.UpdatePasswordRequest;
import com.tripcraft.member.mapper.MemberMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    @PatchMapping("/me/nickname")
    public ResponseEntity<ApiResponse<Void>> updateNickname(
            @Valid @RequestBody UpdateNicknameRequest request,
            @AuthenticationPrincipal Long memberId) {
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
}
