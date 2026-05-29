package com.tripcraft.attraction.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AttractionGroupStat {
    private String region;
    private String sigunguName;
    private String category;
    private int count;
}
