-- =============================================
-- Migration: attraction 출처 구분(TourAPI | 카카오 로컬)
-- 적용 버전: schema v0.5 → v0.6
-- 작성일: 2026-07-28
-- 작업자: 전진
--
-- 배경: 개선 02(대량 장소 확보) Phase A — 카카오 로컬 API 수집분을 기존
--       attraction 테이블에 함께 적재하기 위한 최소 스키마 확장.
--
-- 변경 내용:
--   1) source 컬럼 추가 — 출처 구분. 기존 행은 DEFAULT 'TOURAPI'로 자연 정합.
--   2) external_id 컬럼 추가 — 카카오 place id. TOURAPI 행은 NULL.
--   3) content_id NULL 허용 — 카카오 행은 TourAPI contentid가 없음.
--      (기존 uq_attraction_content_id는 MySQL이 다중 NULL을 허용하므로 그대로 유지)
--   4) UNIQUE (source, external_id) 추가 — 카카오 업서트 dedup 키.
--
-- 주의: attraction_detail_* 는 content_id(FK) 기반이라 카카오 행(content_id=NULL)은
--       상세정보를 갖지 않는다. FK 정합성에는 영향 없음.
-- =============================================

ALTER TABLE attraction
    ADD COLUMN source ENUM('TOURAPI','KAKAO') NOT NULL DEFAULT 'TOURAPI'
        COMMENT '출처: 한국관광공사 TourAPI | 카카오 로컬' AFTER id,
    ADD COLUMN external_id VARCHAR(30) NULL
        COMMENT '카카오 place id (source=KAKAO). TOURAPI 행은 NULL' AFTER source,
    MODIFY COLUMN content_id VARCHAR(20) NULL
        COMMENT 'TourAPI contentid (숫자 문자열). source=KAKAO 행은 NULL',
    ADD UNIQUE KEY uq_attraction_source_ext (source, external_id);
