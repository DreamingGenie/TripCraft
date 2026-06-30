-- =============================================
-- transit_cache 테이블 T-Map API 대응 마이그레이션
-- 실행 전: 기존 ODsay 데이터 전부 무효 → TRUNCATE 후 재설계
-- =============================================

-- 1. 기존 데이터 및 테이블 제거
DROP TABLE IF EXISTS transit_cache;

-- 2. T-Map 응답 구조에 맞춘 재생성
--    캐시 키: (from_attraction_id, to_attraction_id, departure_hour)
--    transport_type 제거 — T-Map transit API는 수단 필터 없음
--    transport_mode VARCHAR(20) — T-Map 모드 BUS/SUBWAY/RAIL/EXPRESSBUS 등 자유롭게 수용
CREATE TABLE transit_cache (
    id                 BIGINT           NOT NULL AUTO_INCREMENT,
    from_attraction_id BIGINT           NOT NULL COMMENT '출발지 FK',
    to_attraction_id   BIGINT           NOT NULL COMMENT '도착지 FK',
    departure_hour     TINYINT          NOT NULL COMMENT '출발 시(0~23)',
    duration_minutes   SMALLINT         NOT NULL COMMENT '소요 시간(분)',
    transport_mode     VARCHAR(20)      NOT NULL COMMENT 'T-Map 주요 이동 수단 (BUS/SUBWAY/RAIL 등)',
    transfer_count     TINYINT UNSIGNED NULL     COMMENT '환승 횟수',
    fare               INT UNSIGNED     NULL     COMMENT '요금(원)',
    total_distance_m   INT UNSIGNED     NULL     COMMENT '총 이동 거리(m)',
    cached_at          TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_transit (from_attraction_id, to_attraction_id, departure_hour),
    CONSTRAINT fk_transit_from FOREIGN KEY (from_attraction_id) REFERENCES attraction(id) ON DELETE CASCADE,
    CONSTRAINT fk_transit_to   FOREIGN KEY (to_attraction_id)   REFERENCES attraction(id) ON DELETE CASCADE
) COMMENT='T-Map 대중교통 API 이동 시간 캐시. 전국 대중교통(KTX·버스·지하철) 지원'
  DEFAULT CHARSET = utf8mb4;
