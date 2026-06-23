package com.tripcraft.member.service;

import com.tripcraft.member.dto.LoginRequest;
import com.tripcraft.member.dto.SignupRequest;
import com.tripcraft.member.dto.TokenResponse;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    void signup(SignupRequest request);

    void login(LoginRequest request, HttpServletResponse response);

    /** 카카오 인가 코드로 로그인/자동가입 후 토큰 쿠키 발급 */
    void kakaoLogin(String code, HttpServletResponse response);

    void refresh(String refreshToken, HttpServletResponse response);

    void logout(String refreshToken, HttpServletResponse response);
}
