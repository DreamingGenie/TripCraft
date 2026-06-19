-- attach 테이블: 업로드 파일 생명주기 관리
-- target: 'profile' | 'post' | 'post_draft'
-- post_draft의 target_id = memberId (업로더 식별용), 글 등록 시 'post' + postId로 변경
-- ※ master 브랜치에 feature/mypage가 먼저 머지된 경우 이미 존재할 수 있으므로 IF NOT EXISTS 사용

CREATE TABLE IF NOT EXISTS attach (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(255) NOT NULL COMMENT '저장 파일명 (UUID.ext)',
    host_name  VARCHAR(255) NULL     COMMENT '원본 파일명',
    size       BIGINT       NOT NULL DEFAULT 0,
    mimetype   VARCHAR(100) NULL,
    host_path  VARCHAR(500) NULL     COMMENT '서버 절대 경로 (파일 삭제 시 사용)',
    target     VARCHAR(20)  NOT NULL COMMENT 'profile | post | post_draft',
    target_id  BIGINT       NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_attach_target (target, target_id)
);
