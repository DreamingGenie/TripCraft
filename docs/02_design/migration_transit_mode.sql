-- ================================================================
-- TripCraft Migration: 이동수단 모드 확장 (v0.2 → v0.3)
-- 실행 순서 주의: ADD KEY 먼저 → DROP INDEX (FK 제약 지원 유지)
-- ================================================================

-- Step 1. transit_cache 컬럼 추가
ALTER TABLE transit_cache
    ADD COLUMN request_mode  VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC_TRANSIT'
                             COMMENT '요청 이동수단 모드 (PUBLIC_TRANSIT·DRIVING·WALKING)',
    ADD COLUMN taxi_fare     INT UNSIGNED NULL
                             COMMENT 'T Map 택시 예상 요금(원) — DRIVING 모드',
    ADD COLUMN route_coords  MEDIUMTEXT   NULL
                             COMMENT '경로 좌표 JSON ([lng,lat] 배열) — T Map GeoJSON LineString';

-- Step 2. 새 UNIQUE KEY 추가 (request_mode 포함) — FK 인덱스 교체 전 먼저 추가
ALTER TABLE transit_cache
    ADD UNIQUE KEY uq_transit_v3 (from_attraction_id, to_attraction_id, departure_hour, request_mode);

-- Step 3. 기존 UNIQUE KEY 제거 — Step 2 완료 후에야 삭제 가능 (FK가 uq_transit_v3로 이전됨)
ALTER TABLE transit_cache
    DROP INDEX uq_transit;

-- Step 4. 인덱스명 통일 (선택 사항 — 코드상 키 이름을 참조하는 곳 없으면 생략 가능)
ALTER TABLE transit_cache
    RENAME INDEX uq_transit_v3 TO uq_transit;

-- Step 5. trip 테이블 기본 이동수단 컬럼 추가
ALTER TABLE trip
    ADD COLUMN default_transit_mode VARCHAR(20) NOT NULL DEFAULT 'PUBLIC_TRANSIT'
               COMMENT '신규 블록 배치 시 기본 이동수단 (PUBLIC_TRANSIT·DRIVING·WALKING)';
