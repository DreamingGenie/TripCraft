package com.tripcraft.member.service;

import com.tripcraft.global.attach.domain.Attach;
import com.tripcraft.global.attach.mapper.AttachMapper;
import com.tripcraft.global.security.JwtTokenProvider;
import com.tripcraft.global.storage.FileStorageService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final MemberMapper memberMapper;
    private final MemberTokenMapper memberTokenMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final KakaoOAuthService kakaoOAuthService;
    private final AttachMapper attachMapper;
    private final FileStorageService fileStorageService;

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

        Member member = buildMember(request);
        memberMapper.insert(member);
        log.info("회원 가입 완료 memberId={}", member.getId());
    }

    @Override
    @Transactional
    public void login(LoginRequest request, HttpServletResponse response) {
        Member member = memberMapper.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("로그인 실패(미존재 이메일) email={}", request.getEmail());
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
                });

        if (member.getPassword() == null) {
            log.warn("로그인 실패(소셜 전용 계정) memberId={}", member.getId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "소셜 계정으로만 로그인 가능합니다.");
        }

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            log.warn("로그인 실패(비밀번호 불일치) memberId={}", member.getId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        memberTokenMapper.deleteByMemberId(member.getId());
        issueTokens(member, response);
        log.info("로그인 성공 memberId={}", member.getId());
    }

    @Override
    @Transactional
    public void kakaoLogin(String code, HttpServletResponse response) {
        String kakaoToken = kakaoOAuthService.getKakaoAccessToken(code);
        KakaoUserInfo info = kakaoOAuthService.getKakaoUserInfo(kakaoToken);

        Member member = findOrCreateMember(info);
        memberTokenMapper.deleteByMemberId(member.getId());
        issueTokens(member, response);
        log.info("카카오 로그인 성공 memberId={}", member.getId());
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
        log.info("카카오 신규 가입 memberId={}", member.getId());
        saveKakaoProfileImage(member.getId(), info.profileImage());
        return member;
    }

    /** 카카오 프로필 이미지를 다운로드해 attach(profile)로 저장 (best-effort, 실패해도 가입은 유지) */
    private void saveKakaoProfileImage(Long memberId, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        try {
            KakaoOAuthService.DownloadedImage img = kakaoOAuthService.downloadImage(imageUrl);
            if (img == null || img.bytes() == null || img.bytes().length == 0) return;
            String filename = fileStorageService.saveBytes(img.bytes(), img.contentType(), "profile");
            Attach attach = new Attach();
            attach.setName(filename);
            attach.setHostName(filename);
            attach.setSize(img.bytes().length);
            attach.setMimetype(img.contentType());
            attach.setHostPath(fileStorageService.toHostPath("profile", filename));
            attach.setTarget("profile");
            attach.setTargetId(memberId);
            attachMapper.insert(attach);
        } catch (Exception e) {
            log.warn("카카오 프로필 이미지 저장 실패 memberId={}: {}", memberId, e.getMessage());
        }
    }

    /**
     * 카카오 닉네임 사용 (없으면 기본값).
     * 닉네임은 유니크 제약이 없고 일반 회원가입도 중복을 허용하므로 dedup하지 않는다.
     */
    private String resolveNickname(String base) {
        String name = (base != null && !base.isBlank()) ? base : "카카오사용자";
        return name.length() > 20 ? name.substring(0, 20) : name;  // nickname VARCHAR(20) 안전 처리
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
        Long memberId = (refreshToken != null && jwtTokenProvider.validate(refreshToken))
                ? jwtTokenProvider.getMemberId(refreshToken) : null;
        if (refreshToken != null) {
            memberTokenMapper.deleteByToken(refreshToken);
        }
        clearAllCookies(response);
        log.info("로그아웃 memberId={}", memberId);
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
