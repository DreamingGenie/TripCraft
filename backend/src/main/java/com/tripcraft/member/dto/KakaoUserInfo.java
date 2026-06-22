package com.tripcraft.member.dto;

/** 카카오 사용자 정보 조회 결과 (필요한 필드만 추림) */
public record KakaoUserInfo(String socialId, String email, String nickname) {}
