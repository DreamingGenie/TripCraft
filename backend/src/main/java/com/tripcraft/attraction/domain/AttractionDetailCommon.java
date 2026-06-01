package com.tripcraft.attraction.domain;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
public class AttractionDetailCommon {
    private String contentId;
    private String overview;
    private String homepage;
    private String telname;
    private LocalDateTime syncedAt;
}
