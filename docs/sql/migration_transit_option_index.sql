-- trip_block에 선택된 이동수단 옵션 인덱스 저장
-- 대중교통: selectPath pathIndex / 자동차: driving option index (0~3)
ALTER TABLE trip_block
    ADD COLUMN transit_option_index INT NULL
        COMMENT '선택된 경로 인덱스 (대중교통 pathIndex 또는 자동차 옵션 0~3)'
        AFTER transit_mode;
