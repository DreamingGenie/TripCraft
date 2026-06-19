package com.tripcraft.attraction.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 거리 기반 주변 관광지 조회 결과 (chat 컨텍스트 주입 + 프론트 핀/상세 이동 버튼용). */
@Getter
@Setter
@NoArgsConstructor
public class NearbyAttraction {
    private Long id;
    private String title;
    private Integer contentTypeId;
    private String category;        // contentTypeId→라벨 (서비스에서 채움)
    private String addr1;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Double distanceM;       // ST_Distance_Sphere 결과 (미터)
}
