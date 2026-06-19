-- attach 테이블: 업로드 파일 생명주기 관리
-- target: 'profile' | 'post' | 'post_draft'
-- target_id: NULL = 게시글 등록 전 임시 상태 (post_draft)
-- ※ master 브랜치에 feature/mypage가 먼저 머지된 경우 이미 존재할 수 있으므로 IF NOT EXISTS 사용

CREATE TABLE IF NOT EXISTS attach (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    target     VARCHAR(20)  NOT NULL COMMENT 'profile | post | post_draft',
    target_id  BIGINT       NULL     COMMENT 'NULL = draft 상태',
    name       VARCHAR(255) NOT NULL COMMENT '파일명 (UUID.ext)',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_attach_target (target, target_id)
);
