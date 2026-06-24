-- =============================================
-- TripCraft Schema DDL v0.5
-- 기준: 요구사항 명세 v0.1 / 기획서 v0.2
-- v0.2 변경: transit_cache ODsay 응답 전체 저장 구조로 재설계,
--            trip_block에 transit 표시 컬럼(transit_duration_minutes, transit_mode) 추가
-- v0.3 변경: 이동수단 모드 확장 (PUBLIC_TRANSIT / DRIVING / WALKING)
--            trip에 default_transit_mode 추가
--            transit_cache에 request_mode·taxi_fare·route_coords 추가,
--            UNIQUE KEY에 request_mode 포함 (모드별 독립 캐시)
-- v0.4 변경: post_comment에 parent_id 추가 (대댓글 1단계 지원)
-- v0.5 변경: attach 테이블 신설 (프로필·게시글 이미지 메타데이터 관리)
--            post에 deleted_at 추가 (소프트 딜리트)
--            post_bookmark 테이블 신설
--            → migration_comment_parent.sql 참조
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
    id              BIGINT                NOT NULL AUTO_INCREMENT COMMENT '회원 PK',
    email           VARCHAR(100)          NULL COMMENT '로그인 이메일 (소셜 계정은 NULL 가능)',
    nickname        VARCHAR(20)           NOT NULL COMMENT '닉네임 (2~20자)',
    password        VARCHAR(255)          NULL COMMENT 'BCrypt 해시 (소셜 전용 계정은 NULL)',
    role            ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER' COMMENT '권한',
    social_provider VARCHAR(20)           NULL COMMENT '소셜 로그인 제공자 (kakao 등)',
    social_id       VARCHAR(100)          NULL COMMENT '소셜 제공자의 사용자 고유 ID',
    created_at      TIMESTAMP             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP             NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_member_email (email),
    UNIQUE KEY uq_member_social (social_provider, social_id)
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
-- 3-1. 시도 참조 (sido) — TourAPI areaCode2 기준
-- name=공식명(동기화가 갱신), alias=표시용 짧은 이름(수정 가능, 없으면 name 표시)
-- ---------------------------------------------
CREATE TABLE sido (
    sido_code TINYINT     NOT NULL COMMENT '시도 코드 (TourAPI areaCode2: 1=서울 … 39=제주)',
    name      VARCHAR(50) NOT NULL COMMENT '공식명 (TourAPI areaCode2, 동기화가 갱신)',
    alias     VARCHAR(50) NULL     COMMENT '표시용 짧은 이름 (수정 가능, 없으면 name)',
    PRIMARY KEY (sido_code)
) DEFAULT CHARSET = utf8mb4;

-- 시도 시드 (name=공식명, alias=짧은 이름). TourAPI 동기화가 이후 name 최신화.
INSERT INTO sido (sido_code, name, alias) VALUES
(1,'서울','서울'),(2,'인천','인천'),(3,'대전','대전'),(4,'대구','대구'),(5,'광주','광주'),
(6,'부산','부산'),(7,'울산','울산'),(8,'세종특별자치시','세종'),
(31,'경기도','경기'),(32,'강원특별자치도','강원'),(33,'충청북도','충북'),(34,'충청남도','충남'),
(35,'경상북도','경북'),(36,'경상남도','경남'),(37,'전북특별자치도','전북'),(38,'전라남도','전남'),
(39,'제주특별자치도','제주');

-- ---------------------------------------------
-- 3-2. 시군구 참조 (sigungu) — TourAPI areaCode2 기준
-- ---------------------------------------------
CREATE TABLE sigungu (
    sido_code    TINYINT     NOT NULL COMMENT '시도 코드 (1=서울, 31=경기 등)',
    sigungu_code TINYINT     NOT NULL COMMENT '시도 내 시군구 코드',
    name         VARCHAR(50) NOT NULL COMMENT '공식명 (TourAPI areaCode2, 동기화가 갱신)',
    alias        VARCHAR(50) NULL     COMMENT '표시용 이름 (수정 가능, 없으면 name)',
    PRIMARY KEY (sido_code, sigungu_code)
) DEFAULT CHARSET = utf8mb4;

