package com.tripcraft.member.service;

import com.tripcraft.global.security.JwtTokenProvider;
import com.tripcraft.member.domain.Member;
import com.tripcraft.member.domain.MemberToken;
import com.tripcraft.member.dto.LoginRequest;
import com.tripcraft.member.dto.SignupRequest;
import com.tripcraft.member.dto.TokenResponse;
import com.tripcraft.member.mapper.MemberMapper;
import com.tripcraft.member.mapper.MemberTokenMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final MemberMapper memberMapper;
    private final MemberTokenMapper memberTokenMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    @Override
    @Transactional
    public TokenResponse signup(SignupRequest request, HttpServletResponse response) {
        memberMapper.findByEmail(request.getEmail()).ifPresent(m -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.");
        });

        Member member = buildMember(request);
        memberMapper.insert(member);
        return issueTokens(member, response);
    }

    @Override
    @Transactional
    public TokenResponse login(LoginRequest request, HttpServletResponse response) {
        Member member = memberMapper.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        memberTokenMapper.deleteByMemberId(member.getId());
        return issueTokens(member, response);
    }

    @Override
    @Transactional
    public TokenResponse refresh(String refreshToken, HttpServletResponse response) {
        if (refreshToken == null || !jwtTokenProvider.validate(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다.");
        }

        MemberToken stored = memberTokenMapper.findByToken(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "만료된 리프레시 토큰입니다."));

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            memberTokenMapper.deleteByToken(refreshToken);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "만료된 리프레시 토큰입니다.");
        }

        Long memberId = jwtTokenProvider.getMemberId(refreshToken);
        String role = jwtTokenProvider.getRole(refreshToken);
        String newAccessToken = jwtTokenProvider.createAccessToken(memberId, role);
        return new TokenResponse(newAccessToken);
    }

    @Override
    @Transactional
    public void logout(String refreshToken, HttpServletResponse response) {
        if (refreshToken != null) {
            memberTokenMapper.deleteByToken(refreshToken);
        }
        clearRefreshCookie(response);
    }

    private TokenResponse issueTokens(Member member, HttpServletResponse response) {
        String role = member.getRole().name();
        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), role);
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId(), role);

        MemberToken token = new MemberToken();
        token.setMemberId(member.getId());
        token.setToken(refreshToken);
        token.setExpiresAt(LocalDateTime.now().plusNanos(refreshTokenExpiry * 1_000_000L));
        memberTokenMapper.insert(token);

        setRefreshCookie(response, refreshToken);
        return new TokenResponse(accessToken);
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("refresh_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/auth/refresh");
        cookie.setMaxAge((int) (refreshTokenExpiry / 1000));
        response.addCookie(cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refresh_token", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/auth/refresh");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private Member buildMember(SignupRequest request) {
        Member member = new Member();
        member.setEmail(request.getEmail());
        member.setPassword(passwordEncoder.encode(request.getPassword()));
        member.setNickname(request.getNickname());
        member.setRole(Member.Role.USER);
        return member;
    }
}
