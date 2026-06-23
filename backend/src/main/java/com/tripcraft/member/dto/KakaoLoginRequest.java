package com.tripcraft.member.dto;

import jakarta.validation.constraints.NotBlank;

/** 카카오 OAuth: 프론트가 받은 인가 코드(code)를 백엔드로 전달 */
public record KakaoLoginRequest(@NotBlank String code) {}
