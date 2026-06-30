-- =============================================================
-- 커뮤니티 예시 데이터 — 운영 DB 자립 실행본 (로컬 post 8~19 동등)
-- 회원 → 게시글(후기 12) → 댓글(30) → 일정·커버(12) 순서로 한 파일에 담음.
--
-- 사진: post 테이블에 커버 컬럼이 없다. 카드 사진은
--       연결 일정(trip) → 후보 관광지(trip_candidate) → attraction.first_image(URL)
--       에서 자동으로 잡힌다. 별도 이미지 업로드 불필요.
--       → 운영 attraction 에 first_image 채워진 관광지가 있어야 사진이 뜬다(TourAPI 수집 완료 전제).
--       관광지는 sido_code 로 "사진 있는 곳"을 동적 선택하므로 운영 관광지 ID가 달라도 안전.
--
-- 멱등: 모든 INSERT 가 중복 가드(INSERT IGNORE / NOT EXISTS / 안티조인)라 재실행 안전.
-- 회원 공통 비밀번호: Test1234!
--
-- 실행 대상 DB: 운영 = tripcraft  (로컬에서 돌릴 땐 아래 USE 를 trip_craft 로)
-- =============================================================
USE tripcraft;

-- -------------------------------------------------------------
-- 1. 테스트 회원 (email UNIQUE → INSERT IGNORE 로 멱등)
-- -------------------------------------------------------------
INSERT IGNORE INTO member (email, password, nickname, role) VALUES
  ('jiyeon@test.com',  '$2b$10$bMnwHL8yqkU4EXJfEondn.qDD9PlmcED2l8FM3xCTj1Q2s0dfFflK', '김지연', 'USER'),
  ('minsoo@test.com',  '$2b$10$bMnwHL8yqkU4EXJfEondn.qDD9PlmcED2l8FM3xCTj1Q2s0dfFflK', '박민수', 'USER'),
  ('sohee@test.com',   '$2b$10$bMnwHL8yqkU4EXJfEondn.qDD9PlmcED2l8FM3xCTj1Q2s0dfFflK', '이소희', 'USER'),
  ('taehoon@test.com', '$2b$10$bMnwHL8yqkU4EXJfEondn.qDD9PlmcED2l8FM3xCTj1Q2s0dfFflK', '최태훈', 'USER'),
  ('yurim@test.com',   '$2b$10$bMnwHL8yqkU4EXJfEondn.qDD9PlmcED2l8FM3xCTj1Q2s0dfFflK', '한유림', 'USER');

SET @jiyeon  = (SELECT id FROM member WHERE email = 'jiyeon@test.com');
SET @minsoo  = (SELECT id FROM member WHERE email = 'minsoo@test.com');
SET @sohee   = (SELECT id FROM member WHERE email = 'sohee@test.com');
SET @taehoon = (SELECT id FROM member WHERE email = 'taehoon@test.com');
SET @yurim   = (SELECT id FROM member WHERE email = 'yurim@test.com');

