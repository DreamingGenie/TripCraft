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
