-- ============================================================
-- 내 장소(커스텀 장소): 개인 장소 + 일정 후보 커스텀 지원
-- elegant plan §모델
--   member_place: 회원별 재사용 커스텀 장소
--   trip_candidate: attraction_id NULL 허용 + 커스텀 필드 + source 'CUSTOM'
-- 코드보다 먼저 적용.
-- ============================================================

CREATE TABLE member_place (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NOT NULL COMMENT '소유 회원 FK',
    name       VARCHAR(100) NOT NULL COMMENT '장소명',
    category   VARCHAR(20)  NOT NULL COMMENT '분류(관광지·문화시설·레포츠·숙박·쇼핑·음식점)',
    address    VARCHAR(255) NULL     COMMENT '주소',
    latitude   DECIMAL(10,7) NULL,
    longitude  DECIMAL(10,7) NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_member_place_member (member_id),
    FOREIGN KEY fk_member_place_member (member_id) REFERENCES member(id) ON DELETE CASCADE
) COMMENT='회원 개인 커스텀 장소(재사용)' DEFAULT CHARSET = utf8mb4;

ALTER TABLE trip_candidate
    MODIFY COLUMN attraction_id BIGINT NULL COMMENT '관광지 FK (커스텀 장소면 NULL)',
    MODIFY COLUMN city_code TINYINT NULL COMMENT '도시 분류 (커스텀이면 NULL)',
    MODIFY COLUMN source ENUM('MANUAL','FAVORITE','CUSTOM') NOT NULL DEFAULT 'MANUAL'
        COMMENT '추가 경로: MANUAL/FAVORITE/CUSTOM(커스텀 장소)',
    ADD COLUMN place_name     VARCHAR(100)  NULL COMMENT '커스텀 장소명'   AFTER attraction_id,
    ADD COLUMN place_category VARCHAR(20)   NULL COMMENT '커스텀 분류'     AFTER place_name,
    ADD COLUMN place_address  VARCHAR(255)  NULL COMMENT '커스텀 주소'     AFTER place_category,
    ADD COLUMN place_lat      DECIMAL(10,7) NULL COMMENT '커스텀 위도'     AFTER place_address,
    ADD COLUMN place_lng      DECIMAL(10,7) NULL COMMENT '커스텀 경도'     AFTER place_lat;
