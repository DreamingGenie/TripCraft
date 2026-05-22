package com.tripcraft.attraction.mapper;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AttractionSearchCondition {

    private List<Integer> areaCodes;
    private List<Integer> sigunguCodes;
    private List<Integer> contentTypeIds;
    private String keyword;
    private Integer offset;
    private Integer limit;
}
