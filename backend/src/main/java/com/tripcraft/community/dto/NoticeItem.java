package com.tripcraft.community.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class NoticeItem {

    private Long id;
    private String title;
    private LocalDateTime createdAt;
}
