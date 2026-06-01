package com.tripcraft.attraction.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class AttractionDetailDto {
    private Long id;
    private String contentId;
    private String title;
    private Integer contentTypeId;
    private String category;
    private String region;
    private String sigunguName;
    private String addr1;
    private String addr2;
    private String tel;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String firstImage;
    private String firstImage2;
    // detailCommon2
    private String overview;
    private String homepage;
    private String telname;
    // detailIntro2
    private Map<String, String> intro;
    // detailImage2
    private List<ImageItem> images;
    // detailInfo2
    private List<InfoItem> infoList;

    @Getter @Builder
    public static class ImageItem {
        private String originimgurl;
        private String smallimageurl;
        private String imgname;
        private String cpyrhtDivCd;
    }

    @Getter @Builder
    public static class InfoItem {
        private String infoname;
        private String infotext;
        private Map<String, Object> roomData;
    }
}
