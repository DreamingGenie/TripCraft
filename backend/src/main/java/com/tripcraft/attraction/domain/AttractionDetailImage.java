package com.tripcraft.attraction.domain;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AttractionDetailImage {
    private Long id;
    private String contentId;
    private String serialnum;
    private String originimgurl;
    private String smallimageurl;
    private String imgname;
    private String cpyrhtDivCd;
}
