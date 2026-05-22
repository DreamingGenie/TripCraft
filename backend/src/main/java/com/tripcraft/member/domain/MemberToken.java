package com.tripcraft.member.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MemberToken {

    private Long id;
    private Long memberId;
    private String token;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
