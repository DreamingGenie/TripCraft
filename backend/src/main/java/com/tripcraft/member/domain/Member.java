package com.tripcraft.member.domain;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Member {

    private Long id;
    private String email;
    private String password;
    private String nickname;
    private Role role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum Role {
        USER, ADMIN
    }
}
