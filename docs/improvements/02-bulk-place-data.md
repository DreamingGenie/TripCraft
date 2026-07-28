# 개선 02 — 대량 장소 확보 + 대용량 데이터 처리 연습

> 상태: 🔨 구현중 (Phase A 완료) · 최종 갱신: 2026-07-29

## 진행 현황

| Phase | 범위 | 상태 |
|---|---|---|
| **A** | 카카오 로컬 API 수집(§2.1·2.4) + 최소 스키마 확장(§2.3 컬럼분) | ✅ 구현 완료 |
| B | 중단·재개 체크포인트 `crawl_progress`(§2.2) | ⏸ 후속 |
| C | 좌표·상호명 중복 병합 정책(§2.3 병합분) | ⏸ 후속 |
| D | 네이버맵 크롤링 학습 부록(§3) | ⏸ 후속 |
| E | 3레벨 성능 벤치마크(§4) | ⏸ 후속 |

**Phase A 구현물**: `attraction`에 `source`(TOURAPI|KAKAO)·`external_id` 추가, `content_id` NULL 허용,
`UNIQUE(source, external_id)` (`docs/sql/migration_kakao_source.sql`, 스키마 v0.6).
`KakaoLocalClient`(헤더 인증 전용 RestClient) · `KakaoLocalCallLimiter`(쿼터) ·
`KakaoRegionResolver`(address_name→시도/시군구 코드) · `KakaoLocalSyncServiceImpl`(rect 격자+quadtree).
트리거: `POST /api/admin/attractions/sync/kakao`, `/sync/kakao/partial?categoryGroupCode=`.

## 1. 목표 / 데이터 소스 평가

현재 관광지 DB는 한국관광공사 **TourAPI `areaBasedList2`** 로만 수집한다(`attraction` 테이블).
장소 수가 서비스로 다루기엔 적어 **더 많은 장소를 확보**하고 싶고, 그로 인해 생기는 대량 데이터를
**쿼리·아키텍처·적재 3레벨에서 최적화**하며 **성능 개선 before/after 수치를 남기는 것**을 학습 목표로 삼는다.

### 데이터 소스: 카카오 로컬 API 주력 (솔직한 평가)

| 후보 | 평가 | 결론 |
|---|---|---|
| **네이버맵 크롤링** | 이용약관상 자동 수집 금지 → **Public 포트폴리오 저장소엔 특히 부적절**. JS 동적 렌더 + 안티봇으로 난이도 중상. DOM 변경에 취약(유지보수 리스크). | **주력 부적합** |
| **카카오 로컬 REST API** | 공식·무료. 키워드/카테고리 검색 + 좌표 반환. 페이지당 15건 × 최대 3페이지 = **쿼리당 최대 45건**. 카테고리 그룹코드로 유형 필터. | **주력 채택** |

→ **카카오 로컬 API를 대량 확보 주력**으로, **네이버맵 크롤링은 소규모 "학습용 부록"**(§3)으로 방법만 익힌다.

## 2. 카카오 로컬 API 수집 설계

기존 TourAPI 수집 구조(`client`/`service`/`batch`/`controller`)를 **미러링**한다.

### 2.1 수집 컴포넌트

- `client/KakaoLocalClient.java` — Spring `RestClient`(기존 `TourApiClient` 패턴 재사용).
  - `Authorization: KakaoAK {REST_API_KEY}` 헤더.
  - 카테고리 검색(`/v2/local/search/category.json`, `category_group_code` + `rect`) 우선,
    보조로 키워드 검색(`/v2/local/search/keyword.json`).
- 카테고리 그룹코드 순회: 관광명소 `AT4`, 음식점 `FD6`, 카페 `CE7`, 숙박 `AD5`, 문화시설 `CT1` 등.
- **커버리지 전략**: 카카오는 지역명보다 **좌표 사각형(`rect`) 검색**이 강하다.
  시군구 bounding box를 **격자 셀**로 쪼개 각 셀을 rect로 조회한다.
  (한 rect의 결과 상한 45건에 걸리면 셀을 더 잘게 분할 = quadtree 방식 세분화.)

### 2.2 중단·재개 (체크포인트) — 요구사항

TourAPI가 Spring Batch로 재시작 가능하듯, 카카오 수집도 **진행 커서를 DB에 저장**해
**중단 지점 다음부터 재개**한다. 한 번에 이어서 다 돌릴 필요가 없다.