-- -------------------------------------------------------------
-- 2. 게시글 12건 (제목으로 안티조인 → 이미 있으면 건너뜀)
-- -------------------------------------------------------------
INSERT INTO post (member_id, title, content, view_count, like_count)
SELECT v.mid, v.title, v.content, v.vc, v.lc
FROM (
  SELECT @jiyeon mid, '제주도 3박 4일 완전 정복 후기' title,
    '<p>드디어 꿈에 그리던 제주도 여행을 다녀왔어요! 성산일출봉에서 바라본 일출은 정말 평생 잊지 못할 것 같아요.</p><p>첫날은 공항 근처에서 흑돼지 구이로 시작했는데, 두툼한 고기에 쌈 싸서 먹는 그 맛이란... 서울에서 먹던 것과는 차원이 달랐어요.</p><p>둘째 날은 우도에 들어가서 땅콩 아이스크림을 먹고 자전거로 섬을 한 바퀴 돌았어요. 에메랄드빛 바다가 너무 아름다웠습니다. 혼저옵서예!</p>' content,
    145 vc, 24 lc
  UNION ALL SELECT @jiyeon, '혼자 떠난 부산 1박 2일 — 바다와 야경 사이',
    '<p>갑작스럽게 혼자 부산 여행을 결심했어요. 해운대 해수욕장을 걷다 보니 바다 냄새에 온갖 걱정이 사라지는 느낌이었어요.</p><p>광안리에서 광안대교 야경을 보며 맥주 한 캔. 이게 진짜 여행이구나 싶었어요. 다음에는 꼭 친구들이랑 같이 오고 싶어요 🌊</p>', 88, 11
  UNION ALL SELECT @minsoo, '경주 역사 탐방 — 불국사부터 첨성대까지',
    '<p>한국사를 좋아하는 분들이라면 경주는 정말 강력 추천입니다. 불국사 석가탑 앞에 서면 천 년의 시간이 느껴지는 것 같아요.</p><p>첨성대는 생각보다 작았지만 그 자리에 서서 신라 시대 사람들을 상상하니 감동이 밀려왔습니다. 분황사 모전석탑도 놓치지 마세요!</p><p>야경은 동궁과 월지(안압지)가 최고입니다. 물에 비친 조명이 너무 아름다워요.</p>', 212, 38
  UNION ALL SELECT @minsoo, '강릉 커피 거리 투어 완벽 가이드',
    '<p>강릉은 이제 커피 도시라고 불러도 손색이 없을 것 같아요. 안목 해변 카페 거리에서 아메리카노 한 잔 들고 바다를 바라보는 그 여유!</p><p>테라로사, 보헤미안 등 유명 로스터리도 다 들러봤는데 각각 개성이 달라서 비교하는 재미가 있었어요. 카페인 과다 섭취 주의 ☕</p>', 158, 29
  UNION ALL SELECT @sohee, '전주 한옥마을 감성 여행 — 한복 입고 인증샷',
    '<p>전주 한옥마을에서 한복을 빌려 입고 돌아다니는 건 진짜 강력 추천이에요! 오목대에서 내려다보는 한옥마을 뷰가 정말 예쁘거든요.</p><p>전주 비빔밥은 당연히 먹어야죠. 한옥마을 안 식당에서 먹었는데 고추장의 풍미가 남달랐어요. 막걸리랑 같이 먹으면 더 맛있어요 😄</p><p>콩나물국밥도 빠질 수 없죠. 아침에 뜨끈하게 한 그릇 하고 나서 다시 한옥마을을 산책하는 것이 전주 여행의 정석입니다.</p>', 201, 45
  UNION ALL SELECT @sohee, '남해 독일마을 & 다랭이 논 드라이브 코스',
    '<p>남해는 생각보다 훨씬 아름다운 곳이에요. 독일마을에서 이국적인 느낌을 받고, 바로 옆 다랭이 논에서는 한국의 전통적인 아름다움을 동시에 느낄 수 있어요.</p><p>해안 드라이브 도로가 정말 환상적이에요. 창문 내리고 바다 바람 맞으면서 달리면 그게 바로 힐링이더라고요 🚗</p>', 96, 17
  UNION ALL SELECT @taehoon, '설악산 단풍 트레킹 — 비선대 코스 추천',
    '<p>10월 설악산 단풍은 진짜 미쳤어요. 비선대까지 올라가는 길에 양쪽으로 펼쳐지는 단풍이 마치 그림 속에 있는 것 같았어요.</p><p>코스 난이도는 중급 정도인데 경치가 너무 좋아서 힘든 줄도 몰랐어요. 단, 주말에는 사람이 엄청 많으니까 평일 이른 아침에 출발하는 게 좋아요.</p><p>내려올 때 먹었던 황태해장국 한 그릇이 꿀맛이었습니다. 다리에 힘이 쫙 풀리더니 다시 살아나는 느낌 🍃</p>', 269, 52
  UNION ALL SELECT @taehoon, '속초 당일치기 코스 — 아바이 마을부터 대포항까지',
    '<p>속초 당일치기도 충분히 즐길 수 있어요! 아바이 순대 먹고, 갯배 타고 건너가서 영금정 구경하고, 대포항에서 회까지 먹으면 완벽한 하루예요.</p><p>속초 시장 닭강정도 줄 서서 먹을 만한 가치가 있어요. 달달하고 바삭한 게 정말 중독성 있더라고요 🦑</p>', 132, 21
  UNION ALL SELECT @yurim, '여수 낭만 포차 거리 & 돌산도 야경',
    '<p>여수 밤바다는 노래 가사 그대로예요. 낭만 포차 거리에서 갓 구운 굴과 새조개 샤브샤브를 먹으면서 바라보는 여수 야경은 정말 낭만 그 자체입니다.</p><p>돌산대교 야경 포인트에서 야경 사진도 찍고, 향일암 일출도 보고 싶어서 새벽 4시에 일어났는데 완전 후회 없었어요 ✨</p>', 185, 36
  UNION ALL SELECT @yurim, '통영 블루로드 트레킹 & 케이블카',
    '<p>통영은 한국의 나폴리라는 별명이 딱 맞아요. 케이블카 타고 미륵산 정상에서 내려다보는 한려수도 뷰는 숨이 막힐 정도로 아름다워요.</p><p>블루로드 트레킹 코스도 잘 정비되어 있고 각 구간마다 다른 느낌의 바다를 볼 수 있어요. 이순신 장군 유적지도 근처에 있으니 같이 둘러보세요 ⛵</p>', 120, 22
  UNION ALL SELECT @jiyeon, '서울 고궁 산책 — 경복궁부터 북촌 한옥마을까지',
    '<p>주말에 서울 도심에서 고궁 산책을 했어요. 경복궁 수문장 교대식을 보고 한복 입은 사람들 사이를 걷다 보니 시간 여행을 온 기분이었어요.</p><p>북촌 한옥마을 골목골목이 정말 예뻐서 사진 찍느라 시간 가는 줄 몰랐네요. 삼청동 카페에서 마무리까지 완벽한 하루였습니다 🏯</p>', 135, 19
  UNION ALL SELECT @taehoon, '안동 하회마을 — 고즈넉한 전통의 하루',
    '<p>안동 하회마을은 시간이 멈춘 듯한 곳이에요. 낙동강이 마을을 휘감아 도는 풍경이 그림 같았습니다.</p><p>저녁에 본 하회별신굿탈놀이가 인상적이었고, 안동 간고등어 정식도 별미였어요. 병산서원까지 들르면 완벽한 코스랍니다 🌾</p>', 103, 15
) v
LEFT JOIN post p ON p.title = v.title
WHERE p.id IS NULL;

