-- =============================================
-- TripCraft Schema DDL v0.3
-- 기준: 요구사항 명세 v0.1 / 기획서 v0.2
-- v0.2 변경: transit_cache ODsay 응답 전체 저장 구조로 재설계,
--            trip_block에 transit 표시 컬럼(transit_duration_minutes, transit_mode) 추가
-- v0.3 변경: 이동수단 모드 확장 (PUBLIC_TRANSIT / DRIVING / WALKING)
--            trip에 default_transit_mode 추가
--            transit_cache에 request_mode·taxi_fare·route_coords 추가,
--            UNIQUE KEY에 request_mode 포함 (모드별 독립 캐시)
-- =============================================
-- 결정 사항 요약
--   - member: 하드 딜리트. 탈퇴 시 앱 레이어에서 trip_block → trip_candidate → trip 순서대로 삭제 후 member 삭제
--   - attraction: TourAPI 필드 그대로 저장. api_modified_at 기반 증분 배치 동기화
--   - transit_cache: (from, to, departure_hour, request_mode) 키. ODsay·T Map 응답 모드별 캐시
--   - trip_block: transit_duration_minutes/transit_mode 비정규화 저장 (블록 배치 시 자동 계산)
--   - trip: default_transit_mode로 신규 블록 배치 시 기본 이동수단 결정
--   - post: 작성자·일정 삭제 시 SET NULL (탈퇴한 사용자 표시, 게시글 보존)
--   - notice: 관리자 계정 삭제 시 SET NULL (공지 보존)
--   - trip_block → trip_candidate: RESTRICT (모달 확인 후 삭제 UX)
-- =============================================

