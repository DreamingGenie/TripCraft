-- ============================================================
-- 회원 소셜 로그인(카카오 OAuth) 컬럼 추가
-- 기존 DB에 1회 적용. (신규 셋업은 schema.sql에 이미 반영됨)
--
-- - social_provider/social_id: 소셜 제공자 + 제공자 내 고유 ID
-- - email/password NULL 허용: 카카오가 이메일 제공 동의를 거부할 수 있고,
--   소셜 전용 계정은 비밀번호가 없음
-- ============================================================

ALTER TABLE member
    ADD COLUMN social_provider VARCHAR(20)  NULL COMMENT '소셜 로그인 제공자 (kakao 등)',
    ADD COLUMN social_id       VARCHAR(100) NULL COMMENT '소셜 제공자의 사용자 고유 ID';

-- 이메일 미제공/비밀번호 없는 소셜 계정 허용
ALTER TABLE member
    MODIFY COLUMN email    VARCHAR(100) NULL COMMENT '로그인 이메일 (소셜 계정은 NULL 가능)',
    MODIFY COLUMN password VARCHAR(255) NULL COMMENT 'BCrypt 해시 (소셜 전용 계정은 NULL)';

CREATE UNIQUE INDEX uq_member_social ON member(social_provider, social_id);
