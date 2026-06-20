-- ============================================================
-- migration: attach 테이블 신설 + post 소프트 딜리트
-- 작성일: 2026-06-14 / 작성자: 전진
-- ※ 이 파일은 MR#5(mypage)에서 최초 작성된 버전으로 히스토리 보존용.
--   컬럼 설계가 일부 변경되었으므로 최신 스키마는 migration_attach.sql 또는 schema.sql을 참고.
--   주요 차이: host_name·mimetype·host_path NOT NULL → NULL 허용 (Spring API null 반환 대응),
--              name/host_name VARCHAR(128) → VARCHAR(255), target VARCHAR(32) → VARCHAR(20)
-- ============================================================

-- 1. attach 테이블 생성
CREATE TABLE IF NOT EXISTS attach (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '첨부파일 고유 번호',
    name        VARCHAR(128) NOT NULL COMMENT '서버 저장 파일명 (UUID 기반)',
    host_name   VARCHAR(128) NOT NULL COMMENT '원본 파일명',
    size        BIGINT       NOT NULL DEFAULT 0 COMMENT '파일 크기 (bytes)',
    mimetype    VARCHAR(128) NOT NULL COMMENT 'MIME 타입 (image/jpeg 등)',
    host_path   VARCHAR(512) NOT NULL COMMENT '서버 내 저장 경로',
    target      VARCHAR(32)  NOT NULL COMMENT '첨부 대상 구분 (profile / post / post_draft)',
    target_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '첨부 대상의 레코드 ID (post_draft는 0)',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '등록일시',
    INDEX idx_attach_target (target, target_id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='파일 첨부 메타데이터';

-- 2. post 소프트 딜리트 컬럼 추가
ALTER TABLE post
    ADD COLUMN deleted_at DATETIME NULL COMMENT '삭제일시 (NULL=정상, non-NULL=소프트 딜리트됨)';

-- 확인 쿼리
-- SHOW CREATE TABLE attach;
-- DESCRIBE post;