-- -------------------------------------------------------------
-- 3. 댓글 30건 (post_id + content 안티조인 → 멱등)
-- -------------------------------------------------------------
SET @p1  = (SELECT id FROM post WHERE title = '제주도 3박 4일 완전 정복 후기');
SET @p2  = (SELECT id FROM post WHERE title = '혼자 떠난 부산 1박 2일 — 바다와 야경 사이');
SET @p3  = (SELECT id FROM post WHERE title = '경주 역사 탐방 — 불국사부터 첨성대까지');
SET @p4  = (SELECT id FROM post WHERE title = '강릉 커피 거리 투어 완벽 가이드');
SET @p5  = (SELECT id FROM post WHERE title = '전주 한옥마을 감성 여행 — 한복 입고 인증샷');
SET @p6  = (SELECT id FROM post WHERE title = '남해 독일마을 & 다랭이 논 드라이브 코스');
SET @p7  = (SELECT id FROM post WHERE title = '설악산 단풍 트레킹 — 비선대 코스 추천');
SET @p8  = (SELECT id FROM post WHERE title = '속초 당일치기 코스 — 아바이 마을부터 대포항까지');
SET @p9  = (SELECT id FROM post WHERE title = '여수 낭만 포차 거리 & 돌산도 야경');
SET @p10 = (SELECT id FROM post WHERE title = '통영 블루로드 트레킹 & 케이블카');

