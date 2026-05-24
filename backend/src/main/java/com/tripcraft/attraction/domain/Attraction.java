package com.tripcraft.attraction.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class Attraction {

    private Long id;
    private String contentId;
    private Integer contentTypeId;
    private String title;
    private Integer sidoCode;
    private Integer sigunguCode;
    private String addr1;
    private String addr2;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String tel;
    private String overview;
    private String firstImage;
    private LocalDateTime apiCreatedAt;
    private LocalDateTime apiModifiedAt;
    private LocalDateTime syncedAt;
}
