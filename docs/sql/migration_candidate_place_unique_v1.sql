-- ============================================================
-- 커스텀 장소 좌표 기반 중복 방지
-- trip_candidate 에 (trip_id, place_lat, place_lng) UNIQUE 추가.
-- attraction 후보는 place_lat/place_lng 가 NULL → MySQL 은 UNIQUE 에서 NULL 을
-- 서로 다른 값으로 취급하므로 attraction 다중 행과 충돌하지 않는다.
-- 앱 레이어(addCustomCandidate/addCandidateFromMyPlace)에서도 사전 체크 + 409.
-- ============================================================

-- (선행) 기존 중복 커스텀 후보가 있으면 ALTER 가 실패하므로 정리 필요.
-- 같은 (trip_id, place_lat, place_lng) 중 최소 id 만 남기고 삭제:
DELETE c1 FROM trip_candidate c1
JOIN trip_candidate c2
  ON c1.trip_id = c2.trip_id
 AND c1.place_lat = c2.place_lat
 AND c1.place_lng = c2.place_lng
 AND c1.place_lat IS NOT NULL
 AND c1.id > c2.id;

ALTER TABLE trip_candidate
    ADD UNIQUE KEY uq_candidate_place (trip_id, place_lat, place_lng);
