-- ============================================================
-- migration: attach 테이블 신설 + post 소프트 딜리트
-- 작성일: 2026-06-14
-- 작성자: 전진
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
