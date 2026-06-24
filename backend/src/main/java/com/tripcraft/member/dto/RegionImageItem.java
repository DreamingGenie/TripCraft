package com.tripcraft.member.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 방문 지도: 한 여행이야기의 사진 한 장(커버·본문 포함). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegionImageItem {

    private String imageUrl;
}
