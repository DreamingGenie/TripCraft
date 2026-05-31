package com.tripcraft.attraction.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class AttractionDetailDto {
    private Long id;
    private String title;
    private Integer contentTypeId;
    private String category;
    private String region;
    private String sigunguName;
    private String addr1;
    private String addr2;
    private String tel;
    private String overview;
    private String firstImage;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
