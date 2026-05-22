package com.tripcraft.attraction.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class Attraction {

    private Long id;
    private Integer contentId;
    private Integer contentTypeId;
    private String title;
    private String addr1;
    private String addr2;
    private String zipcode;
    private String tel;
    private String firstimage;
    private String firstimage2;
    private BigDecimal mapx;
    private BigDecimal mapy;
    private Integer sigunguCode;
    private Integer areaCode;
    private String overview;
    private LocalDateTime apiModifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
