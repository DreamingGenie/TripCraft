package com.tripcraft.member.service;

import com.tripcraft.member.dto.LoginRequest;
import com.tripcraft.member.dto.SignupRequest;
import com.tripcraft.member.dto.TokenResponse;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    void signup(SignupRequest request);

    TokenResponse login(LoginRequest request, HttpServletResponse response);

    TokenResponse refresh(String refreshToken, HttpServletResponse response);

    void logout(String refreshToken, HttpServletResponse response);
}
