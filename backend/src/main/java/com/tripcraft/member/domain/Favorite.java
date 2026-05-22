package com.tripcraft.member.domain;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Favorite {

    private Long id;
    private Long memberId;
    private Long attractionId;
    private LocalDateTime createdAt;
}
