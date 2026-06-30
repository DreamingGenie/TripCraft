-- TripCraft 일정 테스트 데이터
-- 기존 게시글(15, 16, 18)에 일정을 연결해 일정 가져오기 기능 테스트용
-- 실행 전제: community_test_data.sql 실행 완료 (member 3,4 / post 15,16,18 존재)

-- Trip 1: 부산 2박 3일 (김지연 member_id=3 / post 16)
INSERT INTO trip (member_id, title, start_date, end_date, member_count, default_transit_mode, is_public)
VALUES (3, '부산 2박 3일 핵심 코스', '2026-07-10', '2026-07-12', 2, 'PUBLIC_TRANSIT', 1);
SET @trip1 = LAST_INSERT_ID();

INSERT INTO trip_candidate (trip_id, attraction_id, city_code, source) VALUES (@trip1, 4641, 6, 'MANUAL');
INSERT INTO trip_candidate (trip_id, attraction_id, city_code, source) VALUES (@trip1, 4640, 6, 'MANUAL');
INSERT INTO trip_candidate (trip_id, attraction_id, city_code, source) VALUES (@trip1, 4644, 6, 'MANUAL');
INSERT INTO trip_candidate (trip_id, attraction_id, city_code, source) VALUES (@trip1, 4658, 6, 'MANUAL');
INSERT INTO trip_candidate (trip_id, attraction_id, city_code, source) VALUES (@trip1, 4639, 6, 'MANUAL');

SET @c1_1 = (SELECT id FROM trip_candidate WHERE trip_id = @trip1 AND attraction_id = 4641);
SET @c1_2 = (SELECT id FROM trip_candidate WHERE trip_id = @trip1 AND attraction_id = 4640);
SET @c1_3 = (SELECT id FROM trip_candidate WHERE trip_id = @trip1 AND attraction_id = 4644);
SET @c1_4 = (SELECT id FROM trip_candidate WHERE trip_id = @trip1 AND attraction_id = 4658);
SET @c1_5 = (SELECT id FROM trip_candidate WHERE trip_id = @trip1 AND attraction_id = 4639);

INSERT INTO trip_block (candidate_id, trip_date, display_order, start_time, duration_minutes) VALUES (@c1_1, '2026-07-10', 1, '10:00:00', 120);
INSERT INTO trip_block (candidate_id, trip_date, display_order, start_time, duration_minutes) VALUES (@c1_2, '2026-07-10', 2, '13:30:00', 90);
INSERT INTO trip_block (candidate_id, trip_date, display_order, start_time, duration_minutes) VALUES (@c1_3, '2026-07-11', 1, '11:00:00', 120);
INSERT INTO trip_block (candidate_id, trip_date, display_order, start_time, duration_minutes) VALUES (@c1_4, '2026-07-11', 2, '15:00:00', 90);
INSERT INTO trip_block (candidate_id, trip_date, display_order, start_time, duration_minutes) VALUES (@c1_5, '2026-07-12', 1, '10:30:00', 120);

UPDATE post SET trip_id = @trip1 WHERE id = 16;


-- Trip 2: 강릉 1박 2일 (박민수 member_id=4 / post 18)
INSERT INTO trip (member_id, title, start_date, end_date, member_count, default_transit_mode, is_public)
VALUES (4, '강릉 1박 2일 커피 & 바다', '2026-08-15', '2026-08-16', 2, 'DRIVING', 1);
SET @trip2 = LAST_INSERT_ID();

INSERT INTO trip_candidate (trip_id, attraction_id, city_code, source) VALUES (@trip2, 10815, 32, 'MANUAL');
INSERT INTO trip_candidate (trip_id, attraction_id, city_code, source) VALUES (@trip2, 10822, 32, 'MANUAL');
INSERT INTO trip_candidate (trip_id, attraction_id, city_code, source) VALUES (@trip2, 10810, 32, 'MANUAL');
INSERT INTO trip_candidate (trip_id, attraction_id, city_code, source) VALUES (@trip2, 10807, 32, 'MANUAL');
INSERT INTO trip_candidate (trip_id, attraction_id, city_code, source) VALUES (@trip2, 10816, 32, 'MANUAL');

SET @c2_1 = (SELECT id FROM trip_candidate WHERE trip_id = @trip2 AND attraction_id = 10815);
SET @c2_2 = (SELECT id FROM trip_candidate WHERE trip_id = @trip2 AND attraction_id = 10822);
SET @c2_3 = (SELECT id FROM trip_candidate WHERE trip_id = @trip2 AND attraction_id = 10810);
SET @c2_4 = (SELECT id FROM trip_candidate WHERE trip_id = @trip2 AND attraction_id = 10807);
SET @c2_5 = (SELECT id FROM trip_candidate WHERE trip_id = @trip2 AND attraction_id = 10816);