- **수집 단위(커서)** = `(source, category_group_code, grid_cell_index, page)`.
- 단위 완료 시마다 체크포인트 row 갱신 — 전용 `crawl_progress` 테이블(권장) 또는 기존 `system_config` 확장.
  ```
  crawl_progress(
    source, category_code, grid_cell_index, last_page,
    status,        -- DONE | FAILED | IN_PROGRESS
    retry_count, updated_at
  )
  ```
- 재실행 시 **마지막 완료 커서 다음부터** 시작하고 이미 `DONE`인 셀은 skip.
- 업서트 방식이라 중복 재실행도 **idempotent(안전)**.
- 실패 단위는 `FAILED` + `retry_count` 기록 후 스킵 → 후속 재처리 루프에서만 다시 시도.

### 2.3 저장 스키마 확장

기존 `attraction` 스키마를 재사용하되, 카카오는 TourAPI `content_id` 체계와 다르므로 **출처를 구분**한다.

- 마이그레이션(설계): `attraction`에 **출처 컬럼** `source`(enum: `TOURAPI` | `KAKAO`)
  + **외부 ID 컬럼**(예: `external_id` = 카카오 place id) 추가, `(source, external_id)` UNIQUE.
  - 현행 UNIQUE `uq_attraction_content_id`는 TourAPI 전용이므로 카카오 행은 `content_id` NULL 허용으로 조정 검토.
- **중복 병합 정책**: 동일 장소가 두 소스에 존재할 수 있다 → **좌표 근접(예: 30m 이내) + 상호명 정규화 매칭**으로
  중복 후보를 식별하고 대표 레코드를 선정(우선순위·병합 규칙을 문서 부록에 표로 정리).
- 호출 한도: 기존 `TourApiCallLimiter`/`system_config` 카운트 패턴을 카카오용으로 확장(일일 쿼터 관리).

### 2.4 트리거

- 관리자 API(기존 `AttractionSyncController` 패턴): `POST /api/admin/attractions/sync/kakao`(전체/부분).
- 정기 배치는 TourAPI 스케줄러(`batch/TourApiSyncScheduler`)와 동일 틀로 추가하되 기본 비활성.

## 3. 네이버맵 크롤링 — 학습용 부록 (실서비스 파이프라인 아님)

> ⚠️ 이 절은 **크롤링을 배우기 위한 소규모 실습**이다. 수집물은 학습 노트일 뿐 주력 DB 파이프라인에 편입하지 않는다.
> 실제 실행은 소량 샘플로, 라이브 세션에서 함께 진행한다.

크롤링 경험이 없는 사람이 따라올 수 있게 단계별로:

1. **개념·경계**: 크롤링 vs 공식 API 차이. `robots.txt`·이용약관 확인 방법. 법적/윤리 경계와 왜 주력으로 안 쓰는지.
2. **도구 선택**: 정적 페이지면 `requests` + BeautifulSoup, **동적(네이버맵)이면 Playwright**(헤드리스 브라우저) 권장.
   설치·최초 실행(브라우저 바이너리 설치 포함).
3. **내부 API 역추적**: 브라우저 개발자도구 Network 탭에서 지도가 부르는 **내부 XHR/JSON 엔드포인트**를 찾아
   DOM 파싱 대신 그 JSON을 읽는다(가장 안정적인 방법). 요청 헤더·쿼리 구조 관찰.
4. **파싱·순회**: 셀렉터/JSON 필드 추출, 페이지네이션·무한스크롤 처리, 명시적 대기(`wait_for_selector` 등).
5. **매너**: 레이트리밋(요청 간 지연), User-Agent, 차단 회피의 **정당한 범위**와 넘지 말아야 할 선.
6. **중단·재개 실습**: 마지막 처리 페이지/셀을 JSON 파일 또는 DB에 기록 → 재실행 시 resume.
   §2.2 카카오 체크포인트 개념을 크롤링에서도 동일하게 연습한다.
7. **마무리**: 소량 샘플만 수집·파싱 확인. 산출물은 학습 노트(`docs/improvements/notes/` 등)로만 남긴다.

## 4. 대용량 처리 & 성능 벤치마크

### 4.1 측정 프로토콜 (가장 먼저 — baseline "before"를 남기는 게 핵심)

