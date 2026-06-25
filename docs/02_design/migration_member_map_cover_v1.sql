-- migration_member_map_cover_v1.sql
-- 마이페이지 방문 지도: 지역별 표지 사진 + crop(초점/확대) 저장.
-- 표지는 여행이야기의 임의 사진 선택 또는 직접 업로드 → 지도 전용 사본으로 복사 저장.
-- 출처 글 삭제(SET NULL)와 무관하게 지도 사진 유지. 미선택 지역은 앱 레이어에서 최신 후기로 표시.
-- schema.sql 정본에 통합 반영됨(17. member_map_cover).

CREATE TABLE member_map_cover (
    member_id      BIGINT       NOT NULL COMMENT '회원 FK',
    region_level   VARCHAR(10)  NOT NULL DEFAULT 'SIDO' COMMENT '지역 레벨 (SIDO | SIGUNGU)',
    region_code    SMALLINT     NOT NULL COMMENT '지역 코드 (SIDO=sido_code)',
    image_url      VARCHAR(500) NOT NULL COMMENT '지도 전용 복사본 이미지 URL (/uploads/images/...)',
    host_path      VARCHAR(500) NULL     COMMENT '복사본 파일 절대경로 (교체/삭제 시 파일 정리용)',
    source_post_id BIGINT       NULL     COMMENT '출처 여행이야기 (글로 이동 링크용, 직접 업로드면 NULL)',
    focus_x        DECIMAL(5,2) NOT NULL DEFAULT 50.00 COMMENT 'crop 초점 X(%) — CSS object-position',
    focus_y        DECIMAL(5,2) NOT NULL DEFAULT 50.00 COMMENT 'crop 초점 Y(%)',
    zoom           DECIMAL(4,2) NOT NULL DEFAULT 1.00  COMMENT 'crop 확대 배율(>=1)',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id, region_level, region_code),
    INDEX idx_map_cover_post (source_post_id),
    FOREIGN KEY fk_map_cover_member (member_id)      REFERENCES member(id) ON DELETE CASCADE,
    FOREIGN KEY fk_map_cover_post   (source_post_id) REFERENCES post(id)   ON DELETE SET NULL
) COMMENT='방문 지도 지역별 표지 사진(지도 전용 사본) + crop 설정. 회원당 지역당 1건'
  DEFAULT CHARSET = utf8mb4;