INSERT INTO post_comment (post_id, member_id, content)
SELECT v.pid, v.mid, v.c
FROM (
  SELECT @p1 pid, @minsoo  mid, '성산일출봉 일출 저도 봤는데 정말 잊을 수 없는 경험이에요. 다음에는 우도 스쿠버다이빙도 도전해보세요!' c
  UNION ALL SELECT @p1, @sohee,   '흑돼지 너무 그립다... 저도 제주도 가고 싶어졌어요 ㅠㅠ'
  UNION ALL SELECT @p1, @taehoon, '우도 자전거 투어 코스가 어떻게 됐나요? 몇 시간 정도 걸렸는지도 궁금해요!'
  UNION ALL SELECT @p1, @yurim,   '제주도 여행 일정 공유해주시면 참고하고 싶어요. 혼자 여행 계획 중이거든요 😊'
  UNION ALL SELECT @p2, @jiyeon,  '광안리 야경 맥주 조합 진짜 최고죠. 저는 부산 갈 때마다 그게 1번 코스예요!'
  UNION ALL SELECT @p2, @sohee,   '혼자 여행도 이렇게 감성적으로 즐기실 수 있군요. 용기 내야겠어요 🌊'
  UNION ALL SELECT @p3, @jiyeon,  '동궁과 월지 야경 사진 정말 예쁘게 나오더라고요! 어느 계절에 가셨어요?'
  UNION ALL SELECT @p3, @sohee,   '저도 경주 수학여행 이후로 한 번도 못 갔는데, 어른이 돼서 다시 가보고 싶네요.'
  UNION ALL SELECT @p3, @yurim,   '불국사 석가탑 앞에서 사진 찍으면 정말 멋있게 나오더라고요. 좋은 후기 감사해요!'
  UNION ALL SELECT @p4, @jiyeon,  '테라로사 커피 정말 유명하죠! 안목 해변에서 커피 마시는 그 여유가 그립네요.'
  UNION ALL SELECT @p4, @taehoon, '강릉 커피 투어 예산은 얼마나 잡으면 될까요? 카페 몇 군데나 들르셨어요?'
  UNION ALL SELECT @p4, @yurim,   '보헤미안이 어디 있는지 찾기 어렵던데 위치 알려주실 수 있나요? 꼭 가보고 싶어요!'
  UNION ALL SELECT @p5, @minsoo,  '전주 비빔밥은 진짜 전주에서 먹어야 맛있죠. 서울 전주비빔밥은 흉내도 못 내요 ㅋㅋ'
  UNION ALL SELECT @p5, @taehoon, '한복 입고 사진 찍는 거 부끄럽지 않으셨어요? 저도 도전해보고 싶은데 용기가 안 나서요 😅'
  UNION ALL SELECT @p5, @yurim,   '오목대 뷰포인트 정말 인생샷 스팟이에요. 저도 전주 사진 되게 잘 나왔던 기억이 있어요!'
  UNION ALL SELECT @p6, @minsoo,  '다랭이 논 봄에도 예쁘다던데 어느 계절이 제일 좋을까요?'
  UNION ALL SELECT @p6, @taehoon, '독일마을 실제로 보면 정말 유럽 느낌 나나요? 사진으로만 봤는데 신기하더라고요!'
  UNION ALL SELECT @p7, @jiyeon,  '10월 단풍 시즌에 예약하려면 숙소 얼마나 일찍부터 잡아야 하나요? 매년 엄청 붙잡히던데요.'
  UNION ALL SELECT @p7, @minsoo,  '비선대 코스 왕복으로 몇 시간 정도 잡으면 될까요? 중간에 식사 시간도 포함해서요.'
  UNION ALL SELECT @p7, @sohee,   '황태해장국 어느 식당이에요? 저도 설악산 트레킹 후에 꼭 먹어보고 싶어요 🍜'
  UNION ALL SELECT @p7, @yurim,   '평일에 가도 사람 많던가요? 조용하게 즐기고 싶어서요.'
  UNION ALL SELECT @p8, @jiyeon,  '아바이 마을 갯배 타는 거 저도 항상 재밌었어요! 운치가 있죠 ㅎㅎ'
  UNION ALL SELECT @p8, @sohee,   '속초 시장 닭강정 줄이 엄청 길던데 얼마나 기다리셨어요?'
  UNION ALL SELECT @p8, @yurim,   '대포항 회는 어느 식당이 제일 괜찮았어요? 가격도 궁금해요!'
  UNION ALL SELECT @p9, @minsoo,  '여수 밤바다 노래 들으면서 거기 앉아 있으면 진짜 소름 돋겠다 ㅋㅋ 부럽네요.'
  UNION ALL SELECT @p9, @taehoon, '향일암 일출 정말 힘들게 올라갔는데 그 가치는 충분하더라고요!'
  UNION ALL SELECT @p9, @sohee,   '낭만 포차 가격이 얼마나 되나요? 예산 계획할 때 참고하고 싶어서요.'
  UNION ALL SELECT @p10, @jiyeon, '케이블카 대기 시간이 얼마나 되던가요? 주말에 가면 줄이 엄청 길다고 들었어요.'
  UNION ALL SELECT @p10, @minsoo, '이순신 장군 유적지도 근처라니 역사 공부도 하고 여행도 하고 일석이조네요!'
  UNION ALL SELECT @p10, @sohee,  '블루로드 전 구간 다 도신 건가요? A~D 코스 중 제일 추천하는 코스가 있나요?'
) v
LEFT JOIN post_comment pc ON pc.post_id = v.pid AND pc.content = v.c
WHERE pc.id IS NULL;