-- 시군구 시드 데이터 (TourAPI 실제 attraction 데이터 기준, 가나다 코드 순서)
-- 출처: migration_sigungu_v1.sql — schema.sql 단독 셋업을 위해 통합
INSERT INTO sigungu (sido_code, sigungu_code, name) VALUES
-- 서울(1): 25구 — TourAPI 가나다 순
(1,1,'강남구'),(1,2,'강동구'),(1,3,'강북구'),(1,4,'강서구'),(1,5,'관악구'),
(1,6,'광진구'),(1,7,'구로구'),(1,8,'금천구'),(1,9,'노원구'),(1,10,'도봉구'),
(1,11,'동대문구'),(1,12,'동작구'),(1,13,'마포구'),(1,14,'서대문구'),(1,15,'서초구'),
(1,16,'성동구'),(1,17,'성북구'),(1,18,'송파구'),(1,19,'양천구'),(1,20,'영등포구'),
(1,21,'용산구'),(1,22,'은평구'),(1,23,'종로구'),(1,24,'중구'),(1,25,'중랑구'),

-- 인천(2): 8구 2군
(2,1,'강화군'),(2,2,'계양구'),(2,3,'미추홀구'),(2,4,'남동구'),(2,5,'동구'),
(2,6,'부평구'),(2,7,'서구'),(2,8,'연수구'),(2,9,'옹진군'),(2,10,'중구'),

-- 대전(3): 5구
(3,1,'대덕구'),(3,2,'동구'),(3,3,'서구'),(3,4,'유성구'),(3,5,'중구'),

-- 대구(4): 7구 1군 + 군위군(편입)
(4,1,'남구'),(4,2,'달서구'),(4,3,'달성군'),(4,4,'동구'),(4,5,'북구'),
(4,6,'서구'),(4,7,'수성구'),(4,8,'중구'),(4,9,'군위군'),

-- 광주(5): 5구
(5,1,'광산구'),(5,2,'남구'),(5,3,'동구'),(5,4,'북구'),(5,5,'서구'),

-- 부산(6): 15구 1군
(6,1,'강서구'),(6,2,'금정구'),(6,3,'기장군'),(6,4,'남구'),(6,5,'동구'),
(6,6,'동래구'),(6,7,'부산진구'),(6,8,'북구'),(6,9,'사상구'),(6,10,'사하구'),
(6,11,'서구'),(6,12,'수영구'),(6,13,'연제구'),(6,14,'영도구'),(6,15,'중구'),
(6,16,'해운대구'),

-- 울산(7): 4구 1군
(7,1,'중구'),(7,2,'남구'),(7,3,'동구'),(7,4,'북구'),(7,5,'울주군'),

-- 세종(8): 세종특별자치시 (시군구 없음)
(8,1,'세종시'),

-- 경기(31): 28시 3군
(31,1,'가평군'),(31,2,'고양시'),(31,3,'과천시'),(31,4,'광명시'),(31,5,'광주시'),
(31,6,'구리시'),(31,7,'군포시'),(31,8,'김포시'),(31,9,'남양주시'),(31,10,'동두천시'),
(31,11,'부천시'),(31,12,'성남시'),(31,13,'수원시'),(31,14,'시흥시'),(31,15,'안산시'),
(31,16,'안성시'),(31,17,'안양시'),(31,18,'양주시'),(31,19,'양평군'),(31,20,'여주시'),
(31,21,'연천군'),(31,22,'오산시'),(31,23,'용인시'),(31,24,'의왕시'),(31,25,'의정부시'),
(31,26,'이천시'),(31,27,'파주시'),(31,28,'평택시'),(31,29,'포천시'),(31,30,'하남시'),
(31,31,'화성시'),

-- 강원(32): 7시 11군
(32,1,'강릉시'),(32,2,'고성군'),(32,3,'동해시'),(32,4,'삼척시'),(32,5,'속초시'),
(32,6,'양구군'),(32,7,'양양군'),(32,8,'영월군'),(32,9,'원주시'),(32,10,'인제군'),
(32,11,'정선군'),(32,12,'철원군'),(32,13,'춘천시'),(32,14,'태백시'),(32,15,'평창군'),
(32,16,'홍천군'),(32,17,'화천군'),(32,18,'횡성군'),

