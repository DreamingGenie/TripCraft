package com.tripcraft.member.service;

import com.tripcraft.global.security.JwtTokenProvider;
import com.tripcraft.member.domain.Member;
import com.tripcraft.member.domain.MemberToken;
import com.tripcraft.member.dto.KakaoUserInfo;
import com.tripcraft.member.dto.LoginRequest;
import com.tripcraft.member.dto.SignupRequest;
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
    private final KakaoOAuthService kakaoOAuthService;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    @Override
    @Transactional
    public void signup(SignupRequest request) {
        memberMapper.findByEmail(request.getEmail()).ifPresent(m -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.");
        });

        memberMapper.insert(buildMember(request));
    }

    @Override
    @Transactional
    public void login(LoginRequest request, HttpServletResponse response) {
        Member member = memberMapper.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));

        if (member.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "소셜 계정으로만 로그인 가능합니다.");
        }

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        memberTokenMapper.deleteByMemberId(member.getId());
        issueTokens(member, response);
    }

    @Override
    @Transactional
    public void kakaoLogin(String code, HttpServletResponse response) {
        String kakaoToken = kakaoOAuthService.getKakaoAccessToken(code);
        KakaoUserInfo info = kakaoOAuthService.getKakaoUserInfo(kakaoToken);

        Member member = findOrCreateMember(info);
        memberTokenMapper.deleteByMemberId(member.getId());
        issueTokens(member, response);
    }

    /** 카카오 사용자 → 회원 매핑: 소셜ID 연결 → 이메일 연결 → 신규 자동가입 */
    private Member findOrCreateMember(KakaoUserInfo info) {
        // 1) 이미 카카오 연결된 계정
        var bySocial = memberMapper.findBySocial("kakao", info.socialId());
        if (bySocial.isPresent()) {
            return bySocial.get();
        }

        // 2) 이메일이 기존 회원과 일치 → 소셜 연결
        if (info.email() != null && !info.email().isBlank()) {
            var byEmail = memberMapper.findByEmail(info.email());
            if (byEmail.isPresent()) {
                Member m = byEmail.get();
                memberMapper.linkSocial(m.getId(), "kakao", info.socialId());
                m.setSocialProvider("kakao");
                m.setSocialId(info.socialId());
                return m;
            }
        }

        // 3) 신규 자동 가입 (이메일 NULL 가능, 비밀번호 없음)
        Member member = new Member();
        member.setEmail(info.email());
        member.setPassword(null);
        member.setNickname(resolveNickname(info.nickname()));
        member.setRole(Member.Role.USER);
        member.setSocialProvider("kakao");
        member.setSocialId(info.socialId());
        memberMapper.insert(member);
        return member;
    }

    /** 닉네임 중복 시 접미사로 유니크 보장 (없으면 기본값) */
    private String resolveNickname(String base) {
        String candidate = (base != null && !base.isBlank()) ? base : "카카오사용자";
        if (candidate.length() > 16) candidate = candidate.substring(0, 16);  // 접미사 여유(최대 20자)
        String result = candidate;
        int suffix = 1;
        while (memberMapper.findByNickname(result).isPresent()) {
            suffix++;
            result = candidate + "_" + suffix;
        }
        return result;
    }

    @Override
    @Transactional
    public void refresh(String refreshToken, HttpServletResponse response) {
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
        setAccessCookie(response, newAccessToken);
    }

    @Override
    @Transactional
    public void logout(String refreshToken, HttpServletResponse response) {
        if (refreshToken != null) {
            memberTokenMapper.deleteByToken(refreshToken);
        }
        clearAllCookies(response);
    }

    private void issueTokens(Member member, HttpServletResponse response) {
        String role = member.getRole().name();
        String accessToken  = jwtTokenProvider.createAccessToken(member.getId(), role);
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId(), role);

        MemberToken token = new MemberToken();
        token.setMemberId(member.getId());
        token.setToken(refreshToken);
        token.setExpiresAt(LocalDateTime.now().plusNanos(refreshTokenExpiry * 1_000_000L));
        memberTokenMapper.insert(token);

        setAccessCookie(response, accessToken);
        setRefreshCookie(response, refreshToken);
    }

    private void setAccessCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("access_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) (accessTokenExpiry / 1000));
        response.addCookie(cookie);
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("refresh_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/auth/refresh");
        cookie.setMaxAge((int) (refreshTokenExpiry / 1000));
        response.addCookie(cookie);
    }

    private void clearAllCookies(HttpServletResponse response) {
        Cookie access = new Cookie("access_token", null);
        access.setHttpOnly(true);
        access.setPath("/");
        access.setMaxAge(0);
        response.addCookie(access);

        Cookie refresh = new Cookie("refresh_token", null);
        refresh.setHttpOnly(true);
        refresh.setPath("/api/auth/refresh");
        refresh.setMaxAge(0);
        response.addCookie(refresh);
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
