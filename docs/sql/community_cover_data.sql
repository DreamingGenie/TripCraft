-- =============================================================
-- 커뮤니티 후기 커버사진 목데이터
-- community_test_data.sql 의 후기 10개를 "지역에 맞는 사진 있는 관광지"에 연결해
-- 피드 카드 커버가 실제 관광지 사진으로 나오게 한다.
-- (커버 폴백: post_cover → 본문 첫 이미지 → 연결 일정 대표이미지)
--
-- 실행 전제: community_test_data.sql 실행 완료(회원·게시글 존재).
-- 제목 매칭 + trip_id IS NULL 가드라 멱등(재실행 안전). trip_test_data.sql 불필요.
-- =============================================================
USE trip_craft;

SET @jiyeon  = (SELECT id FROM member WHERE email = 'jiyeon@test.com');
SET @minsoo  = (SELECT id FROM member WHERE email = 'minsoo@test.com');
SET @sohee   = (SELECT id FROM member WHERE email = 'sohee@test.com');
SET @taehoon = (SELECT id FROM member WHERE email = 'taehoon@test.com');
SET @yurim   = (SELECT id FROM member WHERE email = 'yurim@test.com');

-- 한 지역의 "사진 있는 관광지(분류 12)"를 골라 일정으로 만들고 글에 연결.
-- sido_code(TourAPI areaCode): 6부산 32강원 35경북 36경남 37전북 38전남 39제주.
-- 같은 시도에 글이 여럿이면 OFFSET 으로 다른 관광지를 골라 사진이 겹치지 않게 한다.

-- 공통 프로시저 대신 반복 블록 사용(@m=회원, @t=일정).

-- 1) 제주(39) — 김지연
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
  VALUES (@jiyeon,'제주 3박 4일','2026-09-20','2026-09-23',3,'DRIVING',1);
SET @t=LAST_INSERT_ID();
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
  VALUES (@t,(SELECT id FROM attraction WHERE sido_code=39 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 0),39,'MANUAL');
UPDATE post SET trip_id=@t WHERE title='제주도 3박 4일 완전 정복 후기' AND trip_id IS NULL;

-- 2) 부산(6) — 김지연
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
  VALUES (@jiyeon,'부산 1박 2일','2026-07-10','2026-07-11',1,'PUBLIC_TRANSIT',1);
SET @t=LAST_INSERT_ID();
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
  VALUES (@t,(SELECT id FROM attraction WHERE sido_code=6 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 0),6,'MANUAL');
UPDATE post SET trip_id=@t WHERE title='혼자 떠난 부산 1박 2일 — 바다와 야경 사이' AND trip_id IS NULL;

-- 3) 경주(경북 35) — 박민수
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
  VALUES (@minsoo,'경주 역사 탐방','2026-05-10','2026-05-11',2,'PUBLIC_TRANSIT',1);
SET @t=LAST_INSERT_ID();
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
  VALUES (@t,(SELECT id FROM attraction WHERE sido_code=35 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 0),35,'MANUAL');
UPDATE post SET trip_id=@t WHERE title='경주 역사 탐방 — 불국사부터 첨성대까지' AND trip_id IS NULL;

-- 4) 강릉(강원 32) — 박민수
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
  VALUES (@minsoo,'강릉 1박 2일','2026-08-15','2026-08-16',2,'DRIVING',1);
SET @t=LAST_INSERT_ID();
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
  VALUES (@t,(SELECT id FROM attraction WHERE sido_code=32 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 14),32,'MANUAL');
UPDATE post SET trip_id=@t WHERE title='강릉 커피 거리 투어 완벽 가이드' AND trip_id IS NULL;

-- 5) 전주(전북 37) — 이소희
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
  VALUES (@sohee,'전주 한옥마을','2026-04-05','2026-04-06',2,'PUBLIC_TRANSIT',1);
SET @t=LAST_INSERT_ID();
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
  VALUES (@t,(SELECT id FROM attraction WHERE sido_code=37 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 0),37,'MANUAL');
UPDATE post SET trip_id=@t WHERE title='전주 한옥마을 감성 여행 — 한복 입고 인증샷' AND trip_id IS NULL;

-- 6) 남해(경남 36) — 이소희
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
  VALUES (@sohee,'남해 드라이브','2026-06-01','2026-06-02',2,'DRIVING',1);
SET @t=LAST_INSERT_ID();
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
  VALUES (@t,(SELECT id FROM attraction WHERE sido_code=36 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 0),36,'MANUAL');
UPDATE post SET trip_id=@t WHERE title='남해 독일마을 & 다랭이 논 드라이브 코스' AND trip_id IS NULL;

-- 7) 설악산(강원 32) — 최태훈
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
  VALUES (@taehoon,'설악산 단풍 트레킹','2026-10-18','2026-10-19',2,'DRIVING',1);
SET @t=LAST_INSERT_ID();
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
  VALUES (@t,(SELECT id FROM attraction WHERE sido_code=32 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 0),32,'MANUAL');
UPDATE post SET trip_id=@t WHERE title='설악산 단풍 트레킹 — 비선대 코스 추천' AND trip_id IS NULL;

-- 8) 속초(강원 32) — 최태훈
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
  VALUES (@taehoon,'속초 당일치기','2026-09-12','2026-09-12',1,'PUBLIC_TRANSIT',1);
SET @t=LAST_INSERT_ID();
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
  VALUES (@t,(SELECT id FROM attraction WHERE sido_code=32 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 7),32,'MANUAL');
UPDATE post SET trip_id=@t WHERE title='속초 당일치기 코스 — 아바이 마을부터 대포항까지' AND trip_id IS NULL;

-- 9) 여수(전남 38) — 한유림
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
  VALUES (@yurim,'여수 밤바다','2026-07-25','2026-07-26',2,'PUBLIC_TRANSIT',1);
SET @t=LAST_INSERT_ID();
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
  VALUES (@t,(SELECT id FROM attraction WHERE sido_code=38 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 0),38,'MANUAL');
UPDATE post SET trip_id=@t WHERE title='여수 낭만 포차 거리 & 돌산도 야경' AND trip_id IS NULL;

-- 10) 통영(경남 36) — 한유림
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
  VALUES (@yurim,'통영 한려수도','2026-08-08','2026-08-09',2,'PUBLIC_TRANSIT',1);
SET @t=LAST_INSERT_ID();
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
  VALUES (@t,(SELECT id FROM attraction WHERE sido_code=36 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 7),36,'MANUAL');
UPDATE post SET trip_id=@t WHERE title='통영 블루로드 트레킹 & 케이블카' AND trip_id IS NULL;

-- 확인:
-- SELECT p.title,
--   (SELECT a.first_image FROM trip_candidate tc JOIN attraction a ON a.id=tc.attraction_id
--    WHERE tc.trip_id=p.trip_id ORDER BY tc.id LIMIT 1) AS cover
-- FROM post p WHERE p.member_id IN (SELECT id FROM member WHERE email LIKE '%@test.com') ORDER BY p.id;
