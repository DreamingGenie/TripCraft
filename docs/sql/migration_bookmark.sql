-- ============================================================
-- migration: post_bookmark 테이블 신설
-- 작성일: 2026-06-14
-- 작성자: 전진
-- ============================================================

CREATE TABLE IF NOT EXISTS post_bookmark (
    member_id  BIGINT   NOT NULL COMMENT '북마크한 회원 FK',
    post_id    BIGINT   NOT NULL COMMENT '북마크된 게시글 FK',
    created_at DATETIME NOT NULL DEFAULT NOW() COMMENT '북마크 일시',
    PRIMARY KEY (member_id, post_id),
    FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    FOREIGN KEY (post_id)   REFERENCES post (id)
    -- post_id FK에 ON DELETE CASCADE 없음: 글 삭제 시 북마크 보존 (소프트 딜리트 연동)
);
