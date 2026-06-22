-- ============================================================
-- 시도 참조 테이블 생성 및 시드 + 시군구 alias 컬럼 추가
-- 지역 명칭을 TourAPI areaCode2에서 동기화하도록 전환하면서 추가.
-- 기존 DB에 1회 적용. (신규 셋업은 schema.sql에 이미 반영됨)
--
-- name  = TourAPI areaCode2 공식명 (동기화가 갱신, source of truth)
-- alias = 표시용 짧은 이름 (수정 가능, 없으면 name 표시). 동기화는 건드리지 않음.
-- ============================================================

-- 시도 참조 테이블
CREATE TABLE IF NOT EXISTS sido (
    sido_code TINYINT     NOT NULL COMMENT '시도 코드 (TourAPI areaCode2: 1=서울 … 39=제주)',
    name      VARCHAR(50) NOT NULL COMMENT '공식명 (TourAPI areaCode2, 동기화가 갱신)',
    alias     VARCHAR(50) NULL     COMMENT '표시용 짧은 이름 (수정 가능, 없으면 name)',
    PRIMARY KEY (sido_code)
) DEFAULT CHARSET = utf8mb4;

-- 시도 시드 (name=공식명, alias=짧은 이름)
-- 재실행해도 alias(사용자 편집)는 보존하고 name만 갱신
INSERT INTO sido (sido_code, name, alias) VALUES
(1,'서울','서울'),(2,'인천','인천'),(3,'대전','대전'),(4,'대구','대구'),(5,'광주','광주'),
(6,'부산','부산'),(7,'울산','울산'),(8,'세종특별자치시','세종'),
(31,'경기도','경기'),(32,'강원특별자치도','강원'),(33,'충청북도','충북'),(34,'충청남도','충남'),
(35,'경상북도','경북'),(36,'경상남도','경남'),(37,'전북특별자치도','전북'),(38,'전라남도','전남'),
(39,'제주특별자치도','제주')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 시군구 alias 컬럼 추가 (새 코드의 SELECT alias / 표시명 폴백에 필요)
-- 컬럼이 이미 있으면 이 문장은 건너뛸 것.
ALTER TABLE sigungu
    ADD COLUMN alias VARCHAR(50) NULL COMMENT '표시용 이름 (수정 가능, 없으면 name)';