-- 충북(33): 3시 8군 (code 9 공백 — 청원군 청주시 편입)
(33,1,'괴산군'),(33,2,'단양군'),(33,3,'보은군'),(33,4,'영동군'),(33,5,'옥천군'),
(33,6,'음성군'),(33,7,'제천시'),(33,8,'진천군'),(33,10,'청주시'),(33,11,'충주시'),
(33,12,'증평군'),

-- 충남(34): 8시 7군 (code 10 공백 — 연기군 세종시 편입)
(34,1,'공주시'),(34,2,'금산군'),(34,3,'논산시'),(34,4,'당진시'),(34,5,'보령시'),
(34,6,'부여군'),(34,7,'서산시'),(34,8,'서천군'),(34,9,'아산시'),(34,11,'예산군'),
(34,12,'천안시'),(34,13,'청양군'),(34,14,'태안군'),(34,15,'홍성군'),(34,16,'계룡시'),

-- 경북(35): 10시 13군 (code 5 공백)
(35,1,'경산시'),(35,2,'경주시'),(35,3,'고령군'),(35,4,'구미시'),(35,6,'김천시'),
(35,7,'문경시'),(35,8,'봉화군'),(35,9,'상주시'),(35,10,'성주군'),(35,11,'안동시'),
(35,12,'영덕군'),(35,13,'영양군'),(35,14,'영주시'),(35,15,'영천시'),(35,16,'예천군'),
(35,17,'울릉군'),(35,18,'울진군'),(35,19,'의성군'),(35,20,'청도군'),(35,21,'청송군'),
(35,22,'칠곡군'),(35,23,'포항시'),

-- 경남(36): 8시 10군 (codes 6·11·14 공백)
(36,1,'거제시'),(36,2,'거창군'),(36,3,'고성군'),(36,4,'김해시'),(36,5,'남해군'),
(36,7,'밀양시'),(36,8,'사천시'),(36,9,'산청군'),(36,10,'양산시'),(36,12,'의령군'),
(36,13,'진주시'),(36,15,'창녕군'),(36,16,'창원시'),(36,17,'통영시'),(36,18,'하동군'),
(36,19,'함안군'),(36,20,'함양군'),(36,21,'합천군'),

-- 전북(37): 6시 8군
(37,1,'고창군'),(37,2,'군산시'),(37,3,'김제시'),(37,4,'남원시'),(37,5,'무주군'),
(37,6,'부안군'),(37,7,'순창군'),(37,8,'완주군'),(37,9,'익산시'),(37,10,'임실군'),
(37,11,'장수군'),(37,12,'전주시'),(37,13,'정읍시'),(37,14,'진안군'),

-- 전남(38): 5시 17군 (codes 14·15 공백)
(38,1,'강진군'),(38,2,'고흥군'),(38,3,'곡성군'),(38,4,'광양시'),(38,5,'구례군'),
(38,6,'나주시'),(38,7,'담양군'),(38,8,'목포시'),(38,9,'무안군'),(38,10,'보성군'),
(38,11,'순천시'),(38,12,'신안군'),(38,13,'여수시'),(38,16,'영광군'),(38,17,'영암군'),
(38,18,'완도군'),(38,19,'장성군'),(38,20,'장흥군'),(38,21,'진도군'),(38,22,'함평군'),
(38,23,'해남군'),(38,24,'화순군'),