INSERT INTO trip_block (candidate_id, trip_date, display_order, start_time, duration_minutes) VALUES (@c2_1, '2026-08-15', 1, '10:00:00', 90);
INSERT INTO trip_block (candidate_id, trip_date, display_order, start_time, duration_minutes) VALUES (@c2_2, '2026-08-15', 2, '14:00:00', 120);
INSERT INTO trip_block (candidate_id, trip_date, display_order, start_time, duration_minutes) VALUES (@c2_3, '2026-08-15', 3, '17:00:00', 60);
INSERT INTO trip_block (candidate_id, trip_date, display_order, start_time, duration_minutes) VALUES (@c2_4, '2026-08-16', 1, '09:30:00', 150);
INSERT INTO trip_block (candidate_id, trip_date, display_order, start_time, duration_minutes) VALUES (@c2_5, '2026-08-16', 2, '14:00:00', 90);

UPDATE post SET trip_id = @trip2 WHERE id = 18;


-- Trip 3: 제주도 3박 4일 (김지연 member_id=3 / post 15)
INSERT INTO trip (member_id, title, start_date, end_date, member_count, default_transit_mode, is_public)
VALUES (3, '제주도 3박 4일 오름 자연 탐방', '2026-09-20', '2026-09-23', 3, 'DRIVING', 1);
SET @trip3 = LAST_INSERT_ID();

INSERT INTO trip_candidate (trip_id, attraction_id, city_code, source) VALUES (@trip3, 24449, 39, 'MANUAL');
INSERT INTO trip_candidate (trip_id, attraction_id, city_code, source) VALUES (@trip3, 24450, 39, 'MANUAL');
INSERT INTO trip_candidate (trip_id, attraction_id, city_code, source) VALUES (@trip3, 24451, 39, 'MANUAL');
INSERT INTO trip_candidate (trip_id, attraction_id, city_code, source) VALUES (@trip3, 24452, 39, 'MANUAL');
INSERT INTO trip_candidate (trip_id, attraction_id, city_code, source) VALUES (@trip3, 24454, 39, 'MANUAL');
INSERT INTO trip_candidate (trip_id, attraction_id, city_code, source) VALUES (@trip3, 24455, 39, 'MANUAL');
INSERT INTO trip_candidate (trip_id, attraction_id, city_code, source) VALUES (@trip3, 24456, 39, 'MANUAL');

SET @c3_1 = (SELECT id FROM trip_candidate WHERE trip_id = @trip3 AND attraction_id = 24449);
SET @c3_2 = (SELECT id FROM trip_candidate WHERE trip_id = @trip3 AND attraction_id = 24450);
SET @c3_3 = (SELECT id FROM trip_candidate WHERE trip_id = @trip3 AND attraction_id = 24451);
SET @c3_4 = (SELECT id FROM trip_candidate WHERE trip_id = @trip3 AND attraction_id = 24452);
SET @c3_5 = (SELECT id FROM trip_candidate WHERE trip_id = @trip3 AND attraction_id = 24454);
SET @c3_6 = (SELECT id FROM trip_candidate WHERE trip_id = @trip3 AND attraction_id = 24455);
SET @c3_7 = (SELECT id FROM trip_candidate WHERE trip_id = @trip3 AND attraction_id = 24456);

INSERT INTO trip_block (candidate_id, trip_date, display_order, start_time, duration_minutes) VALUES (@c3_1, '2026-09-20', 1, '10:00:00', 120);
INSERT INTO trip_block (candidate_id, trip_date, display_order, start_time, duration_minutes) VALUES (@c3_2, '2026-09-20', 2, '14:00:00', 120);
INSERT INTO trip_block (candidate_id, trip_date, display_order, start_time, duration_minutes) VALUES (@c3_3, '2026-09-21', 1, '09:30:00', 150);
INSERT INTO trip_block (candidate_id, trip_date, display_order, start_time, duration_minutes) VALUES (@c3_4, '2026-09-21', 2, '14:30:00', 120);
INSERT INTO trip_block (candidate_id, trip_date, display_order, start_time, duration_minutes) VALUES (@c3_5, '2026-09-22', 1, '10:00:00', 180);
INSERT INTO trip_block (candidate_id, trip_date, display_order, start_time, duration_minutes) VALUES (@c3_6, '2026-09-22', 2, '15:00:00', 90);
INSERT INTO trip_block (candidate_id, trip_date, display_order, start_time, duration_minutes) VALUES (@c3_7, '2026-09-23', 1, '11:00:00', 120);

UPDATE post SET trip_id = @trip3 WHERE id = 15;
