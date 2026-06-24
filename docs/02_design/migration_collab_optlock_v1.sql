-- ============================================================
-- 협업 편집 낙관적 락(Optimistic Lock): trip_block.version
-- 동시 편집 시 "같은 블록"의 교차 덮어쓰기(Last-Write-Wins) 데이터 유실 방지.
--   - 사용자 편집(이동/리사이즈) UPDATE 는 WHERE id=? AND version=? 로 수행하고 version+1.
--   - 매칭 0행이면 그 사이 다른 사용자가 먼저 수정한 것 → 409 CONFLICT → 클라이언트 재조회.
--   - transit 재계산은 version 을 건드리지 않는 별도 UPDATE 를 써서 오탐(false conflict)을 막는다.
-- version 은 "같은 row"에만 작용하므로 무관한 블록 편집·transit 재계산과는 충돌하지 않는다.
-- 코드보다 먼저 적용.
-- ============================================================

ALTER TABLE trip_block
    ADD COLUMN version INT NOT NULL DEFAULT 0
        COMMENT '낙관적 락 버전. 사용자 편집 시 +1. transit 재계산은 미변경' AFTER transit_option_index;
