# plan.md — 여행 일정·이동 시간

> **수정 규칙**: 수정 전 변경 내용을 사용자에게 설명하고 승인을 받을 것.

## 패키지 위치

```
com.tripcraft.plan/
├── TripController.java
├── TripService.java / TripServiceImpl.java
├── TripMapper.java
└── dto/
```

## 핵심 엔티티 관계

```
Trip (일정)
 └── TripCandidate (후보군, city_code로 그룹화)  [CASCADE]
      └── TripBlock (확정 블록, 드롭 시에만 생성) [RESTRICT ← 모달 UX]
Member
 └── Favorite (즐겨찾기)
TripBlock → TransitCache (from/to attraction + departure_hour + transport_type)
```

## 삭제 규칙

**후보군(TripCandidate) 삭제 시**:
- TripBlock 존재 → 서비스 레이어에서 존재 확인 후 모달 응답 대기
  - 예: TripBlock 먼저 DELETE 후 TripCandidate DELETE
  - 아니오: 취소
- TripBlock 없음 → 즉시 DELETE

**Trip 삭제 시**:
- Post에서 해당 trip_id 참조 확인
- 있으면: "공유된 게시글이 있어 삭제할 수 없습니다" 오류 반환
- 없으면: 삭제 (TripCandidate, TripBlock CASCADE)

**Member 탈퇴 시 (앱 레이어에서 순서대로)**:
```
1. trip_block DELETE (해당 member의 모든 trip 기준)
2. trip_candidate DELETE
3. trip DELETE
4. member DELETE → CASCADE (member_token, favorite, post_like)
                 → SET NULL (post.member_id, notice.member_id)
```

## 후보군 자동 연동 로직 (즐겨찾기)

새 도시(sigungu_code)가 TripCandidate에 **처음** 추가될 때:
1. 해당 회원의 Favorite 중 동일 city_code 장소 조회
2. 이미 trip_candidate에 없는 장소만 INSERT (source='FAVORITE')
3. 토스트: "[도시명]의 즐겨찾기 N개가 후보군에 추가됐어요"

즐겨찾기 해제 → 후보군 영향 없음 (DB/앱 레이어 모두 별도 처리 없음)

## TripBlock 구조

```
trip_block
├── candidate_id         FK → trip_candidate (RESTRICT)
├── trip_date            DATE   — TRIGGER로 trip 날짜 범위 검증
├── display_order        TINYINT — 앱 레이어 관리 (UNIQUE 제약 없음, 실시간 편집 고려)
├── start_time           TIME NULL
├── duration_minutes     SMALLINT DEFAULT 60  — end_time = start_time + duration_minutes
├── transport_preference TINYINT DEFAULT 0    — 0=대중교통전체 1=지하철 2=버스 3=자동차
└── memo                 TEXT NULL
```

## 이동 시간 캐시 (transit_cache)

**캐시 키**: `(from_attraction_id, to_attraction_id, departure_hour, transport_type)`

- `departure_hour`: 실제 시(0~23). 레벨별 대표 시간은 서비스 레이어에서 결정
- `transport_type`: 0=대중교통전체 1=지하철 2=버스 3=자동차 (trip_block.transport_preference와 동일 값)
- 대중교통: ODsay API / 자동차: 별도 API (미정)

**캐시 레벨 (system_config `transit_cache_level`)**:

| 레벨 | 슬롯 | departure_hour 결정 방식 |
|------|------|------------------------|
| 1 | 1개 | 항상 config의 level1_hour (기본 10) |
| 2 | 2개 | 러시=level2_rush_hour(8) / 비러시=level2_nonrush_hour(12) |
| 3 | 3개 | 출근러시=8 / 퇴근러시=18 / 그 외=12 |
| 4 | 5개 | 새벽=4 / 출근러시=8 / 평시=12 / 퇴근러시=18 / 야간=22 |
| 5 | 24개 | 실제 출발 시각의 hour 그대로 |

레벨이 달라도 같은 departure_hour를 쓰면 캐시 공유됨.

**오류 처리**:
- API 타임아웃 5초 초과 또는 오류 → "이동 시간을 계산할 수 없습니다" 표시, 일정 저장은 정상 동작
- 대중교통 경로 없음 (도서·산간) → transport_mode='NONE', "경로 없음" 표시

**스키마 참조**: `docs/02_design/schema.sql`

## ODsay API 문서

**전체 레퍼런스**: `docs/02_design/odsay_api_v1.8.md`

핵심 요약:
- 도시내 pathType: 1/2/3 / 도시간 pathType: 11/12/13/20
- 도시내 trafficType: 1=지하철 2=버스 3=도보 / 도시간: 4=열차 5=고속버스 6=시외버스 7=항공
- 도시내 요금 필드: `payment` / 도시간: `totalPayment`
- `SearchType=0` → 도시내검색 (도시간 결과도 포함)
- 도시간 결과 반환 시 출발지→출발터미널, 도착터미널→도착지 도시내 API 추가 호출 필요

## ODsay 구현 현황 (2026-05-27 완료)

- 엔드포인트: `https://api.odsay.com/v1/api/searchPubTransPathT`
  - `/transit/path` 아님 — 해당 경로는 ODsay에 존재하지 않음
- 인증: **URI 서비스 키** + `Referer: http://localhost:5173` 헤더
  - 서버 키는 공인 IP 등록 필요 → 개발 환경 IP 변동 문제로 URI 키 방식 채택