-- ---------------------------------------------
-- 1. 회원 (member)
-- ---------------------------------------------
CREATE TABLE member (
    id         BIGINT                   NOT NULL AUTO_INCREMENT COMMENT '회원 PK',
    email      VARCHAR(100)             NOT NULL COMMENT '로그인 이메일 (유일)',
    nickname   VARCHAR(20)              NOT NULL COMMENT '닉네임 (2~20자)',
    password   VARCHAR(255)             NOT NULL COMMENT 'BCrypt 해시 저장',
    role       ENUM('USER', 'ADMIN')    NOT NULL DEFAULT 'USER' COMMENT '권한',
    created_at TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_member_email (email)
) COMMENT='회원. 탈퇴 시 하드 딜리트 — 앱 레이어에서 연관 데이터 순서대로 삭제 필요'
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 2. JWT Refresh Token (member_token)
-- ---------------------------------------------
CREATE TABLE member_token (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    member_id     BIGINT       NOT NULL COMMENT '회원 FK',
    refresh_token VARCHAR(500) NOT NULL COMMENT 'Refresh Token 값',
    expires_at    DATETIME     NOT NULL COMMENT '만료 시각 (발급 시 +7일)',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_token_member  (member_id),
    INDEX idx_token_value   (refresh_token(100)),
    FOREIGN KEY fk_token_member (member_id) REFERENCES member(id) ON DELETE CASCADE
) COMMENT='JWT Refresh Token 저장. 로그아웃 시 DELETE로 무효화'
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 3. 시군구 참조 (sigungu) — TourAPI 공식 코드 기준
-- ---------------------------------------------
CREATE TABLE sigungu (
    sido_code    TINYINT     NOT NULL COMMENT '시도 코드 (1=서울, 31=경기 등)',
    sigungu_code TINYINT     NOT NULL COMMENT '시도 내 시군구 코드',
    name         VARCHAR(50) NOT NULL,
    PRIMARY KEY (sido_code, sigungu_code)
) DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 4. 관광지 (attraction) — areaBasedList2 필드 기준
-- ---------------------------------------------
CREATE TABLE attraction (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '내부 PK',
    content_id       VARCHAR(20)   NOT NULL COMMENT 'TourAPI contentid (숫자 문자열)',
    content_type_id  TINYINT       NOT NULL COMMENT '12:관광지 14:문화시설 28:레포츠 32:숙박 38:쇼핑 39:음식점',
    title            VARCHAR(200)  NOT NULL COMMENT '장소명',
    sido_code        TINYINT       NOT NULL COMMENT '시도 코드 (1=서울 6=부산 등, 최대 33)',
    sigungu_code     TINYINT       NOT NULL COMMENT '시군구 코드',
    addr1            VARCHAR(300)  NULL     COMMENT 'TourAPI addr1',
    addr2            VARCHAR(100)  NULL     COMMENT 'TourAPI addr2',
    zipcode          VARCHAR(10)   NULL     COMMENT '우편번호',
    latitude         DECIMAL(10,7) NULL     COMMENT '위도 (TourAPI mapy 변환 저장)',
    longitude        DECIMAL(10,7) NULL     COMMENT '경도 (TourAPI mapx 변환 저장)',
    tel              VARCHAR(50)   NULL     COMMENT '전화번호',
    first_image      VARCHAR(500)  NULL     COMMENT '대표 이미지 URL (firstimage)',
    first_image2     VARCHAR(500)  NULL     COMMENT '대표 이미지2 URL (firstimage2)',
    mlevel           TINYINT       NULL     COMMENT '지도 레벨',
    cat1             VARCHAR(10)   NULL     COMMENT '대분류 코드',
    cat2             VARCHAR(10)   NULL     COMMENT '중분류 코드',
    cat3             VARCHAR(10)   NULL     COMMENT '소분류 코드',
    l_dong_regn_cd   VARCHAR(20)   NULL     COMMENT '법정동 지역 코드',
    l_dong_signgu_cd VARCHAR(20)   NULL     COMMENT '법정동 시군구 코드',
    lcls_systm1      VARCHAR(200)  NULL     COMMENT '분류체계1',
    lcls_systm2      VARCHAR(200)  NULL     COMMENT '분류체계2',
    lcls_systm3      VARCHAR(200)  NULL     COMMENT '분류체계3',
    cpyrht_div_cd    VARCHAR(10)   NULL     COMMENT '저작권 구분 코드',
    api_created_at   DATETIME      NULL     COMMENT 'TourAPI createdtime 변환 저장',
    api_modified_at  DATETIME      NULL     COMMENT 'TourAPI modifiedtime — 증분 배치 기준',
    synced_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                            ON UPDATE CURRENT_TIMESTAMP
                                            COMMENT '마지막 DB 동기화 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uq_attraction_content_id (content_id),
    INDEX idx_sido         (sido_code),
    INDEX idx_sigungu      (sigungu_code),
    INDEX idx_content_type (content_type_id),
    INDEX idx_sido_type    (sido_code,    content_type_id),
    INDEX idx_sigungu_type (sigungu_code, content_type_id),
    INDEX idx_api_modified (api_modified_at)
) COMMENT='한국관광공사 TourAPI 관광지 데이터. areaBasedList2 필드만 저장. 상세정보는 attraction_detail_* 테이블 참조'
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 4-1. 관광지 공통 상세 (attraction_detail_common) — detailCommon2
-- ---------------------------------------------
CREATE TABLE attraction_detail_common (
    content_id VARCHAR(20)  NOT NULL COMMENT 'TourAPI contentid (FK)',
    overview   TEXT         NULL     COMMENT '장소 소개글',
    homepage   VARCHAR(500) NULL     COMMENT '홈페이지 URL (HTML 태그 포함 가능)',
    telname    VARCHAR(100) NULL     COMMENT '전화번호 명칭',
    synced_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                     ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (content_id),
    FOREIGN KEY fk_detail_common (content_id) REFERENCES attraction(content_id) ON DELETE CASCADE
) COMMENT='detailCommon2 추가 필드 (overview·homepage·telname)'
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 4-2. 관광지 유형별 소개 (attraction_detail_intro) — detailIntro2
-- ---------------------------------------------
CREATE TABLE attraction_detail_intro (
    content_id      VARCHAR(20) NOT NULL COMMENT 'TourAPI contentid (FK)',
    content_type_id TINYINT     NOT NULL COMMENT 'contenttypeid (JSON 해석 기준)',
    intro_data      JSON        NULL     COMMENT 'contenttypeid별 소개 필드 전체 JSON',
    synced_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
                                         ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (content_id),
    FOREIGN KEY fk_detail_intro (content_id) REFERENCES attraction(content_id) ON DELETE CASCADE
) COMMENT='detailIntro2 응답 전체 JSON 저장. 검색 조건 필요 시 컬럼 추가 마이그레이션'
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 4-3. 관광지 이미지 (attraction_detail_image) — detailImage2
-- ---------------------------------------------
CREATE TABLE attraction_detail_image (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    content_id     VARCHAR(20)  NOT NULL COMMENT 'TourAPI contentid (FK)',
    serialnum      VARCHAR(10)  NULL     COMMENT '이미지 순번',
    originimgurl   VARCHAR(500) NULL     COMMENT '원본 이미지 URL',
    smallimageurl  VARCHAR(500) NULL     COMMENT '썸네일 이미지 URL',
    imgname        VARCHAR(200) NULL     COMMENT '이미지 파일명',
    cpyrht_div_cd  VARCHAR(10)  NULL     COMMENT '저작권 구분 코드',
    PRIMARY KEY (id),
    INDEX idx_image_content (content_id),
    FOREIGN KEY fk_detail_image (content_id) REFERENCES attraction(content_id) ON DELETE CASCADE
) COMMENT='detailImage2 이미지 목록'
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 4-4. 관광지 이용 안내 (attraction_detail_info) — detailInfo2
-- ---------------------------------------------
CREATE TABLE attraction_detail_info (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    content_id        VARCHAR(20)  NOT NULL COMMENT 'TourAPI contentid (FK)',
    serialnum         VARCHAR(10)  NULL     COMMENT '항목 순번',
    fldgubun          VARCHAR(10)  NULL     COMMENT '필드 구분',
    infoname          VARCHAR(200) NULL     COMMENT '안내 항목명',
    infotext          TEXT         NULL     COMMENT '안내 내용',
    subcontentid      VARCHAR(20)  NULL     COMMENT '서브 콘텐츠 ID (여행코스)',
    subdetailalt      VARCHAR(200) NULL,
    subdetailimg      VARCHAR(500) NULL,
    subdetailoverview TEXT         NULL,
    subname           VARCHAR(200) NULL,
    subnum            VARCHAR(10)  NULL,
    room_data         JSON         NULL     COMMENT '숙박(contenttypeid=32) 객실 정보 JSON (room* 필드)',
    PRIMARY KEY (id),
    INDEX idx_info_content (content_id),
    FOREIGN KEY fk_detail_info (content_id) REFERENCES attraction(content_id) ON DELETE CASCADE
) COMMENT='detailInfo2 이용 안내 항목. 숙박 객실 정보는 room_data JSON 컬럼에 저장'
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 4. 즐겨찾기 (favorite)
-- ---------------------------------------------
CREATE TABLE favorite (
    id            BIGINT    NOT NULL AUTO_INCREMENT,
    member_id     BIGINT    NOT NULL COMMENT '회원 FK',
    attraction_id BIGINT    NOT NULL COMMENT '관광지 FK',
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_favorite (member_id, attraction_id),
    FOREIGN KEY fk_favorite_member     (member_id)     REFERENCES member(id)     ON DELETE CASCADE,
    FOREIGN KEY fk_favorite_attraction (attraction_id) REFERENCES attraction(id) ON DELETE CASCADE
) COMMENT='즐겨찾기. 해제해도 trip_candidate는 유지 (앱 레이어에서 별도 처리 없음)'
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 5. 여행 일정 (trip)
-- ---------------------------------------------
CREATE TABLE trip (
    id                    BIGINT      NOT NULL AUTO_INCREMENT,
    member_id             BIGINT      NOT NULL COMMENT '소유 회원 FK',
    title                 VARCHAR(30) NOT NULL COMMENT '여행 제목 (최대 30자)',
    start_date            DATE        NOT NULL COMMENT '출발일',
    end_date              DATE        NOT NULL COMMENT '귀환일',
    member_count          TINYINT     NOT NULL DEFAULT 1  COMMENT '여행 인원',
    default_transit_mode  VARCHAR(20) NOT NULL DEFAULT 'PUBLIC_TRANSIT'
                                               COMMENT '신규 블록 배치 시 기본 이동수단 (PUBLIC_TRANSIT·DRIVING·WALKING)',
    is_public             TINYINT(1)  NOT NULL DEFAULT 0  COMMENT '커뮤니티 공유 여부 (0:비공개 1:공개)',
    created_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_trip_member (member_id),
    FOREIGN KEY fk_trip_member (member_id) REFERENCES member(id) ON DELETE CASCADE
) COMMENT='여행 일정. Day 탭은 start_date~end_date 범위를 런타임 계산'
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 6. 후보군 (trip_candidate)
-- ---------------------------------------------
CREATE TABLE trip_candidate (
    id            BIGINT                       NOT NULL AUTO_INCREMENT,
    trip_id       BIGINT                       NOT NULL COMMENT '일정 FK',
    attraction_id BIGINT                       NOT NULL COMMENT '관광지 FK',
    city_code     TINYINT                      NOT NULL COMMENT '도시 분류 기준 (attraction.sigungu_code)',
    source        ENUM('MANUAL', 'FAVORITE')   NOT NULL DEFAULT 'MANUAL'
                                               COMMENT '추가 경로: MANUAL=직접추가 FAVORITE=즐겨찾기 자동연동',
    added_at      TIMESTAMP                    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_candidate (trip_id, attraction_id),
    FOREIGN KEY fk_candidate_trip       (trip_id)       REFERENCES trip(id)       ON DELETE CASCADE,
    FOREIGN KEY fk_candidate_attraction (attraction_id) REFERENCES attraction(id) ON DELETE CASCADE
) COMMENT='일정 후보군. 탐색 화면에서 장소 추가 시 생성. trip_block 존재 시 삭제 불가 (RESTRICT)'
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 7. 타임라인 확정 블록 (trip_block)
-- ---------------------------------------------
CREATE TABLE trip_block (
    id                       BIGINT      NOT NULL AUTO_INCREMENT,
    candidate_id             BIGINT      NOT NULL COMMENT '후보군 FK',
    trip_date                DATE        NOT NULL COMMENT '배치된 날짜 (trip.start_date~end_date 범위, TRIGGER 검증)',
    display_order            TINYINT     NOT NULL COMMENT '해당 날짜 내 표시 순서 (1부터, 앱 레이어 관리)',
    start_time               TIME        NULL     COMMENT '시작 시간 (NULL=미확정)',
    duration_minutes         SMALLINT    NOT NULL DEFAULT 60 COMMENT '체류 시간(분). end_time = start_time + duration_minutes',
    transport_preference     TINYINT     NOT NULL DEFAULT 0
                                                  COMMENT '이전 장소→이 장소 이동수단 0=대중교통전체 1=지하철 2=버스 3=자동차',
    memo                     TEXT        NULL     COMMENT '장소 메모',
    transit_duration_minutes SMALLINT    NULL     COMMENT '이전 블록→이 블록 이동 시간(분). 첫 블록은 NULL',
    transit_mode             VARCHAR(100) NULL    COMMENT '이동 수단 목록 (콤마 구분, 예: EXPRESSBUS,RAIL). 첫 블록은 NULL',
    created_at               TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_block_candidate (candidate_id),
    INDEX idx_block_date      (trip_date),
    -- RESTRICT: trip_candidate 삭제 전 모달 확인 UX 보장
    FOREIGN KEY fk_block_candidate (candidate_id) REFERENCES trip_candidate(id) ON DELETE RESTRICT
) COMMENT='타임라인 확정 블록. CandidateCard 드롭 시 생성. display_order 정합성은 앱 레이어 담당'
  DEFAULT CHARSET = utf8mb4;

