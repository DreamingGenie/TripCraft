package com.tripcraft.attraction.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourApiDetailInfoItem {

    // 공통
    private String contentid;
    private String contenttypeid;
    private String fldgubun;
    private String infoname;
    private String infotext;
    private String serialnum;

    // 서브 콘텐츠 (여행코스 등)
    private String subcontentid;
    private String subdetailalt;
    private String subdetailimg;
    private String subdetailoverview;
    private String subname;
    private String subnum;

    // 객실 기본 정보 (숙박 contenttypeid=32)
    private String roomcode;
    private String roomtitle;
    private String roomsize1;
    private String roomsize2;
    private String roomcount;
    private String roombasecount;
    private String roommaxcount;
    private String roomintro;

    // 객실 요금
    private String roomoffseasonminfee1;
    private String roomoffseasonminfee2;
    private String roompeakseasonminfee1;
    private String roompeakseasonminfee2;

    // 객실 편의시설
    private String roombathfacility;
    private String roombath;
    private String roomhometheater;
    private String roomaircondition;
    private String roomtv;
    private String roompc;
    private String roomcable;
    private String roominternet;
    private String roomrefrigerator;
    private String roomtoiletries;
    private String roomsofa;
    private String roomtable;
    private String roomhairdryer;
    private String roomcook;

    // 객실 이미지
    private String roomimg1;
    private String roomimg1alt;
    private String roomimg2;
    private String roomimg2alt;
    private String roomimg3;
    private String roomimg3alt;
    private String roomimg4;
    private String roomimg4alt;
    private String roomimg5;
    private String roomimg5alt;

    // 저작권
    private String cpyrhtDivCd1;
    private String cpyrhtDivCd2;
    private String cpyrhtDivCd3;
    private String cpyrhtDivCd4;
    private String cpyrhtDivCd5;
}
