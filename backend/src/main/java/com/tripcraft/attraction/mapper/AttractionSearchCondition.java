package com.tripcraft.attraction.mapper;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AttractionSearchCondition {

    private List<Integer> sidoCodes;
    private List<SigunguPair> sigunguPairs;
    private List<Integer> contentTypeIds;
    private String keyword;
    private int offset;
    private int limit;
}
