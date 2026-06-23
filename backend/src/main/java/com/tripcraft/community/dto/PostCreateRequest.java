package com.tripcraft.community.dto;

import lombok.Getter;

@Getter
public class PostCreateRequest {

    private String title;
    private String content;
    private Long tripId;
    private String coverImageUrl;
}
