package com.tripcraft.member.dto;

import lombok.Getter;

@Getter
public class WithdrawRequest {

    /** 일반 계정은 본인 확인용, 소셜 전용 계정은 비밀번호가 없어 선택값(null). */
    private String password;
}
