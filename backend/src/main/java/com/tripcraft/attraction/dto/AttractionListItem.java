package com.tripcraft.attraction.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class AttractionListItem {

    private Long id;
    private String title;
    private Integer contentTypeId;
    private String category;
    private Integer sidoCode;
    private String region;
    private String address;
    private String firstImage;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private boolean favorited;
}
