package com.tripcraft.attraction.service;

public interface TourApiSyncService {

    /** 전체 지역 × 콘텐츠 타입 수집 */
    SyncResult syncAll();

    /** 특정 지역 × 콘텐츠 타입만 수집 */
    SyncResult syncByArea(int areaCode, int contentTypeId);

    record SyncResult(int total, long elapsedMs) {}
}
