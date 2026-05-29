package com.tripcraft.attraction.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AttractionPageResponse {

    private List<AttractionListItem> items;
    private int total;
    private int page;
    private int size;
    private List<AttractionGroupStat> groupStats;
}