-- trip_block 날짜 범위 검증 TRIGGER
DELIMITER $$

CREATE TRIGGER trg_trip_block_date_insert
BEFORE INSERT ON trip_block
FOR EACH ROW
BEGIN
    DECLARE v_start DATE;
    DECLARE v_end   DATE;

    SELECT t.start_date, t.end_date
    INTO   v_start, v_end
    FROM   trip t
    JOIN   trip_candidate tc ON tc.trip_id = t.id
    WHERE  tc.id = NEW.candidate_id;

    IF NEW.trip_date < v_start OR NEW.trip_date > v_end THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = '블록 날짜가 여행 기간을 벗어났습니다.';
    END IF;
END$$

CREATE TRIGGER trg_trip_block_date_update
BEFORE UPDATE ON trip_block
FOR EACH ROW
BEGIN
    DECLARE v_start DATE;
    DECLARE v_end   DATE;

    SELECT t.start_date, t.end_date
    INTO   v_start, v_end
    FROM   trip t
    JOIN   trip_candidate tc ON tc.trip_id = t.id
    WHERE  tc.id = NEW.candidate_id;

    IF NEW.trip_date < v_start OR NEW.trip_date > v_end THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = '블록 날짜가 여행 기간을 벗어났습니다.';
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------
-- 8. 이동 시간 캐시 (transit_cache)
-- ---------------------------------------------
CREATE TABLE transit_cache (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    from_attraction_id BIGINT       NOT NULL COMMENT '출발지 FK',
    to_attraction_id   BIGINT       NOT NULL COMMENT '도착지 FK',
    departure_hour     TINYINT      NOT NULL COMMENT '출발 시(0~23)',
    request_mode       VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC_TRANSIT'
                                            COMMENT '요청 이동수단 모드 (PUBLIC_TRANSIT·DRIVING·WALKING)',
    duration_minutes   SMALLINT     NOT NULL COMMENT '소요 시간(분)',
    transport_mode     VARCHAR(100) NOT NULL COMMENT '실제 이동 수단 목록 (콤마 구분, 예: EXPRESSBUS,RAIL·CAR·WALK)',
    transfer_count     TINYINT UNSIGNED NULL  COMMENT '환승 횟수 (대중교통만)',
    fare               INT UNSIGNED NULL      COMMENT '요금(원) — ODsay info.payment',
    taxi_fare          INT UNSIGNED NULL      COMMENT '택시 예상 요금(원) — T Map taxiFare (DRIVING 모드)',
    total_distance_m   INT UNSIGNED NULL      COMMENT '총 이동 거리(m)',
    total_walk_m       INT UNSIGNED NULL      COMMENT '도보 거리(m) (대중교통만)',
    path_detail        JSON         NULL      COMMENT 'ODsay path[0] 전체 JSON (대중교통 subPath 포함)',
    route_coords       MEDIUMTEXT   NULL      COMMENT '경로 좌표 JSON ([lng,lat] 배열) — T Map GeoJSON LineString (DRIVING·WALKING)',
    cached_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_transit (from_attraction_id, to_attraction_id, departure_hour, request_mode),
    FOREIGN KEY fk_transit_from (from_attraction_id) REFERENCES attraction(id) ON DELETE CASCADE,
    FOREIGN KEY fk_transit_to   (to_attraction_id)   REFERENCES attraction(id) ON DELETE CASCADE
) COMMENT='이동 시간 API 응답 캐시 (ODsay 대중교통·T Map 자동차·도보). (from, to, hour, mode) 키'
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 9. 시스템 설정 (system_config)
-- ---------------------------------------------
CREATE TABLE system_config (
    config_key   VARCHAR(60)  NOT NULL COMMENT '설정 키',
    config_value VARCHAR(200) NOT NULL COMMENT '설정 값',
    description  VARCHAR(200) NULL     COMMENT '관리자 화면 설명',
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (config_key)
) COMMENT='관리자 시스템 설정'
  DEFAULT CHARSET = utf8mb4;