- `OdsayClient`: `com.tripcraft.plan.client.OdsayClient`
- `TransitService/Impl`: `com.tripcraft.plan.service`
- `GET /api/transit?fromId=&toId=&hour=&transportType=` — 인증 불필요
- 프론트엔드: `frontend/src/api/transit.js` → ScheduleView `fetchTransitForDay()` 호출
- TransitPill: 블록 사이 독립 요소, 높이 = 소요 분(px), 사선 패턴 배경

**버그픽스 (2026-05-28)**
- `extractMode()` 반환값이 한국어(`"버스"`)였던 것을 DB ENUM 호환 영문(`BUS`)으로 수정
  - MySQL ENUM 불일치로 INSERT 실패 → try-catch에서 조용히 삭제 → 캐시가 쌓이지 않던 문제

**ODsay API 구조 재설계 + Transit 상세 패널 (2026-05-29)**

### 버그픽스

1. **날짜 간 블록 이동 시 Transit 미계산**: `recalculateTransitForDate` 외부 try-catch 구조가 첫 블록 실패 시 나머지 블록 계산 중단 → 블록별 개별 try-catch로 변경
2. **블록 정렬 기준 수정**: `displayOrder` → `startTime` 기준 정렬 (날짜 이동 시 새 블록이 중간에 삽입되는 케이스 정확 처리)
3. **`SearchType=0` 제거**: 메인 경로 검색(`findTransitPath`)에 `SearchType=0`이 설정되어 도시내 경로만 반환 → 도시간 모든 쌍이 -99 오류. 메인 검색에서 제거, 로컬 구간 검색(`findLocalPath`)에만 유지
4. **NONE 캐시**: ODsay 경로 없음(-99) 시 `transport_mode='NONE'`으로 캐시 저장 → 이후 동일 쌍 API 재호출 방지. 프론트엔드 pill은 "경로 정보 없음" 회색 사선 스타일로 표시

### ODsay 호출 구조 재설계

**문제**: 도시간 경로 옵션마다 출발역/도착역이 다름 (수서역 SRT vs 서울역 KTX vs 강남 고속버스). 기존 코드는 첫 번째 경로의 역 좌표로만 localFrom/localTo 계산 → 다른 경로 선택 시 소요시간 오계산.

**해결**: 각 경로가 자신의 출발역·도착역 좌표로 독립적으로 localFrom/localTo 계산.

```
findTransitPath(출발지, 목적지)
└── List<OdsayResult> (N개 경로)
    ├── pathType < 11 (도시내): totalMinutes = path.info.totalTime
    └── pathType ≥ 11 (도시간): 경로마다 독립 계산
        ├── findLocalPath(출발지 → 경로의 첫 비-도보 subPath 출발역)
        └── findLocalPath(경로의 마지막 비-도보 subPath 도착역 → 목적지)
            폴백: -99/-98 → Haversine(30km/h, estimated=true)
```

**path_detail JSON 구조 변경** (transit_cache.path_detail 컬럼):
```json
// 신규 구조 (경로 노드 안에 localFrom/localTo 임베드)
{
  "intercityPaths": [
    {
      "pathType": 11, "info": {...}, "subPath": [...],
      "localFrom": { "minutes": 28, "estimated": false, "subPath": [...] },
      "localTo":   { "minutes": 40, "estimated": true }
    },
    {
      "pathType": 11, "info": {...}, "subPath": [...],
      "localFrom": { "minutes": 15, "estimated": false, "subPath": [...] },
      "localTo":   { "minutes": 35, "estimated": false, "subPath": [...] }
    }
  ]
}
```

> **⚠ 주의**: 구조 변경으로 기존 캐시 데이터 무효화. 스키마 변경 없음, `TRUNCATE TABLE transit_cache` 실행 필요.

### 변경 파일

| 파일 | 변경 내용 |
|------|----------|
| `OdsayClient` | `LocalPathResult` record 추가, `findLocalPathMinutes` → `findLocalPath`(subPath 포함 전체 경로 반환), `OdsayResult`에 `pathType` 필드 추가 |
| `TransitServiceImpl` | `RouteEnrichment` private record, 경로별 독립 localFrom/localTo 계산 루프, `buildPathDetail` 구조 변경, `selectPath` 경로 노드 내부에서 localFrom/localTo 읽도록 수정 |
| `TransitController` | `GET /api/transit/detail` (path_detail 반환), `POST /api/transit/select` (경로 선택·저장) 엔드포인트 추가 |
| `TransitCacheMapper` | `updateSummary` 쿼리 추가 |
| `TripBlockMapper` | `updateTransitByAttractionPair` 쿼리 추가 |
| `ScheduleView.vue` | TransitDetailPanel 연동, NONE pill 처리, pill 클릭 → 상세 모달 |
| `TransitDetailPanel.vue` | 신규 컴포넌트 — 경로 탭 선택, 로컬 구간 subPath 렌더링, 경로 저장 |
| `schedule.css` | `.transit-pill-none` 회색 스타일 추가 |
| `transit.js` | `getTransitDetail`, `selectTransitPath` API 함수 추가 |

## 전역 예외 처리 (GlobalExceptionHandler)

`com.tripcraft.global.exception.GlobalExceptionHandler` (`@RestControllerAdvice`)

- `ResponseStatusException` → `ApiResponse.fail(reason, statusCode)` 형식으로 직접 반환
  - Spring Boot 기본 `/error` 재디스패치 우회 → Security 필터에서 401로 둔갑하던 문제 해결
- 일반 `Exception` → 500 반환
- `SecurityConfig`: `/error` `permitAll()` 추가 (안전망)
