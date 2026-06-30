-- transit_cache: path_index 제거, UNIQUE KEY 원복 (단일 row + paths JSON 배열 저장 방식)
ALTER TABLE transit_cache
    DROP INDEX uq_transit,
    DROP COLUMN path_index,
    ADD UNIQUE KEY uq_transit (from_attraction_id, to_attraction_id, departure_hour);
