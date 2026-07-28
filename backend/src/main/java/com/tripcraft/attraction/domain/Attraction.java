package com.tripcraft.attraction.domain;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class Attraction {

    private Long id;
    /** 출처: TOURAPI(한국관광공사) | KAKAO(카카오 로컬). 기본 TOURAPI. */
    private String source = "TOURAPI";
    /** 외부 장소 ID. source=KAKAO일 때 카카오 place id, TOURAPI는 null. */
    private String externalId;
    private String contentId;
    private Integer contentTypeId;
    private String title;
    private Integer sidoCode;
    private Integer sigunguCode;
    private String addr1;
    private String addr2;
    private String zipcode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String tel;
    private String firstImage;
    private String firstImage2;
    private Integer mlevel;
    private String cat1;
    private String cat2;
    private String cat3;
    private String lDongRegnCd;
    private String lDongSignguCd;
    private String lclsSystm1;
    private String lclsSystm2;
    private String lclsSystm3;
    private String cpyrhtDivCd;
    private LocalDateTime apiCreatedAt;
    private LocalDateTime apiModifiedAt;
    private LocalDateTime syncedAt;
}