-- -------------------------------------------------------------
-- 4. 일정 + 커버 연결 (사진 = sido_code 로 사진 있는 관광지 동적 선택)
--    sido_code(TourAPI areaCode): 1서울 6부산 32강원 35경북 36경남 37전북 38전남 39제주
--    재실행 안전: trip NOT EXISTS / candidate NOT EXISTS / post.trip_id IS NULL
-- -------------------------------------------------------------

-- 1) 제주(39) — 김지연
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
SELECT @jiyeon,'제주 3박 4일','2026-09-20','2026-09-23',3,'DRIVING',1
WHERE NOT EXISTS (SELECT 1 FROM trip WHERE title='제주 3박 4일' AND member_id=@jiyeon);
SET @t=(SELECT id FROM trip WHERE title='제주 3박 4일' AND member_id=@jiyeon ORDER BY id LIMIT 1);
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
SELECT @t,(SELECT id FROM attraction WHERE sido_code=39 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 0),39,'MANUAL'
WHERE NOT EXISTS (SELECT 1 FROM trip_candidate WHERE trip_id=@t);
UPDATE post SET trip_id=@t WHERE title='제주도 3박 4일 완전 정복 후기' AND trip_id IS NULL;

-- 2) 부산(6) — 김지연
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
SELECT @jiyeon,'부산 1박 2일','2026-07-10','2026-07-11',1,'PUBLIC_TRANSIT',1
WHERE NOT EXISTS (SELECT 1 FROM trip WHERE title='부산 1박 2일' AND member_id=@jiyeon);
SET @t=(SELECT id FROM trip WHERE title='부산 1박 2일' AND member_id=@jiyeon ORDER BY id LIMIT 1);
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
SELECT @t,(SELECT id FROM attraction WHERE sido_code=6 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 0),6,'MANUAL'
WHERE NOT EXISTS (SELECT 1 FROM trip_candidate WHERE trip_id=@t);
UPDATE post SET trip_id=@t WHERE title='혼자 떠난 부산 1박 2일 — 바다와 야경 사이' AND trip_id IS NULL;

-- 3) 경주(경북 35) — 박민수
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
SELECT @minsoo,'경주 역사 탐방','2026-05-10','2026-05-11',2,'PUBLIC_TRANSIT',1
WHERE NOT EXISTS (SELECT 1 FROM trip WHERE title='경주 역사 탐방' AND member_id=@minsoo);
SET @t=(SELECT id FROM trip WHERE title='경주 역사 탐방' AND member_id=@minsoo ORDER BY id LIMIT 1);
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
SELECT @t,(SELECT id FROM attraction WHERE sido_code=35 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 0),35,'MANUAL'
WHERE NOT EXISTS (SELECT 1 FROM trip_candidate WHERE trip_id=@t);
UPDATE post SET trip_id=@t WHERE title='경주 역사 탐방 — 불국사부터 첨성대까지' AND trip_id IS NULL;