INSERT INTO system_config (config_key, config_value, description) VALUES
-- 캐시 활성화 / 레벨
('transit_cache_enabled',      '1',    '이동시간 캐시 활성화 0=비활성 1=활성'),
('transit_cache_level',        '3',    '캐시 정밀도 레벨 1=시간무시 2=러시/비러시 3=출퇴근구분 4=5구간 5=시간별'),
-- 러시아워 경계 (레벨 2~4 공통)
('transit_rush_morning_start', '07:00','출근 러시 시작'),
('transit_rush_morning_end',   '09:00','출근 러시 종료'),
('transit_rush_evening_start', '17:00','퇴근 러시 시작'),
('transit_rush_evening_end',   '20:00','퇴근 러시 종료'),
-- 레벨 4 추가 경계
('transit_dawn_end',           '07:00','새벽 종료 (레벨 4용)'),
('transit_night_start',        '20:00','야간 시작 (레벨 4용)'),
-- 레벨별 대표 시간 (departure_hour 캐시 키 결정)
('transit_level1_hour',          '10', '레벨 1 대표 시간'),
('transit_level2_rush_hour',     '8',  '레벨 2 러시 대표 시간'),
('transit_level2_nonrush_hour',  '12', '레벨 2 비러시 대표 시간'),
('transit_level3_morning_hour',  '8',  '레벨 3 출근러시 대표 시간'),
('transit_level3_evening_hour',  '18', '레벨 3 퇴근러시 대표 시간'),
('transit_level3_other_hour',    '12', '레벨 3 그 외 대표 시간'),
('transit_level4_dawn_hour',     '4',  '레벨 4 새벽 대표 시간'),
('transit_level4_morning_hour',  '8',  '레벨 4 출근러시 대표 시간'),
('transit_level4_daytime_hour',  '12', '레벨 4 평시 대표 시간'),
('transit_level4_evening_hour',  '18', '레벨 4 퇴근러시 대표 시간'),
('transit_level4_night_hour',    '22', '레벨 4 야간 대표 시간');

