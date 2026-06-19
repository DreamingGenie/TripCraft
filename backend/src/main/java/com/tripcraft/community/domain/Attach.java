package com.tripcraft.community.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Attach {
    private Long id;
    private String target;    // 'profile' | 'post' | 'post_draft'
    private Long targetId;    // NULL = draft 상태
    private String name;      // UUID.ext
    private LocalDateTime createdAt;
}
