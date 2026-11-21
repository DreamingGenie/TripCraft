-- 글 목록 정렬 인덱스 (2026-11-21)
-- 최신순(created_at DESC)·인기순(like_count DESC, created_at DESC) 목록이 인덱스 없이 전체 행을 정렬하고 있었다.
-- 측정(MySQL 8.0, 게시글 50,000건, LIMIT 10): 최신순 13.1ms -> 0.06ms, 인기순 14.4ms -> 0.05ms.
-- 근거: docs/features/perf-profile-image.md "2026-11-21 실측"
ALTER TABLE post
    ADD INDEX idx_post_created_at (created_at),
    ADD INDEX idx_post_popular    (like_count, created_at);
