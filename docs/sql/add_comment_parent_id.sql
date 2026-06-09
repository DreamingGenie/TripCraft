-- [임시 파일 — 정식 migration은 docs/02_design/migration_comment_parent.sql 참조]
-- 대댓글 기능: post_comment 테이블에 parent_id 컬럼 추가
-- 실행 전 DB 백업 권장

ALTER TABLE post_comment
    ADD COLUMN parent_id BIGINT NULL AFTER member_id,
    ADD CONSTRAINT fk_comment_parent
        FOREIGN KEY (parent_id) REFERENCES post_comment (id) ON DELETE CASCADE;
