package com.tripcraft.attraction.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourApiDetailIntroItem {

    private String contentid;
    private String contenttypeid;

    // 관광지 (contenttypeid=12)
    private String expagerange;
    private String expguide;
    private String heritage1;
    private String heritage2;
    private String heritage3;
    private String infocenter;
    private String opendate;
    private String parking;
    private String restdate;
    private String taketime;
    private String theme;
    private String accomcount;
    private String chkbabycarriage;
    private String chkcreditcard;
    private String chkpet;
    private String useseason;
    private String usetime;

    // 문화시설 (contenttypeid=14)
    private String accomcountculture;
    private String chkbabycarriageculture;
    private String chkcreditcardculture;
    private String chkpetculture;
    private String discountinfofood;  // 음식점에도 있지만 문화시설 응답에 포함될 수 있음
    private String infocenterculture;
    private String parkingculture;
    private String parkingfee;
    private String restdateculture;
    private String scale;
    private String spendtime;
    private String usefee;
    private String usetimeculture;

    // 행사/공연/축제 (contenttypeid=15)
    private String agelimit;
    private String bookingplace;
    private String discountinfofestival;
    private String eventhomepage;
    private String eventplace;
    private String eventstartdate;
    private String eventenddate;
    private String festivalgrade;
    private String playtime;
    private String program;
    private String spendtimefestival;
    private String sponsor1;
    private String sponsor1tel;
    private String sponsor2;
    private String sponsor2tel;
    private String subevent;
    private String usetimefestival;

    // 여행코스 (contenttypeid=25)
    private String distance;
    private String infocentertourcourse;
    private String schedule;
    private String taketime_tourcourse; // alias for taketime in this type
    private String theme_tourcourse;

    // 레포츠 (contenttypeid=28)
    private String accomcountleports;
    private String chkbabycarriageleports;
    private String chkcreditcardleports;
    private String chkpetleports;
    private String expagerangeleports;
    private String infocenterleports;
    private String openperiod;
    private String parkingfeeleports;
    private String parkingleports;
    private String reservation;
    private String restdateleports;
    private String scaleleports;
    private String usetimeleports;
    private String usefeeleports;

    // 숙박 (contenttypeid=32)
    private String accomcountlodging;
    private String barbecue;
    private String beauty;
    private String beverage;
    private String bicycle;
    private String campfire;
    private String checkintime;
    private String checkouttime;
    private String chkcooking;
    private String fitness;
    private String foodplace;
    private String infocenterlodging;
    private String karaoke;
    private String parking_lodging;
    private String parkinglodging;
    private String pickup;
    private String placeinfo;
    private String publicbath;
    private String publicpc;
    private String refundregulation;
    private String reservationlodging;
    private String reservationurl;
    private String roomcount;
    private String roomtype;
    private String sauna;
    private String scalelodging;
    private String seminar;
    private String sports;
    private String subfacility;

    // 쇼핑 (contenttypeid=38)
    private String chkbabycarriageshopping;
    private String chkcreditcardshopping;
    private String chkpetshopping;
    private String culturecenter;
    private String fairday;
    private String infocentershopping;
    private String opendateshopping;
    private String opentime;
    private String parkingshopping;
    private String restdateshopping;
    private String restroom;
    private String saleitem;
    private String saleitemcost;
    private String scaleshopping;
    private String shopguide;

    // 음식점 (contenttypeid=39)
    private String chkcreditcardfood;
    private String discountinfo;
    private String firstmenu;
    private String infocenterfood;
    private String kidsfacility;
    private String lcnsno;
    private String opendatefood;
    private String opentimefood;
    private String packing;
    private String parkingfood;
    private String reservationfood;
    private String restdatefood;
    private String scalefood;
    private String seat;
    private String smoking;
    private String treatmenu;
}
