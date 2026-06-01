package com.tripcraft.attraction.domain;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AttractionDetailInfo {
    private Long id;
    private String contentId;
    private String serialnum;
    private String fldgubun;
    private String infoname;
    private String infotext;
    private String subcontentid;
    private String subdetailalt;
    private String subdetailimg;
    private String subdetailoverview;
    private String subname;
    private String subnum;
    private String roomData; // JSON string (숙박 객실 정보)
}
