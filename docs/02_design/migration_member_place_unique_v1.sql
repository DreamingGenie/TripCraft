-- ============================================================
-- 내 장소(member_place) 좌표 기반 중복 방지
-- 같은 회원이 동일 좌표를 중복 등록하지 못하도록 UNIQUE 추가.
-- 좌표 NULL 행은 MySQL UNIQUE 에서 서로 다른 값으로 취급되어 충돌하지 않는다.
-- ============================================================

-- (선행) 기존 중복 정리 — 같은 (member_id, latitude, longitude) 중 최소 id 만 남김:
DELETE m1 FROM member_place m1
JOIN member_place m2
  ON m1.member_id = m2.member_id
 AND m1.latitude  = m2.latitude
 AND m1.longitude = m2.longitude
 AND m1.latitude IS NOT NULL
 AND m1.id > m2.id;

ALTER TABLE member_place
    ADD UNIQUE KEY uq_member_place (member_id, latitude, longitude);
