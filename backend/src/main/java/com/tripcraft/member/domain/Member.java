package com.tripcraft.member.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Member {

    private Long id;
    private String email;
    private String password;
    private String nickname;
    private Role role;
    private String socialProvider;
    private String socialId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum Role {
        USER, ADMIN
    }
}
