-- transit_cache 재설계: ODsay 응답 전체 저장 + trip_block에 표시용 transit 컬럼 추가
-- 실행 전 기존 transit_cache 데이터 유실 주의

DROP TABLE IF EXISTS transit_cache;

CREATE TABLE transit_cache (
    id                 BIGINT AUTO_INCREMENT,
    from_attraction_id BIGINT       NOT NULL,
    to_attraction_id   BIGINT       NOT NULL,
    departure_hour     TINYINT      NOT NULL,
    duration_minutes   SMALLINT     NOT NULL,
    transport_mode     VARCHAR(20)  NOT NULL,
    transfer_count     TINYINT UNSIGNED NULL,
    fare               INT UNSIGNED NULL,
    total_distance_m   INT UNSIGNED NULL,
    total_walk_m       INT UNSIGNED NULL,
    cached_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_transit (from_attraction_id, to_attraction_id, departure_hour),
    CONSTRAINT fk_transit_from FOREIGN KEY (from_attraction_id) REFERENCES attraction(id) ON DELETE CASCADE,
    CONSTRAINT fk_transit_to   FOREIGN KEY (to_attraction_id)   REFERENCES attraction(id) ON DELETE CASCADE
);

ALTER TABLE trip_block
    ADD COLUMN transit_duration_minutes SMALLINT NULL COMMENT '이전 블록→이 블록 이동 시간(분)',
    ADD COLUMN transit_mode             VARCHAR(20) NULL COMMENT '이동 수단 (BUS/SUBWAY 등)';
