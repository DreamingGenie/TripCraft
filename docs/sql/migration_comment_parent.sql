-- =============================================
-- Migration: post_comment 대댓글 지원
-- 적용 버전: schema v0.3 → v0.4
-- 작성일: 2026-06-09
-- 작업자: 전진·송정기
--
-- 변경 내용:
--   post_comment 테이블에 parent_id 컬럼 추가.
--   NULL = 최상위 댓글, non-NULL = 대댓글.
--   1단계 중첩만 허용 (대댓글의 대댓글 금지 — 앱 레이어에서 검증).
--   부모 댓글 삭제 시 ON DELETE CASCADE로 대댓글도 함께 삭제.
-- =============================================

ALTER TABLE post_comment
    ADD COLUMN parent_id BIGINT NULL COMMENT '부모 댓글 FK (NULL=최상위, non-NULL=대댓글)' AFTER member_id,
    ADD CONSTRAINT fk_comment_parent
        FOREIGN KEY (parent_id) REFERENCES post_comment (id) ON DELETE CASCADE;
