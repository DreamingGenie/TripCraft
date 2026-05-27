package com.tripcraft.plan.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
public class CandidateItem {

    private Long id;
    private Long attractionId;
    private String attractionName;
    private String firstImage;
    private Integer cityCode;
    private String cityName;
    private String category;
    private String source;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private List<BlockItem> blocks;
}
