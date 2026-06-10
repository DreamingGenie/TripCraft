-- ODsay loadLane 결과를 노선 단위로 캐싱. 노선 형상은 자주 바뀌지 않으므로 영구 보관.
CREATE TABLE lane_polyline (
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
    map_object_key VARCHAR(250) NOT NULL,
    route_coords   MEDIUMTEXT   NULL,          -- 파싱된 좌표 JSON [[lng,lat],...], null이면 API 결과 없음
    raw_response   MEDIUMTEXT   NULL,          -- ODsay loadLane 원본 응답 JSON
    created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_map_object_key (map_object_key)
);
