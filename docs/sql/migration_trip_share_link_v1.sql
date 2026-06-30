-- ============================================================
-- 일정 공유 링크: 접근 레벨 + 랜덤 토큰
-- elegant plan: 일정별 공개/비공개(조회·편집) + 추측 불가 토큰 URL
--   share_access: PRIVATE(소유자·초대자만) / VIEW(링크 조회) / EDIT(링크 편집)
--   share_token : 랜덤 URL-safe 토큰(첫 공개 시 생성). 공유 URL = /plan/{id}?s={token}
-- 코드보다 먼저 적용.
-- ============================================================

ALTER TABLE trip
    ADD COLUMN share_access ENUM('PRIVATE','VIEW','EDIT') NOT NULL DEFAULT 'PRIVATE'
        COMMENT '링크 접근 레벨' AFTER is_public,
    ADD COLUMN share_token  CHAR(22) NULL UNIQUE
        COMMENT '공유 링크 랜덤 토큰(URL-safe). NULL=미생성' AFTER share_access;
