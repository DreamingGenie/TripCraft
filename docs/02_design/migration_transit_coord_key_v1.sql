-- ============================================================
-- 이동시간 캐시: attraction id 키 → 좌표(route_key) 키로 통일
-- 커스텀 장소도 attraction 블록과 동일하게 캐시·기능 사용하기 위함.
-- route_key = "fLat,fLng>tLat,tLng@hour#mode" (좌표 5자리 반올림)
-- 코드보다 먼저 적용. 기존 attraction-키 캐시는 무효화(좌표키로 재생성).
-- ============================================================

ALTER TABLE transit_cache DROP FOREIGN KEY fk_transit_from;
ALTER TABLE transit_cache DROP FOREIGN KEY fk_transit_to;

ALTER TABLE transit_cache
    DROP KEY uq_transit,
    MODIFY from_attraction_id BIGINT NULL COMMENT '(미사용, 좌표키로 전환)',
    MODIFY to_attraction_id   BIGINT NULL COMMENT '(미사용, 좌표키로 전환)',
    ADD COLUMN route_key VARCHAR(160) NULL COMMENT '좌표 기반 캐시 키' AFTER id,
    ADD UNIQUE KEY uq_transit_route (route_key);

DELETE FROM transit_cache;
