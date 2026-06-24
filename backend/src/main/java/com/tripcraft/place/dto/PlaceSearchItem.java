package com.tripcraft.place.dto;

import java.math.BigDecimal;

/** Kakao Local 키워드 검색 결과 1건 */
public record PlaceSearchItem(
        String name,
        String address,
        String category,
        BigDecimal latitude,
        BigDecimal longitude) {}
