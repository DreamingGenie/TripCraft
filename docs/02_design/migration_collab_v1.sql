-- ============================================================
-- 일정 협업자 테이블 신설
-- collab_plan.md §9 DB 스키마 변경 계획
-- ============================================================

CREATE TABLE IF NOT EXISTS trip_collaborator (
    id         BIGINT                   NOT NULL AUTO_INCREMENT,
    trip_id    BIGINT                   NOT NULL,
    member_id  BIGINT                   NOT NULL,
    role       ENUM('EDITOR','VIEWER')  NOT NULL DEFAULT 'EDITOR',
    invited_at TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_collaborator (trip_id, member_id),
    FOREIGN KEY (trip_id)   REFERENCES trip(id)   ON DELETE CASCADE,
    FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE
) COMMENT='일정 협업자. 소유자(trip.member_id)와 별도 관리. 역할: EDITOR(조회+편집) / VIEWER(조회만).'
  DEFAULT CHARSET = utf8mb4;
