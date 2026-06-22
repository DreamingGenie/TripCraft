package com.tripcraft.member.controller;

import com.tripcraft.global.response.ApiResponse;
import com.tripcraft.member.domain.Member;
import com.tripcraft.member.dto.KakaoLoginRequest;
import com.tripcraft.member.dto.LoginRequest;
import com.tripcraft.member.dto.MeResponse;
import com.tripcraft.member.dto.SignupRequest;
import com.tripcraft.member.mapper.MemberMapper;
import com.tripcraft.member.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MemberMapper memberMapper;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(
            @Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.status(201).body(ApiResponse.ok());
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        authService.login(request, response);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/kakao")
    public ResponseEntity<ApiResponse<Void>> kakaoLogin(
            @Valid @RequestBody KakaoLoginRequest request,
            HttpServletResponse response) {
        authService.kakaoLogin(request.code(), response);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = resolveRefreshCookie(request);
        authService.refresh(refreshToken, response);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(
            @AuthenticationPrincipal Long memberId) {
        Member member = memberMapper.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return ResponseEntity.ok(ApiResponse.ok(
                new MeResponse(member.getId(), member.getEmail(), member.getNickname(),
                        member.getRole().name(), member.getSocialProvider())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = resolveRefreshCookie(request);
        authService.logout(refreshToken, response);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private String resolveRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> "refresh_token".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
