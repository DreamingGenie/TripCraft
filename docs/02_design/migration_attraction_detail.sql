-- =============================================
-- migration_attraction_detail.sql
-- attraction 테이블 재설계 + detail 관련 테이블 신규 생성
--
-- 변경 내용:
--   attraction: overview·homepage·detail_info·detail_synced_at 제거,
--               areaBasedList2 누락 필드 추가
--   신규: attraction_detail_common / _intro / _image / _info / _room
-- =============================================

-- 1. attraction 테이블 변경
ALTER TABLE attraction
    -- 제거: detail 관련 컬럼 (별도 테이블로 분리)
    DROP COLUMN overview,
    DROP COLUMN homepage,
    DROP COLUMN detail_info,
    DROP COLUMN detail_synced_at,
    -- 추가: areaBasedList2 누락 필드
    ADD COLUMN zipcode          VARCHAR(10)  NULL COMMENT '우편번호'          AFTER addr2,
    ADD COLUMN first_image2     VARCHAR(500) NULL COMMENT '대표 이미지2 URL'  AFTER first_image,
    ADD COLUMN mlevel           TINYINT      NULL COMMENT '지도 레벨'          AFTER first_image2,
    ADD COLUMN cat1             VARCHAR(10)  NULL COMMENT '대분류 코드'        AFTER mlevel,
    ADD COLUMN cat2             VARCHAR(10)  NULL COMMENT '중분류 코드'        AFTER cat1,
    ADD COLUMN cat3             VARCHAR(10)  NULL COMMENT '소분류 코드'        AFTER cat2,
    ADD COLUMN l_dong_regn_cd   VARCHAR(20)  NULL COMMENT '법정동 지역 코드'   AFTER cat3,
    ADD COLUMN l_dong_signgu_cd VARCHAR(20)  NULL COMMENT '법정동 시군구 코드' AFTER l_dong_regn_cd,
    ADD COLUMN lcls_systm1      VARCHAR(200) NULL COMMENT '분류체계1'          AFTER l_dong_signgu_cd,
    ADD COLUMN lcls_systm2      VARCHAR(200) NULL COMMENT '분류체계2'          AFTER lcls_systm1,
    ADD COLUMN lcls_systm3      VARCHAR(200) NULL COMMENT '분류체계3'          AFTER lcls_systm2,
    ADD COLUMN cpyrht_div_cd    VARCHAR(10)  NULL COMMENT '저작권 구분 코드'   AFTER lcls_systm3;

-- 2. 공통 상세 (detailCommon2)
CREATE TABLE attraction_detail_common (
    content_id VARCHAR(20)  NOT NULL COMMENT 'TourAPI contentid (FK)',
    overview   TEXT         NULL     COMMENT '장소 소개글',
    homepage   VARCHAR(500) NULL     COMMENT '홈페이지 URL (HTML 태그 포함 가능)',
    telname    VARCHAR(100) NULL     COMMENT '전화번호 명칭',
    synced_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                     ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (content_id),
    FOREIGN KEY fk_detail_common (content_id) REFERENCES attraction(content_id) ON DELETE CASCADE
) COMMENT='detailCommon2 추가 필드 (overview·homepage·telname)'
  DEFAULT CHARSET = utf8mb4;

-- 3. 유형별 소개 (detailIntro2)
CREATE TABLE attraction_detail_intro (
    content_id      VARCHAR(20) NOT NULL COMMENT 'TourAPI contentid (FK)',
    content_type_id TINYINT     NOT NULL COMMENT 'contenttypeid (JSON 해석 기준)',
    intro_data      JSON        NULL     COMMENT 'contenttypeid별 소개 필드 전체 JSON',
    synced_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
                                         ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (content_id),
    FOREIGN KEY fk_detail_intro (content_id) REFERENCES attraction(content_id) ON DELETE CASCADE
) COMMENT='detailIntro2 응답 전체 JSON 저장. 검색 조건 필요 시 컬럼 추가 마이그레이션'
  DEFAULT CHARSET = utf8mb4;

-- 4. 이미지 (detailImage2)
CREATE TABLE attraction_detail_image (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    content_id    VARCHAR(20)  NOT NULL COMMENT 'TourAPI contentid (FK)',
    serialnum     VARCHAR(10)  NULL     COMMENT '이미지 순번',
    originimgurl  VARCHAR(500) NULL     COMMENT '원본 이미지 URL',
    smallimageurl VARCHAR(500) NULL     COMMENT '썸네일 이미지 URL',
    imgname       VARCHAR(200) NULL     COMMENT '이미지 파일명',
    cpyrht_div_cd VARCHAR(10)  NULL     COMMENT '저작권 구분 코드',
    PRIMARY KEY (id),
    INDEX idx_image_content (content_id),
    FOREIGN KEY fk_detail_image (content_id) REFERENCES attraction(content_id) ON DELETE CASCADE
) COMMENT='detailImage2 이미지 목록'
  DEFAULT CHARSET = utf8mb4;

-- 5. 이용 안내 (detailInfo2)
CREATE TABLE attraction_detail_info (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    content_id        VARCHAR(20)  NOT NULL COMMENT 'TourAPI contentid (FK)',
    serialnum         VARCHAR(10)  NULL     COMMENT '항목 순번',
    fldgubun          VARCHAR(10)  NULL     COMMENT '필드 구분',
    infoname          VARCHAR(200) NULL     COMMENT '안내 항목명',
    infotext          TEXT         NULL     COMMENT '안내 내용',
    subcontentid      VARCHAR(20)  NULL     COMMENT '서브 콘텐츠 ID (여행코스)',
    subdetailalt      VARCHAR(200) NULL,
    subdetailimg      VARCHAR(500) NULL,
    subdetailoverview TEXT         NULL,
    subname           VARCHAR(200) NULL,
    subnum            VARCHAR(10)  NULL,
    room_data         JSON         NULL     COMMENT '숙박(contenttypeid=32) 객실 정보 JSON (room* 필드)',
    PRIMARY KEY (id),
    INDEX idx_info_content (content_id),
    FOREIGN KEY fk_detail_info (content_id) REFERENCES attraction(content_id) ON DELETE CASCADE
) COMMENT='detailInfo2 이용 안내 항목. 숙박 객실 정보는 room_data JSON 컬럼에 저장'
  DEFAULT CHARSET = utf8mb4;

-- 6. system_config 테이블 생성 (없는 경우)
CREATE TABLE IF NOT EXISTS system_config (
    config_key   VARCHAR(100) NOT NULL COMMENT '설정 키',
    config_value TEXT         NULL     COMMENT '설정 값',
    description  VARCHAR(500) NULL     COMMENT '설명',
    PRIMARY KEY (config_key)
) COMMENT='시스템 설정 키-값 저장소'
  DEFAULT CHARSET = utf8mb4;

-- 7. TourAPI 일일 호출 한도 설정
INSERT INTO system_config (config_key, config_value, description) VALUES
('tour_api_daily_limit', '500',  'TourAPI 일일 최대 호출 횟수 (배치+실시간 합산)'),
('tour_api_call_date',   '',     'TourAPI 호출 카운트 기준 날짜 (YYYY-MM-DD)'),
('tour_api_call_count',  '0',    'TourAPI 오늘 누적 호출 횟수')
ON DUPLICATE KEY UPDATE description = VALUES(description);
