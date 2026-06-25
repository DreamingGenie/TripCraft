package com.tripcraft.member.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 방문 지도: 표지 crop(초점/확대)만 갱신. */
@Getter
@Setter
public class CoverCropRequest {

    private String regionLevel = "SIDO";

    @NotNull
    private Integer regionCode;

    @NotNull
    private Double focusX;

    @NotNull
    private Double focusY;

    @NotNull
    private Double zoom;
}
