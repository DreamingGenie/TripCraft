-- transit_cache: path_index 컬럼 추가 + UNIQUE KEY 변경 (경로 전체 저장)
-- 실행 전 기존 transit_cache 데이터 유실 주의

ALTER TABLE transit_cache
    ADD COLUMN path_index TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'ODsay 경로 순서 (0-based)',
    DROP INDEX uq_transit,
    ADD UNIQUE KEY uq_transit (from_attraction_id, to_attraction_id, departure_hour, path_index);
