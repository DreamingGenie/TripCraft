package com.tripcraft.attraction.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourApiDetailImageItem {

    private String contentid;
    private String originimgurl;
    private String imgname;
    private String smallimageurl;
    private String cpyrhtDivCd;
    private String serialnum;
}