-- ---------------------------------------------
-- 10. 커뮤니티 게시글 (post)
-- ---------------------------------------------
CREATE TABLE post (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NULL     COMMENT '작성자 FK (탈퇴 시 NULL → 탈퇴한 사용자 표시)',
    trip_id    BIGINT       NULL     COMMENT '공유된 일정 FK (일정 삭제 시 NULL, 게시글 보존)',
    title      VARCHAR(100) NOT NULL COMMENT '게시글 제목',
    content    TEXT         NULL     COMMENT '본문',
    view_count INT          NOT NULL DEFAULT 0 COMMENT '조회수',
    like_count INT          NOT NULL DEFAULT 0 COMMENT '좋아요 수 (post_like 집계 캐시)',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_post_member (member_id),
    INDEX idx_post_trip   (trip_id),
    -- 일정 삭제 시 게시글 보존 (trip_id = NULL)
    FOREIGN KEY fk_post_member (member_id) REFERENCES member(id) ON DELETE SET NULL,
    FOREIGN KEY fk_post_trip   (trip_id)   REFERENCES trip(id)   ON DELETE SET NULL
) COMMENT='여행 일정 공유 게시글. trip 삭제 시 게시글 보존. 일정 공유 중이면 trip 삭제 불가 (앱 레이어)'
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 11. 좋아요 (post_like)
-- ---------------------------------------------
CREATE TABLE post_like (
    id         BIGINT    NOT NULL AUTO_INCREMENT,
    post_id    BIGINT    NOT NULL COMMENT '게시글 FK',
    member_id  BIGINT    NOT NULL COMMENT '좋아요 누른 회원 FK',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_post_like (post_id, member_id),
    FOREIGN KEY fk_like_post   (post_id)   REFERENCES post(id)   ON DELETE CASCADE,
    FOREIGN KEY fk_like_member (member_id) REFERENCES member(id) ON DELETE CASCADE
) COMMENT='게시글 좋아요 (회원당 1회). 추가/취소 시 post.like_count 함께 업데이트'
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 12. 공지사항 (notice)
-- ---------------------------------------------
CREATE TABLE notice (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NULL     COMMENT '작성 관리자 FK (계정 삭제 시 NULL, 공지 보존)',
    title      VARCHAR(100) NOT NULL COMMENT '공지 제목',
    content    TEXT         NOT NULL COMMENT '공지 내용',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_notice_created (created_at DESC),
    FOREIGN KEY fk_notice_member (member_id) REFERENCES member(id) ON DELETE SET NULL
) COMMENT='관리자 공지사항. 최신 5건 커뮤니티 사이드바 표시'
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 13. 게시글 댓글 (post_comment)
-- ---------------------------------------------
CREATE TABLE post_comment (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    post_id    BIGINT        NOT NULL COMMENT '게시글 FK',
    member_id  BIGINT        NULL     COMMENT '작성자 FK (탈퇴 시 NULL → 탈퇴한 사용자 표시, 댓글 보존)',
    content    VARCHAR(1000) NOT NULL COMMENT '댓글 내용 (최대 1000자)',
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_comment_post   (post_id),
    INDEX idx_comment_member (member_id),
    FOREIGN KEY fk_comment_post   (post_id)   REFERENCES post(id)   ON DELETE CASCADE,
    FOREIGN KEY fk_comment_member (member_id) REFERENCES member(id) ON DELETE SET NULL
) COMMENT='게시글 댓글. 게시글 삭제 시 CASCADE 삭제. 탈퇴 시 댓글 내용 보존'
  DEFAULT CHARSET = utf8mb4;
