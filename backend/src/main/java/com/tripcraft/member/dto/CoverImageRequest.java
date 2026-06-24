package com.tripcraft.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 방문 지도: 후보 사진(이미지 URL)을 표지로 지정. crop은 생략 시 기본값. */
@Getter
@Setter
public class CoverImageRequest {

    private String regionLevel = "SIDO";

    @NotNull
    private Integer regionCode;

    /** 표지로 쓸 이미지 URL (/uploads/images/<name>) — 글 커버 또는 본문 이미지. */
    @NotBlank
    private String imageUrl;

    private Double focusX;
    private Double focusY;
    private Double zoom;
}