-- 제주(39): 2시 (code 1·2 공백 — TourAPI는 3·4 사용)
(39,3,'서귀포시'),(39,4,'제주시');

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
    share_access          ENUM('PRIVATE','VIEW','EDIT') NOT NULL DEFAULT 'PRIVATE'
                                               COMMENT '공유 링크 접근 레벨 (migration_trip_share_link_v1)',
    share_token           CHAR(22)    NULL UNIQUE COMMENT '공유 링크 랜덤 토큰(URL-safe). NULL=미생성',
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
    attraction_id BIGINT                       NULL COMMENT '관광지 FK (커스텀 장소면 NULL)',
    city_code     TINYINT                      NULL COMMENT '도시 분류 기준 (커스텀이면 NULL)',
    source        ENUM('MANUAL', 'FAVORITE', 'CUSTOM') NOT NULL DEFAULT 'MANUAL'
                                               COMMENT '추가 경로: MANUAL/FAVORITE/CUSTOM(커스텀 장소)',
    -- 커스텀 장소(attraction_id NULL)용 필드 (migration_my_place_v1)
    place_name     VARCHAR(100)  NULL COMMENT '커스텀 장소명',
    place_category VARCHAR(20)   NULL COMMENT '커스텀 분류',
    place_address  VARCHAR(255)  NULL COMMENT '커스텀 주소',
    place_lat      DECIMAL(10,7) NULL COMMENT '커스텀 위도',
    place_lng      DECIMAL(10,7) NULL COMMENT '커스텀 경도',
    added_at      TIMESTAMP                    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_candidate (trip_id, attraction_id),
    FOREIGN KEY fk_candidate_trip       (trip_id)       REFERENCES trip(id)       ON DELETE CASCADE,
    FOREIGN KEY fk_candidate_attraction (attraction_id) REFERENCES attraction(id) ON DELETE CASCADE
) COMMENT='일정 후보군. 탐색/커스텀 장소 추가 시 생성. trip_block 존재 시 삭제 불가 (RESTRICT)'
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 6-1. 개인 장소 (member_place) — migration_my_place_v1
-- ---------------------------------------------
CREATE TABLE member_place (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NOT NULL COMMENT '소유 회원 FK',
    name       VARCHAR(100) NOT NULL COMMENT '장소명',
    category   VARCHAR(20)  NOT NULL COMMENT '분류(관광지·문화시설·레포츠·숙박·쇼핑·음식점)',
    address    VARCHAR(255) NULL     COMMENT '주소',
    latitude   DECIMAL(10,7) NULL,
    longitude  DECIMAL(10,7) NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_member_place_member (member_id),
    FOREIGN KEY fk_member_place_member (member_id) REFERENCES member(id) ON DELETE CASCADE
) COMMENT='회원 개인 커스텀 장소(재사용)' DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 6-2. 일정 협업자 (trip_collaborator) — migration_collab_v1
-- ---------------------------------------------
CREATE TABLE trip_collaborator (
    id         BIGINT                   NOT NULL AUTO_INCREMENT,
    trip_id    BIGINT                   NOT NULL,
    member_id  BIGINT                   NOT NULL,
    role       ENUM('EDITOR','VIEWER')  NOT NULL DEFAULT 'EDITOR',
    invited_at TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_collaborator (trip_id, member_id),
    FOREIGN KEY fk_collab_trip   (trip_id)   REFERENCES trip(id)   ON DELETE CASCADE,
    FOREIGN KEY fk_collab_member (member_id) REFERENCES member(id) ON DELETE CASCADE
) COMMENT='일정 협업자. 소유자(trip.member_id)와 별도. 역할: EDITOR(조회+편집)/VIEWER(조회만)'
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
    transit_option_index     INT         NULL     COMMENT '선택된 경로 인덱스 (대중교통 pathIndex 또는 자동차 옵션 0~3)',
    version                  INT         NOT NULL DEFAULT 0 COMMENT '낙관적 락 버전. 사용자 편집 시 +1. transit 재계산은 미변경',
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
    route_key          VARCHAR(160) NULL COMMENT '좌표 기반 캐시 키 (migration_transit_coord_key_v1)',
    from_attraction_id BIGINT       NULL COMMENT '(미사용, 좌표키로 전환)',
    to_attraction_id   BIGINT       NULL COMMENT '(미사용, 좌표키로 전환)',
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
    UNIQUE KEY uq_transit_route (route_key)
) COMMENT='이동 시간 API 응답 캐시 (ODsay 대중교통·T Map 자동차·도보). route_key(좌표) 키 — attraction·커스텀 공통'
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
    deleted_at DATETIME     NULL     COMMENT '삭제일시 (NULL=정상, non-NULL=소프트 딜리트됨)',
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
-- v0.4: parent_id 추가 — 대댓글 1단계 지원
--       NULL = 최상위 댓글, non-NULL = 대댓글
--       부모 삭제 시 ON DELETE CASCADE로 대댓글도 함께 삭제
-- ---------------------------------------------
CREATE TABLE post_comment (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    post_id    BIGINT        NOT NULL COMMENT '게시글 FK',
    member_id  BIGINT        NULL     COMMENT '작성자 FK (탈퇴 시 NULL → 탈퇴한 사용자 표시, 댓글 보존)',
    parent_id  BIGINT        NULL     COMMENT '부모 댓글 FK (NULL=최상위, non-NULL=대댓글 — 1단계만 허용)',
    content    VARCHAR(1000) NOT NULL COMMENT '댓글 내용 (최대 1000자)',
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_comment_post   (post_id),
    INDEX idx_comment_member (member_id),
    FOREIGN KEY fk_comment_post   (post_id)   REFERENCES post(id)        ON DELETE CASCADE,
    FOREIGN KEY fk_comment_member (member_id) REFERENCES member(id)      ON DELETE SET NULL,
    FOREIGN KEY fk_comment_parent (parent_id) REFERENCES post_comment(id) ON DELETE CASCADE
) COMMENT='게시글 댓글·대댓글. parent_id NULL=최상위, non-NULL=대댓글(1단계). 부모 삭제 시 대댓글도 CASCADE'
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 14. 노선 폴리라인 캐시 (lane_polyline)
-- ODsay loadLane API 결과를 노선 단위로 캐싱.
-- 노선 형상은 자주 바뀌지 않으므로 영구 보관.
-- map_object_key: searchPubTransPathT 응답의 info.mapObj 값
-- ---------------------------------------------
CREATE TABLE lane_polyline (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    map_object_key VARCHAR(250) NOT NULL COMMENT 'ODsay info.mapObj 값 (캐시 키)',
    route_coords   MEDIUMTEXT   NULL     COMMENT '파싱된 좌표 JSON [[lng,lat],...], null이면 API 결과 없음',
    raw_response   MEDIUMTEXT   NULL     COMMENT 'ODsay loadLane 원본 응답 JSON',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_map_object_key (map_object_key)
) COMMENT='ODsay loadLane 노선 폴리라인 캐시. 노선 형상 변경이 드물어 영구 보관.'
  DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 15. 북마크 (post_bookmark)
