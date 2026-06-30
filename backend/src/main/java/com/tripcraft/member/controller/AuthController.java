package com.tripcraft.member.controller;

import com.tripcraft.global.response.ApiResponse;
import com.tripcraft.member.domain.Member;
import com.tripcraft.member.dto.KakaoLoginRequest;
import com.tripcraft.member.dto.LoginRequest;
import com.tripcraft.member.dto.MeResponse;
import com.tripcraft.member.dto.SignupRequest;
import com.tripcraft.member.mapper.MemberMapper;
import com.tripcraft.member.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "인증", description = "회원가입·로그인·토큰 재발급·로그아웃 (쿠키 기반 JWT)")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MemberMapper memberMapper;

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(
            @Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.status(201).body(ApiResponse.ok());
    }

    @Operation(summary = "로그인", description = "성공 시 access_token·refresh_token을 HttpOnly 쿠키로 발급")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        authService.login(request, response);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "카카오 소셜 로그인", description = "인가 코드로 카카오 로그인 후 쿠키 발급")
    @PostMapping("/kakao")
    public ResponseEntity<ApiResponse<Void>> kakaoLogin(
            @Valid @RequestBody KakaoLoginRequest request,
            HttpServletResponse response) {
        authService.kakaoLogin(request.code(), response);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "액세스 토큰 재발급", description = "refresh_token 쿠키로 access_token 재발급")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = resolveRefreshCookie(request);
        authService.refresh(refreshToken, response);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(
            @AuthenticationPrincipal Long memberId) {
        Member member = memberMapper.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return ResponseEntity.ok(ApiResponse.ok(
                new MeResponse(member.getId(), member.getEmail(), member.getNickname(),
                        member.getRole().name(), member.getSocialProvider())));
    }

    @Operation(summary = "로그아웃", description = "refresh_token 무효화 + 쿠키 제거")
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
