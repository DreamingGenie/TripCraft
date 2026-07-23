# 개선 01 — 일정 블록 메모 실시간 공유

> 상태: ✅ 완료(v1 idle/blur 자동 저장) · 최종 갱신: 2026-07-08

## 1. 배경 / 목표

현재 시간표의 일정 블록(`trip_block`)이 담는 정보는 **시간(startTime·durationMinutes)과 장소(candidate)**,
그리고 이동시간(transit) 뿐이다. 사용자가 "여기서 점심", "예약 필요", "우천 시 대체" 같은
**맥락 메모**를 블록에 남길 수 없다.

**목표**: 일정 블록에 **최대 한글 100자** 메모를 달고, 협업자에게 **실시간으로 반영**한다.
별도 저장 버튼 없이 자동 저장하며, 실시간 성능을 위해 저장·브로드캐스트 단위를 통제한다.

## 2. 현재 구조 — 무엇을 재사용하는가

이 개선은 **새 인프라 구축이 아니라 기존 실시간 협업 인프라에 메모 편집 경로 한 줄을 얹는 작업**이다.

| 이미 존재하는 것 | 위치 | 재사용 방식 |
|---|---|---|
| `trip_block.memo TEXT` 컬럼 | `docs/sql/schema.sql` | 그대로 사용 (마이그레이션 불필요) |
| STOMP over WebSocket + 편집 토픽 `/topic/trip/{id}` | `global/config/WebSocketConfig.java` | 메모 이벤트 브로드캐스트 채널 |
| `TripEvent`(type·seq 스탬프) | `plan/dto/TripEvent.java` | `BLOCK_MEMO_UPDATED` 타입 추가 |
| grab presence 잠금(서버 게이트) | `plan/controller/TripPresenceController.java`(`getGrabOwner`) | 같은 블록 동시 편집 예방 |
| 낙관적 락(`version`) UPDATE | `plan/mapper/TripBlockMapper.updateWithVersion` | 매퍼 패턴 참고(메모는 version 미변경) |
| 협업 스토어(구독·브로드캐스트) | `frontend/src/stores/collab.js` | 메모 이벤트 수신 핸들러 추가 |
| 시간표 렌더/편집 | `frontend/src/components/ScheduleBoard.vue` | 블록 내 메모 입력 UI |

현재 없는 것: `BlockUpdateRequest`에 `memo` 필드 **없음**, `TripEvent.type`에 메모 이벤트 **없음**.

## 3. API · 이벤트 스펙

### 3.1 REST — 메모 저장 (전용 엔드포인트)

```
PATCH /api/trips/{tripId}/blocks/{blockId}/memo
```

- **요청 DTO**: 전용 `BlockMemoUpdateRequest { String memo }` 신설.
  - 이동/리사이즈(`BlockUpdateRequest`)와 **분리**한다 → 락 경합·이벤트 의미가 명확해지고 version 충돌과 얽히지 않는다.
- **서버 검증**:
  - 길이: 메모 코드포인트 수 ≤ 100 (`memo.codePointCount(0, len) > 100` 이면 400). 한글·이모지 안전.
  - 권한: 해당 트립 협업자 여부 + **grab 소유자 검증**(비소유자 PATCH 거부, 기존 grab-owner 체크 재사용).
- **매퍼**: `updateMemoById(blockId, memo)` — `memo`만 UPDATE, **`version`은 건드리지 않는다**(이동·리사이즈 낙관적 락과 독립).
- **응답**: 공통 형식 `{ success, data, message, errorCode }`.

### 3.2 실시간 — 브로드캐스트

- 저장 성공 후 `/topic/trip/{tripId}`로 `TripEvent` 전송:
  - `type = "BLOCK_MEMO_UPDATED"`
  - `payload = { blockId, memo }`
  - `seq` = 일정별 단조 증가 시퀀스 스탬프(기존 편집 이벤트와 동일 규칙).
- `plan/dto/TripEvent.java`의 type 주석 목록에 `BLOCK_MEMO_UPDATED` 추가.
- 프론트 `collab.js` 구독 핸들러에서 `BLOCK_MEMO_UPDATED` 수신 시 해당 블록 메모를 갱신
  (단, **자기 자신이 편집 중인 블록**이면 무시 — 커서 튐 방지).

## 4. 저장 단위 · 동시편집 · 성능 제약 — 확정 설계

### 4.1 저장 단위 = 디바운스 idle(≈600ms) + blur flush

별도 저장 버튼 없이 자동 저장한다. 저장·브로드캐스트 트리거는 다음 **두 시점뿐**이다.

1. **idle**: 타이핑이 멈추고 ≈600ms 경과 → 1회 `PATCH memo` + 브로드캐스트.
2. **blur**: 포커스가 메모 textarea를 벗어나면 즉시 1회 flush.

- **per-keystroke 전송 금지.** 키 입력마다 보내면 이벤트가 폭주한다.
- 페이로드 크기는 문제가 아니다(한글 100자 ≈ 300바이트). **진짜 리스크는 빈도**이므로 디바운스로 통제한다.
- 타인에게는 편집자가 타이핑을 멈춘 뒤 ~0.6초 이내에 반영된다. 메모 성격상 충분한 실시간성.
- **구현 원칙**: "persist(PATCH)"와 "broadcast"를 처음부터 **분리 가능한 구조**로 짠다.
  그래야 아래 v2가 재작업이 아니라 증분 확장이 된다.