-- post_id FK에 CASCADE 없음 — 글 소프트 딜리트 시 북마크 레코드 보존.
-- 북마크 목록에서 deleted_at IS NOT NULL이면 "삭제된 글입니다" 표시.
-- ---------------------------------------------
CREATE TABLE post_bookmark (
    member_id  BIGINT   NOT NULL COMMENT '북마크한 회원 FK',
    post_id    BIGINT   NOT NULL COMMENT '북마크된 게시글 FK',
    created_at DATETIME NOT NULL DEFAULT NOW() COMMENT '북마크 일시',
    PRIMARY KEY (member_id, post_id),
    FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    FOREIGN KEY (post_id)   REFERENCES post (id)
) DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 16. 첨부파일 (attach)
-- 프로필 이미지, 게시글 이미지 메타데이터 통합 관리.
-- 파일 자체는 서버 디스크에 저장, DB에는 경로 등 메타데이터만 보관.
-- target='post_draft': 글 작성 중 임시 저장 (target_id=memberId — 업로더 식별용)
-- target='post'      : 게시글 확정 후 (target_id=post.id)
-- target='profile'   : 프로필 이미지 (target_id=member.id)
-- host_name/mimetype/host_path는 NULL 허용 — Spring MultipartFile.getOriginalFilename() 등이 null 반환 가능
-- 최신 마이그레이션: migration_attach.sql (migration_attach_softdelete.sql은 MR#5 이전 버전)
-- ---------------------------------------------
CREATE TABLE attach (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(255) NOT NULL COMMENT '저장 파일명 (UUID.ext)',
    host_name  VARCHAR(255) NULL     COMMENT '원본 파일명',
    size       BIGINT       NOT NULL DEFAULT 0,
    mimetype   VARCHAR(100) NULL,
    host_path  VARCHAR(500) NULL     COMMENT '서버 절대 경로 (파일 삭제 시 사용)',
    target     VARCHAR(20)  NOT NULL COMMENT 'profile | post | post_draft',
    target_id  BIGINT       NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_attach_target (target, target_id)
) COMMENT='파일 첨부 메타데이터';