최적화를 시작하기 **전에** 현재 수치를 먼저 기록한다.

- **벤치 대상**:
  1. 키워드 검색 `AttractionServiceImpl.search()` (`title LIKE '%kw%'`)
  2. 지역/카테고리 필터 페이징(`findByCondition`)
  3. 깊은 페이지 `OFFSET`(예: page 500)
  4. 반경 검색 `findNearby`(`ST_Distance_Sphere`)
  5. 배치 업서트 처리량(건/초)
- **지표**: 쿼리 실행시간 p50/p95, `EXPLAIN`의 접근 타입·rows·사용 key, API 왕복 지연, 배치 건/초.
- **방법**: 동일 데이터셋·동일 쿼리로 최적화 전/후를 **반복 측정**(예: 각 20회, 워밍업 제외).
  결과는 아래 표 + [`benchmarks/`](benchmarks/)에 CSV/md로 누적. 재현 스크립트 경로를 명시한다.

측정 결과 표(값은 실측 후 채움):

| 시나리오 | 지표 | Before | After(Lv1) | After(Lv2) | 비고 |
|---|---|---|---|---|---|
| 키워드 검색 | p95(ms) | | | | LIKE→FULLTEXT |
| 깊은 페이징 | p95(ms) | | | | OFFSET→keyset |
| 지역 트리 집계 | p95(ms) | | | | +Redis 캐시 |
| 반경 검색 | p95(ms) | | | | 인덱스/공간 |
| 배치 업서트 | 건/초 | | | | rewriteBatched |

### 4.2 Level 1 — 쿼리 / 인덱스 (DB 레벨)

- `title LIKE '%kw%'`(선행 와일드카드 → 인덱스 미사용) → **FULLTEXT 인덱스**로 전환.
  MySQL 8.0 **ngram parser**로 한글 대응. `MATCH ... AGAINST` 쿼리. before/after 비교.
- 깊은 페이징 `LIMIT/OFFSET` → **키셋(커서) 페이지네이션**(정렬키 기반 `WHERE (sort_key) > ?`).
  프론트 무한스크롤과 정합. OFFSET이 커질수록 벌어지는 격차를 수치로 확인.
- 필터 조합(`sido_code`, `content_type_id` 등)에 맞는 **복합/커버링 인덱스** 재점검.
  현행 인덱스(`idx_sido_type`, `idx_sigungu_type` 등)를 `EXPLAIN` 근거로 검증·보강.

### 4.3 Level 2 — 아키텍처 레벨

- **Redis 캐시** 도입: 인기 검색어·지역 트리 집계(`findGroupStats`)·상세 조회 캐싱.
  Spring `@Cacheable` + Redis, TTL·무효화 정책 정의(수집 배치 후 무효화).
  현재 관광지 계층엔 캐시가 전무하므로(대중교통 `transit_cache`만 예외) 효과가 크다.
- **HikariCP 풀 크기·타임아웃 명시적 튜닝**(현재 기본값) + 부하 시 커넥션 대기·처리량 지표.
- (보류·미래 과제로만 언급) Elasticsearch 전용 검색 인덱스, 읽기 복제본, 샤딩 —
  "초심자 과도" 기준으로 이번 실습 범위에서 제외하되 확장 여지로만 기록.

### 4.4 Level 3 — 대량 적재 레벨

- 배치 업서트 청크 크기(현행 500) 튜닝 + JDBC `rewriteBatchedStatements=true` 적용 전/후 처리량.
- 대량 수집 시 **트랜잭션 경계·청크·재시도 전략**, 실패 지점 재개(§2.2 체크포인트와 연동).

## 5. 검증 방법

- **데이터 확보**: 카카오 수집 실행 → `attraction`에 `source='KAKAO'` 행 증가 확인,
  중복 병합 정책이 좌표·상호명 근접 케이스를 올바르게 병합하는지 샘플 점검.
- **재개**: 수집 중 강제 중단 후 재실행 → `crawl_progress`의 마지막 `DONE` 커서 다음부터 시작하고
  이미 처리한 셀을 재조회하지 않는지 확인.
- **성능**: §4.1 프로토콜대로 **최적화 전 baseline을 먼저 기록** → 각 Level 적용 후 재측정,
  `benchmarks/`의 표에 before/after 수치와 `EXPLAIN` 변화를 남긴다.
</content>