-- 4) 강릉(강원 32) — 박민수
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
SELECT @minsoo,'강릉 1박 2일','2026-08-15','2026-08-16',2,'DRIVING',1
WHERE NOT EXISTS (SELECT 1 FROM trip WHERE title='강릉 1박 2일' AND member_id=@minsoo);
SET @t=(SELECT id FROM trip WHERE title='강릉 1박 2일' AND member_id=@minsoo ORDER BY id LIMIT 1);
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
SELECT @t,(SELECT id FROM attraction WHERE sido_code=32 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 14),32,'MANUAL'
WHERE NOT EXISTS (SELECT 1 FROM trip_candidate WHERE trip_id=@t);
UPDATE post SET trip_id=@t WHERE title='강릉 커피 거리 투어 완벽 가이드' AND trip_id IS NULL;

-- 5) 전주(전북 37) — 이소희
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
SELECT @sohee,'전주 한옥마을','2026-04-05','2026-04-06',2,'PUBLIC_TRANSIT',1
WHERE NOT EXISTS (SELECT 1 FROM trip WHERE title='전주 한옥마을' AND member_id=@sohee);
SET @t=(SELECT id FROM trip WHERE title='전주 한옥마을' AND member_id=@sohee ORDER BY id LIMIT 1);
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
SELECT @t,(SELECT id FROM attraction WHERE sido_code=37 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 0),37,'MANUAL'
WHERE NOT EXISTS (SELECT 1 FROM trip_candidate WHERE trip_id=@t);
UPDATE post SET trip_id=@t WHERE title='전주 한옥마을 감성 여행 — 한복 입고 인증샷' AND trip_id IS NULL;

-- 6) 남해(경남 36) — 이소희
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
SELECT @sohee,'남해 드라이브','2026-06-01','2026-06-02',2,'DRIVING',1
WHERE NOT EXISTS (SELECT 1 FROM trip WHERE title='남해 드라이브' AND member_id=@sohee);
SET @t=(SELECT id FROM trip WHERE title='남해 드라이브' AND member_id=@sohee ORDER BY id LIMIT 1);
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
SELECT @t,(SELECT id FROM attraction WHERE sido_code=36 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 0),36,'MANUAL'
WHERE NOT EXISTS (SELECT 1 FROM trip_candidate WHERE trip_id=@t);
UPDATE post SET trip_id=@t WHERE title='남해 독일마을 & 다랭이 논 드라이브 코스' AND trip_id IS NULL;

-- 7) 설악산(강원 32) — 최태훈
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
SELECT @taehoon,'설악산 단풍 트레킹','2026-10-18','2026-10-19',2,'DRIVING',1
WHERE NOT EXISTS (SELECT 1 FROM trip WHERE title='설악산 단풍 트레킹' AND member_id=@taehoon);
SET @t=(SELECT id FROM trip WHERE title='설악산 단풍 트레킹' AND member_id=@taehoon ORDER BY id LIMIT 1);
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
SELECT @t,(SELECT id FROM attraction WHERE sido_code=32 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 0),32,'MANUAL'
WHERE NOT EXISTS (SELECT 1 FROM trip_candidate WHERE trip_id=@t);
UPDATE post SET trip_id=@t WHERE title='설악산 단풍 트레킹 — 비선대 코스 추천' AND trip_id IS NULL;

-- 8) 속초(강원 32) — 최태훈
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
SELECT @taehoon,'속초 당일치기','2026-09-12','2026-09-12',1,'PUBLIC_TRANSIT',1
WHERE NOT EXISTS (SELECT 1 FROM trip WHERE title='속초 당일치기' AND member_id=@taehoon);
SET @t=(SELECT id FROM trip WHERE title='속초 당일치기' AND member_id=@taehoon ORDER BY id LIMIT 1);
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
SELECT @t,(SELECT id FROM attraction WHERE sido_code=32 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 7),32,'MANUAL'
WHERE NOT EXISTS (SELECT 1 FROM trip_candidate WHERE trip_id=@t);
UPDATE post SET trip_id=@t WHERE title='속초 당일치기 코스 — 아바이 마을부터 대포항까지' AND trip_id IS NULL;