### 4.2 (선택·후속) v2 = 타이핑 라이브 중계 — 증분 확장, 재작업 아님

Google Docs처럼 **타이핑하는 글자를 거의 실시간으로** 남에게 보여주고 싶다면:

- 4.1의 영속화 경로(PATCH·매퍼·`BLOCK_MEMO_UPDATED`)를 **그대로 재사용**하고,
  그 위에 "타이핑 중 라이브 텍스트"를 얹는 **휘발성 채널**만 추가한다.
- 이 휘발성 채널은 **기존 커서 presence 인프라**(`/topic/trip/{id}/presence`, `@MessageMapping`) 패턴을
  그대로 재사용한다 — **저장하지 않고 seq도 부여하지 않는** presence 성격의 이벤트.
- 프론트: 입력을 ≈300ms throttle로 라이브 송신(중계용) + DB 저장은 여전히 idle/blur에만.
- 즉 v2는 새 인프라가 아니라 presence 패턴 한 겹을 더하는 작업이다. **1번 → 3번은 대작업 아님.**

### 4.3 동시 편집 충돌 = grab 잠금 재사용 (서버 게이트), DB 버전락 생략

**문제 확인**: 4.1의 last-write-wins에서 **같은 블록** 메모를 두 명이 거의 동시에 편집하면,
나중에 서버에 도달한 flush가 앞 flush를 덮어써 **앞사람 텍스트가 조용히 소실**될 수 있다.
(단 **다른 블록**은 다른 row라 충돌이 없다.)

**해결 = 기존 grab 잠금 재사용**:
- 메모 textarea 포커스 = 해당 블록 grab, blur = 해제, stale 시 자동 evict.
- 다른 사용자는 그 블록에 "편집 중" 표시 + 읽기 전용 → **동시 편집 자체를 예방**하므로 위 덮어쓰기 시나리오가 성립하지 않는다.
- grab은 "UX단만"이 아니라 **서버 게이트**다(`getGrabOwner`로 서버가 소유자를 추적하고 비소유자 편집을 거부).
  유지할 최소 서버 방어: **PATCH memo 시 grab 소유자 검증**.

**DB row 버전락(낙관적 락)은 생략(중복)**:
- grab이 동시 쓰기를 원천 차단하므로 memo에 version 검사를 더하는 것은 belt-and-suspenders다.
- 텍스트 메모는 충돌 파괴력이 작아 reject/재시도 UX만 번거로워진다.
- 따라서 memo UPDATE는 **version 미변경**으로 이동·리사이즈 낙관적 락과 분리한다.
- 이는 `docs/features/realtime-collab.md`의 "transit 재계산은 version 미변경" 정책과 같은 결
  (사용자 의도 편집만 version을 올린다)이므로 정합적이다.

### 4.4 길이 제약

- 서버: 코드포인트 기준 100자 초과 거부(위 3.1).
- 프론트: textarea `maxlength` + 남은 글자 수 표시.
- 컬럼: `TEXT` 유지(마이그레이션 불필요, 향후 상한 확장 여지 보존).

## 5. 구현 체크리스트

**백엔드**
- [x] `plan/dto/BlockMemoUpdateRequest.java` 신설(`memo`).
- [x] `TripController`에 `PATCH /{tripId}/blocks/{blockId}/memo` 핸들러(권한 + grab 소유자 + 길이 검증).
- [x] `TripBlockMapper.updateMemoById` + XML(`UPDATE trip_block SET memo=#{memo} WHERE id=#{id}`, `#{}`만).
- [x] `TripServiceImpl`에 메모 저장 + `BLOCK_MEMO_UPDATED` 브로드캐스트(`broadcast()`가 afterCommit seq 스탬프).
- [x] `TripEvent.java` type 주석에 `BLOCK_MEMO_UPDATED` 추가.
- [x] `BlockItem` DTO에 `memo` 추가 → 초기 로드 시 메모 표시.

**프론트엔드**
- [x] `ScheduleBoard.vue` 블록에 메모 입력 UI(포커스 시 grab presence, blur 시 해제·flush).
- [x] 디바운스(≈600ms) + blur flush로 `api/trip.js` `updateBlockMemo` PATCH 호출.
- [x] `ScheduleBoard.vue handleTripEvent`에 `BLOCK_MEMO_UPDATED` 수신 핸들러(내가 편집 중인 블록은 무시).
- [x] 100자 `maxlength`. (잔여 글자 표시는 후속 — maxlength로 하드 컷)

**문서·이력**
- [x] `docs/api.md`에 신규 엔드포인트 반영.
- [x] `CHANGELOG.md [Unreleased]`에 항목 추가.

## 6. 검증 방법

- **동시성 E2E**: 두 브라우저 세션(협업자 A·B)으로 같은 일정을 연다.
  - A가 블록 메모 입력 → B 화면에 ~0.6초 내 반영 확인.
  - A가 메모 편집 중 B가 같은 블록을 열면 "편집 중"·읽기전용 표시 확인(grab).
  - 서로 다른 블록을 동시 편집 → 둘 다 정상 저장(충돌 없음) 확인.
- **길이 제약**: 101자 입력·전송 시 서버 400 + 프론트 차단 확인.
- **빈도**: 개발자도구 WS 프레임으로 타이핑 중 브로드캐스트가 per-keystroke가 아니라 idle/blur에만 나가는지 확인.
</content>
