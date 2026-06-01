package com.tripcraft.attraction.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourApiItem {

    private String contentid;
    private String contenttypeid;
    private String title;
    private String addr1;
    private String addr2;
    private String firstimage;
    private String mapx;        // 경도 (longitude)
    private String mapy;        // 위도 (latitude)
    private String areacode;    // 시도 코드
    private String sigungucode;
    private String tel;
    private String createdtime;
    private String modifiedtime;
    private String firstimage2;
    private String mlevel;
    private String cat1;
    private String cat2;
    private String cat3;
    private String zipcode;
    private String lDongRegnCd;
    private String lDongSignguCd;
    private String lclsSystm1;
    private String lclsSystm2;
    private String lclsSystm3;
    private String cpyrhtDivCd;
}
