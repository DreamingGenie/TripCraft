package com.tripcraft.attraction.domain;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
public class AttractionDetailIntro {
    private String contentId;
    private Integer contentTypeId;
    private String introData; // JSON string
    private LocalDateTime syncedAt;
}
