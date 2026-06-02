-- ---------------------------------------------
-- Migration: post_comment 테이블 추가
-- ---------------------------------------------
CREATE TABLE post_comment (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    post_id    BIGINT        NOT NULL COMMENT '게시글 FK',
    member_id  BIGINT        NULL     COMMENT '작성자 FK (탈퇴 시 NULL → 탈퇴한 사용자 표시, 댓글 보존)',
    content    VARCHAR(1000) NOT NULL COMMENT '댓글 내용 (최대 1000자)',
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_comment_post   (post_id),
    INDEX idx_comment_member (member_id),
    FOREIGN KEY fk_comment_post   (post_id)   REFERENCES post(id)   ON DELETE CASCADE,
    FOREIGN KEY fk_comment_member (member_id) REFERENCES member(id) ON DELETE SET NULL
) COMMENT='게시글 댓글. 게시글 삭제 시 CASCADE 삭제. 탈퇴 시 댓글 내용 보존'
  DEFAULT CHARSET = utf8mb4;
