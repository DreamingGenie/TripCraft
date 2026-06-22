package com.tripcraft.attraction.service;

public interface ReferenceDataSyncService {

    /** TourAPI areaCode2로 시도·시군구 참조 테이블 동기화 (name 갱신, alias 보존). */
    SyncResult sync();

    record SyncResult(int sidoCount, int sigunguCount, long elapsedMs) {}
}
