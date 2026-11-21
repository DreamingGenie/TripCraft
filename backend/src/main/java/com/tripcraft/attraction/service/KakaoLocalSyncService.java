package com.tripcraft.attraction.service;

import java.util.List;

/**
 * 카카오 로컬 API 기반 대량 장소 수집(개선 02 Phase A).
 *
 * <p>전국 bounding box를 격자 rect로 나눠 카테고리별 검색하고, rect당 45건 상한에 걸리면
 * quadtree 방식으로 셀을 세분화해 누락을 줄인다. 결과는 {@code attraction} 테이블에
 * {@code source='KAKAO'}로 업서트된다.
 */
public interface KakaoLocalSyncService {

    /** 지원하는 전체 카테고리 그룹코드 수집 */
    SyncResult syncAll();

    /** 특정 카테고리 그룹코드(AT4/FD6/CE7/AD5/CT1)만 수집 */
    SyncResult syncByCategory(String categoryGroupCode);

    /**
     * @param saved       업서트한 장소 수
     * @param skipped     region 매핑 실패 등으로 건너뛴 수
     * @param apiCalls    소비한 카카오 API 호출 수
     * @param elapsedMs   소요 시간(ms)
     * @param quotaHit    쿼터 한도로 조기 중단되었는지 여부
     */
    record SyncResult(int saved, int skipped, int apiCalls, long elapsedMs, boolean quotaHit) {}

    /** 이번 범위에서 지원하는 카카오 카테고리 그룹코드 */
    List<String> SUPPORTED_CATEGORIES = List.of("AT4", "FD6", "CE7", "AD5", "CT1");
}
