package com.tripcraft.attraction.client.dto;

/**
 * 카카오 로컬 category/keyword 검색 응답의 개별 장소(document).
 *
 * @param id                카카오 place id (external_id로 저장)
 * @param placeName         장소명
 * @param categoryGroupCode 카테고리 그룹코드 (AT4/FD6/CE7/AD5/CT1 …)
 * @param x                 경도(longitude) 문자열
 * @param y                 위도(latitude) 문자열
 * @param addressName       지번 주소 (region 매칭에 사용)
 * @param roadAddressName   도로명 주소 (표시용 addr1 우선값)
 * @param phone             전화번호
 */
public record KakaoPlaceItem(
        String id,
        String placeName,
        String categoryGroupCode,
        String x,
        String y,
        String addressName,
        String roadAddressName,
        String phone
) {}
