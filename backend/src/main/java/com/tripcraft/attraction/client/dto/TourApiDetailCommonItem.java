package com.tripcraft.attraction.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourApiDetailCommonItem {

    private String contentid;
    private String overview;
    private String homepage;
    private String telname;
}