-- 9) 여수(전남 38) — 한유림
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
SELECT @yurim,'여수 밤바다','2026-07-25','2026-07-26',2,'PUBLIC_TRANSIT',1
WHERE NOT EXISTS (SELECT 1 FROM trip WHERE title='여수 밤바다' AND member_id=@yurim);
SET @t=(SELECT id FROM trip WHERE title='여수 밤바다' AND member_id=@yurim ORDER BY id LIMIT 1);
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
SELECT @t,(SELECT id FROM attraction WHERE sido_code=38 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 0),38,'MANUAL'
WHERE NOT EXISTS (SELECT 1 FROM trip_candidate WHERE trip_id=@t);
UPDATE post SET trip_id=@t WHERE title='여수 낭만 포차 거리 & 돌산도 야경' AND trip_id IS NULL;

-- 10) 통영(경남 36) — 한유림
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
SELECT @yurim,'통영 한려수도','2026-08-08','2026-08-09',2,'PUBLIC_TRANSIT',1
WHERE NOT EXISTS (SELECT 1 FROM trip WHERE title='통영 한려수도' AND member_id=@yurim);
SET @t=(SELECT id FROM trip WHERE title='통영 한려수도' AND member_id=@yurim ORDER BY id LIMIT 1);
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
SELECT @t,(SELECT id FROM attraction WHERE sido_code=36 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 7),36,'MANUAL'
WHERE NOT EXISTS (SELECT 1 FROM trip_candidate WHERE trip_id=@t);
UPDATE post SET trip_id=@t WHERE title='통영 블루로드 트레킹 & 케이블카' AND trip_id IS NULL;

-- 11) 서울(1) — 김지연  [신규]
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
SELECT @jiyeon,'서울 고궁 산책','2026-03-21','2026-03-21',2,'PUBLIC_TRANSIT',1
WHERE NOT EXISTS (SELECT 1 FROM trip WHERE title='서울 고궁 산책' AND member_id=@jiyeon);
SET @t=(SELECT id FROM trip WHERE title='서울 고궁 산책' AND member_id=@jiyeon ORDER BY id LIMIT 1);
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
SELECT @t,(SELECT id FROM attraction WHERE sido_code=1 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 0),1,'MANUAL'
WHERE NOT EXISTS (SELECT 1 FROM trip_candidate WHERE trip_id=@t);
UPDATE post SET trip_id=@t WHERE title='서울 고궁 산책 — 경복궁부터 북촌 한옥마을까지' AND trip_id IS NULL;

-- 12) 안동(경북 35) — 최태훈  [신규]  (경주와 OFFSET 달리해 사진 겹치지 않게)
INSERT INTO trip (member_id,title,start_date,end_date,member_count,default_transit_mode,is_public)
SELECT @taehoon,'안동 전통 여행','2026-05-30','2026-05-31',2,'DRIVING',1
WHERE NOT EXISTS (SELECT 1 FROM trip WHERE title='안동 전통 여행' AND member_id=@taehoon);
SET @t=(SELECT id FROM trip WHERE title='안동 전통 여행' AND member_id=@taehoon ORDER BY id LIMIT 1);
INSERT INTO trip_candidate (trip_id,attraction_id,city_code,source)
SELECT @t,(SELECT id FROM attraction WHERE sido_code=35 AND content_type_id=12 AND first_image IS NOT NULL AND first_image<>'' ORDER BY id LIMIT 1 OFFSET 50),35,'MANUAL'
WHERE NOT EXISTS (SELECT 1 FROM trip_candidate WHERE trip_id=@t);
UPDATE post SET trip_id=@t WHERE title='안동 하회마을 — 고즈넉한 전통의 하루' AND trip_id IS NULL;

-- -------------------------------------------------------------
-- 확인 쿼리
-- SELECT p.id, p.title,
--   (SELECT a.first_image FROM trip_candidate tc JOIN attraction a ON a.id=tc.attraction_id
--    WHERE tc.trip_id=p.trip_id ORDER BY tc.id LIMIT 1) AS cover_img,
--   (SELECT COUNT(*) FROM post_comment c WHERE c.post_id=p.id) AS comments
-- FROM post p WHERE p.member_id IN (SELECT id FROM member WHERE email LIKE '%@test.com')
-- ORDER BY p.id;
-- =============================================================
