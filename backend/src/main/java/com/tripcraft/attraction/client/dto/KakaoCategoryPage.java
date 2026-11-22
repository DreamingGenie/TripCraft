package com.tripcraft.attraction.client.dto;

import java.util.List;

/**
 * 카카오 로컬 category 검색 한 페이지 결과.
 *
 * @param items      이 페이지의 장소 목록
 * @param isEnd      마지막 페이지 여부(meta.is_end). false면 더 조회 가능
 * @param totalCount 검색된 전체 문서 수(meta.total_count). rect당 최대 45로 제한됨
 */
public record KakaoCategoryPage(
        List<KakaoPlaceItem> items,
        boolean isEnd,
        int totalCount
) {
    public static KakaoCategoryPage empty() {
        return new KakaoCategoryPage(List.of(), true, 0);
    }
}
