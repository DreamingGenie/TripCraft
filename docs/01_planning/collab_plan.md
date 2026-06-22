# 일정 실시간 협업 구현 계획

> **작성일**: 2026-06-22  
> **작성자**: 전진 (실시간-네이티브 재설계: 송정기·전진 토의, 2026-06-22)  
> **관련 기능**: G01 — 실시간 공동 편집
>
> **분리 안내**: 읽기 전용 공유 링크는 [`share_plan.md`](share_plan.md), 카카오 로그인·공유는
> [`kakao_plan.md`](kakao_plan.md)로 분리했다. 이 문서는 **회원 간 실시간 협업 편집**만 다룬다.
>
> **설계 기준**: 구글 시트(권한·공유 모델) + 구글 독스/Figma(라이브 커서·presence)를 참고했다.
> "저장-새로고침" 같은 중간 단계를 두지 않고 **처음부터 실시간 동시 편집**으로 설계한다.
> 현재 편집 API는 이미 연산 단위 즉시 저장(REST)이므로 낙관적 잠금(`version`)·일괄 저장·409
> 충돌 모달은 도입하지 않는다.

---

## 목차

1. [개요 및 설계 원칙](#1-개요-및-설계-원칙)
2. [권한 모델](#2-권한-모델)
3. [협업자 초대](#3-협업자-초대)
4. [실시간 동기화 아키텍처](#4-실시간-동기화-아키텍처)
5. [데이터 변경 이벤트 (서버 권위)](#5-데이터-변경-이벤트-서버-권위)
6. [이동 시간 비동기 재계산](#6-이동-시간-비동기-재계산)
7. [Presence · 라이브 커서 · 소프트 락](#7-presence--라이브-커서--소프트-락)
8. [재연결 재동기화](#8-재연결-재동기화)
9. [DB 스키마 변경 계획](#9-db-스키마-변경-계획)
10. [기술 스택 및 라이브러리](#10-기술-스택-및-라이브러리)
11. [구현 순서](#11-구현-순서)
12. [조심해야 할 포인트](#12-조심해야-할-포인트)

---

## 1. 개요 및 설계 원칙

여행 일정을 동행자(회원)와 함께 **실시간으로 동시에** 편집한다.  
한 편집자가 블록을 배치·이동·삭제하면 같은 일정을 열고 있는 모든 참가자에게 즉시 반영되고,  
누가 어디를 보고/잡고 있는지 라이브 커서로 표시된다.

**설계 원칙**

- **서버 권위(server-authoritative)**: 모든 변경은 서버가 영속화하고, `display_order` 등 정합성을
  서버가 정규화한 **정식 결과를 브로드캐스트**한다. 클라이언트는 그 결과로 수렴한다.
- **즉시 저장 유지**: 현재 연산 단위 REST 저장(`placeBlock`/`updateBlock`/`removeBlock`…)을 그대로
  쓰고, WebSocket은 그 변경을 **다른 참가자에게 전파**하는 역할. 별도 "저장 버튼"·일괄 저장 없음.
- **데이터와 presence 분리**: 영속 데이터 변경(저빈도)과 마우스 커서·상호작용 상태(고빈도, 휘발성)는
  채널을 분리한다.
- **잠금은 명시 RPC가 아니라 상태에서 파생**: lock/unlock 메시지를 따로 두지 않고, 각자의 포인터
  상호작용 상태(grab) 브로드캐스트에서 소프트 락을 파생한다 (7장).

읽기 전용 공유([`share_plan.md`](share_plan.md))는 이 협업 기능의 선행 조건이 아니라 별개 기능이다.

---

## 2. 권한 모델

구글 시트의 두 공유 방식이 기존 구조와 대응된다.

| 구글 시트 | TripCraft 대응 |
|-----------|----------------|
| "링크가 있는 모든 사용자: 뷰어" | 읽기 전용 공유 링크 ([`share_plan.md`](share_plan.md), `share_token`) — 비회원 가능 |
| "특정 사용자와 공유: 편집자/뷰어" | 협업자 초대 (`trip_collaborator`, 회원 대상) |

**역할 정의**

| 역할 | 저장 위치 | 권한 |
|------|----------|------|
| **OWNER** | `trip.member_id` | 모든 권한. 단 1명 (소유권 이전은 이번 범위 제외) |
| **EDITOR** | `trip_collaborator.role='EDITOR'` | 조회·편집 |
| **VIEWER** | `trip_collaborator.role='VIEWER'` | 조회만 ("공유받은 일정" 목록에 표시) |
| (익명 뷰어) | `share_token` | 링크만 아는 비회원, 조회만 |

**권한 매트릭스**

| 동작 | OWNER | EDITOR | VIEWER | 익명(링크) |
|------|:---:|:---:|:---:|:---:|
| 일정 조회 | ✓ | ✓ | ✓ | ✓ |
| 블록·후보 편집 | ✓ | ✓ | ✗ | ✗ |
| 협업자 초대/제거 | ✓ | ✗ | ✗ | ✗ |
| 일정 삭제 | ✓ | ✗ | ✗ | ✗ |
| 공유 링크 on/off | ✓ | ✗ | ✗ | ✗ |

**권한 검증 — 계층형 리졸버로 재설계**

현재 `TripServiceImpl`의 6개 메서드에 중복된 "소유자 단독" 체크
(`if (!trip.getMemberId().equals(memberId)) throw FORBIDDEN`)를 다음으로 교체한다.

```
TripRole resolveRole(tripId, memberId):
    OWNER            (trip.member_id == memberId)
    EDITOR / VIEWER  (trip_collaborator 조회 결과)
    null             (회원 권한 없음 — 링크 접근은 /shared 엔드포인트에서 별도 처리)

assertCanView(tripId, memberId)   = role ≠ null              → 조회
assertCanEdit(tripId, memberId)   = role ∈ {OWNER, EDITOR}   → 편집 API 및 WS 편집 메시지
assertCanManage(tripId, memberId) = role == OWNER            → 삭제·초대/제거·링크 on/off
```

| 현재 메서드 | 교체 |
|-------------|------|
| `getTripDetail` | `assertCanView` |
| `addCandidate` / `removeCandidate` / `placeBlock` / `updateBlock` / `removeBlock` | `assertCanEdit` |
| `deleteTrip` | `assertCanManage` |
| `updateDefaultTransitMode` | `assertCanEdit` |
| (신규) 협업자 초대·제거 | `assertCanManage` |

권한 검증은 REST·WebSocket 양쪽에서 동일 리졸버를 쓴다. 클라이언트가 버튼을 숨겨도 서버가 항상 검증한다.

---

## 3. 협업자 초대

**추가할 API**

| 메서드 | 경로 | 권한 | 설명 |
|--------|------|------|------|
| `POST` | `/api/trips/{id}/collaborators` | OWNER | 회원을 EDITOR/VIEWER로 초대 |
| `DELETE` | `/api/trips/{id}/collaborators/{memberId}` | OWNER | 협업자 제거 |
| `GET` | `/api/trips/{id}/collaborators` | VIEW | 협업자 목록 조회 |
| `GET` | `/api/members/search?q={닉네임 또는 이메일}` | 로그인 | 초대 대상 검색 |
| `GET` | `/api/trips/collaborating` | 로그인 | 내가 협업자로 참여 중인 일정 목록 |

- **초대 = 즉시 수락**: API 호출 즉시 `trip_collaborator` 행 생성, 별도 수락 단계 없음.
- **초대는 오너만**: EDITOR는 다른 사람을 초대/제거할 수 없다 (구글 시트 기본도 소유자 중심).
- **알림**: 별도 실시간 알림 시스템 없음 — 초대받은 사람이 "공유받은 일정" 목록에서 확인.
  (추후 이메일·인앱 알림 추가 가능)
- **탈퇴 처리**: `trip_collaborator.member_id` FK는 `ON DELETE CASCADE`이므로 탈퇴 시 자동 제거.

---

## 4. 실시간 동기화 아키텍처

### 4-1. WebSocket + STOMP

| 방식 | 탈락 이유 |
|------|-----------|
| Polling | 지연·불필요한 요청 |
| SSE | 서버→클라 단방향 (클라→서버 불가) |
| **WebSocket + STOMP** | 양방향·pub/sub — **채택** |
| WebRTC | 시그널링 서버 필요, 과한 복잡도 |

STOMP는 WebSocket 위 메시징 프로토콜로 pub/sub 채널을 쉽게 구성할 수 있어 Spring에서 자연스럽다.
단일 서버 캡스톤 환경이므로 **인메모리 Simple Broker**로 충분하다. (실서비스라면 Redis/RabbitMQ)

### 4-2. 채널 설계 — 데이터와 presence 분리

**구독 채널 (서버 → 클라)**
```
/topic/trip/{tripId}            — 데이터 변경 이벤트 (영속·서버 권위)
/topic/trip/{tripId}/presence   — 커서·상호작용 상태 (고빈도·휘발성)
/user/queue/errors              — 개인 오류 (권한 거부 등)
```

**발행 채널 (클라 → 서버)**
```
/app/trip/{tripId}/block.add        — 블록 추가
/app/trip/{tripId}/block.move       — 블록 이동
/app/trip/{tripId}/block.update     — 블록 메모·시간 수정
/app/trip/{tripId}/block.delete     — 블록 삭제
/app/trip/{tripId}/candidate.add    — 후보군 추가
/app/trip/{tripId}/candidate.delete — 후보군 삭제
/app/trip/{tripId}/pointer          — 포인터 상태 (좌표·hover/click/grab) — 휘발성
```

### 4-3. JWT 인증 통합

Spring Security HTTP 필터 체인은 STOMP 메시지에 적용되지 않으므로 `ChannelInterceptor`로 검증한다.
기존 `JwtTokenProvider`를 재사용한다.

```java
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            // JwtTokenProvider 로 검증 후 memberId 를 Principal 로 설정
        }
        return message;
    }
}
```

- **CONNECT**: JWT 검증 → `Principal(memberId)` 설정
- **SUBSCRIBE `/topic/trip/{id}`**: `assertCanView(tripId, memberId)` 재검증 (타인 일정 구독 차단)
- **SEND `/app/trip/{id}/...`**: 편집 메시지는 `assertCanEdit` 재검증

### 4-4. blockId → trip 해석

`trip_block`에는 `trip_id`가 없고 `candidate_id`로 `trip`에 연결된다.  
발행 경로에 `tripId`가 있으므로 권한은 `tripId`로 검증하되, **대상 블록이 그 trip 소속인지** 확인해
위조를 막는다. 매퍼에 `findTripIdByBlockId(blockId)`(= `trip_block`→`trip_candidate` 조인)를 추가한다.

---

## 5. 데이터 변경 이벤트 (서버 권위)

클라이언트의 편집은 기존 REST(또는 동등한 STOMP 핸들러)로 서버에 반영되고,  
서버가 영속화·정규화한 **정식 결과**를 `/topic/trip/{tripId}`로 브로드캐스트한다.

**이벤트 종류**

| type | 발생 | payload 요지 |
|------|------|--------------|
| `BLOCK_ADDED` | 블록 배치 | blockId, candidateId, tripDate, displayOrder, … |
| `BLOCK_MOVED` | 블록 이동 | blockId, tripDate, displayOrder |
| `BLOCK_UPDATED` | 메모·시간 수정 | blockId, startTime, durationMinutes, memo |
| `BLOCK_DELETED` | 블록 삭제 | blockId, tripDate |
| `CANDIDATE_ADDED` | 후보 추가 | candidateId, attractionId |
| `CANDIDATE_REMOVED` | 후보 삭제 | candidateId |
| `TRANSIT_RECALCULATED` | 이동시간 재계산 완료 | tripDate, [{blockId, transitDurationMinutes, transitMode}] |

**메시지 형식 (예시)**
```json
{
  "type": "BLOCK_MOVED",
  "actorId": 42,
  "actorNickname": "전진",
  "payload": { "blockId": 15, "tripDate": "2026-07-10", "displayOrder": 2 },
  "timestamp": "2026-07-01T14:30:00"
}
```

**서버 권위 순서 정규화**

`display_order`는 앱 레이어가 관리한다(스키마 명시). 두 명이 같은 날·같은 위치에 동시에 블록을
떨궈 순서가 겹쳐도, **서버가 해당 날짜의 순서를 재정규화한 정식 결과를 브로드캐스트**하므로
모든 클라이언트가 동일 상태로 수렴한다. 잠금 없이도 데이터 정합성이 깨지지 않는 핵심 장치다.

**Echo 방지**: 모든 이벤트에 `actorId`를 포함하고, 클라이언트는 자신이 발생시킨 이벤트를 무시한다.
```js
function handleTripEvent(event) {
  if (event.actorId === auth.user.id) return  // 내가 발생시킨 이벤트는 무시
  applyEvent(event)
}
```

---

## 6. 이동 시간 비동기 재계산

**문제**: 블록 배치·이동·삭제 시 `recalculateTransitForDate()`가 ODsay/TMap을 **동기 호출**하고
그 날짜의 **모든 블록 이동시간을 다시 쓴다**. 외부 API라 첫 계산은 수 초 걸릴 수 있다
(`TransitCache` 캐시가 있어 반복 경로는 빠름).

**결정 — 비동기 2단계 + 날짜 단위 브로드캐스트**

```
① 구조 변경 즉시 반영
   block.move 처리 → 위치/순서만 저장 → BLOCK_MOVED 즉시 브로드캐스트
   (해당 날 블록들의 이동시간은 클라이언트에서 "계산 중" 표시)

② 백그라운드 재계산
   서버가 recalculateTransitForDate(tripId, date)를 비동기로 실행 (ODsay 호출)

③ 결과 브로드캐스트
   재계산 완료 → TRANSIT_RECALCULATED(tripDate, 블록별 transit) 브로드캐스트
   → 모든 클라이언트가 이동시간 갱신
```

- 날짜를 가로지르는 이동은 **두 날짜**(old/new)를 각각 재계산·브로드캐스트한다 (현재 로직과 동일).
- 이동시간 재계산은 한 블록이 아니라 **그 날 전체**에 영향을 주므로, 단일 블록 payload가 아니라
  **날짜 단위 묶음**으로 보내야 다른 참가자 화면이 어긋나지 않는다.

---

## 7. Presence · 라이브 커서 · 소프트 락

구글 독스/Figma처럼 각 참가자의 **포인터 상태를 계속 브로드캐스트**하고, 거기서 소프트 락을 파생한다.
명시적 lock/unlock 메시지와 per-lock 타임아웃을 두지 않는다.

### 7-1. 포인터 상태 스트림

```
/app/trip/{tripId}/pointer   (클라 → 서버, throttle ~50ms)
  { x, y, interaction: "hover" | "click" | "grab", targetBlockId? }
        ↓ 서버가 그대로 재전파 (DB 저장 안 함)
/topic/trip/{tripId}/presence
  { actorId, actorNickname, color, x, y, interaction, targetBlockId }
```

- **고빈도·휘발성**: 데이터 채널과 분리, 클라이언트 ~50ms throttle, 서버는 **DB에 저장하지 않고**
  연결 단위 메모리만 사용.
- 라이브 커서: 다른 참가자의 커서를 닉네임·색상과 함께 화면에 표시.

### 7-2. grab 기반 소프트 락

- 이미 타임라인에 배치된 블록을 누군가 `grab`(드래그 시작)하면 → 그 블록을 **소프트 락**으로 표시
  (반투명 + "OO님이 이동 중"). 다른 참가자는 그 블록을 잡을 수 없다.
- 서버는 포인터 스트림에서 파생한 **현재 grab 맵(`Map<blockId, memberId>`, 인메모리)**을 유지하고,
  잠긴 블록에 대한 `block.move`/`block.update`가 다른 사용자에게서 오면 **거부**(`/user/queue/errors`)한다.
- grab 해제(interaction이 grab이 아니게 됨)되면 락도 자연히 풀린다 — 별도 unlock 메시지 불필요.
- 블록 추가·삭제, 후보 추가·삭제는 잠금이 불필요(서버 순서 정규화로 해결)하므로 이벤트만 처리한다.

### 7-3. Liveness (좀비 연결 대비)

포인터 스트림이 멈추면 grab 락이 남을 수 있으므로:

- **정상 종료**: STOMP `SessionDisconnectEvent` 리스너로 해당 사용자의 presence·grab을 즉시 제거하고
  `PRESENCE_LEAVE` + 관련 락 해제를 브로드캐스트.
- **반쯤 끊긴 연결**: 마지막 포인터 갱신 후 **~5초** 무소식이면 stale로 간주해 제거(스테일 타임아웃).

→ per-lock 타임아웃 대신 **presence 생존 확인 하나**로 단순화된다.

---

## 8. 재연결 재동기화

인메모리 브로커는 끊긴 동안의 이벤트를 재생하지 않는다. 따라서 (재)연결 시:

1. STOMP 연결 + `/topic/trip/{id}` 구독
2. **`GET /api/trips/{id}`(기존 `getTripDetail`)로 전체 스냅샷 재조회** → 로컬 상태 교체
3. 이후 라이브 이벤트로 갱신

`reconnectDelay`(예: 3초)로 자동 재연결하고, 재연결 직후 항상 2번 재조회를 수행해 divergence를 막는다.

---

## 9. DB 스키마 변경 계획

```sql
-- 협업자 테이블 (역할 기반)
CREATE TABLE trip_collaborator (
    id         BIGINT                   NOT NULL AUTO_INCREMENT,
    trip_id    BIGINT                   NOT NULL,
    member_id  BIGINT                   NOT NULL,
    role       ENUM('EDITOR', 'VIEWER') NOT NULL DEFAULT 'EDITOR',
    invited_at TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_collaborator (trip_id, member_id),
    FOREIGN KEY (trip_id)   REFERENCES trip(id)   ON DELETE CASCADE,
    FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE
) COMMENT='일정 협업자. 소유자(trip.member_id)와 별도 관리.';
```

- **낙관적 잠금 `version` 컬럼은 추가하지 않는다** — 즉시 저장 + 서버 권위 정규화 + grab 소프트 락으로
  충돌을 처리하므로 불필요.
- 잠금·presence·grab 상태는 모두 **서버 메모리**에서 관리(연결 단위 휘발성). DB 저장 없음.

---

## 10. 기술 스택 및 라이브러리

### 백엔드

| 항목 | 기술 | 비고 |
|------|------|------|
| WebSocket | `spring-boot-starter-websocket` | 신규 추가 |
| 메시징 프로토콜 | STOMP (Spring 내장) | 별도 의존성 없음 |
| 메시지 브로커 | Simple Broker (인메모리) | 단일 서버 환경에 적합 |
| WebSocket 인증 | `ChannelInterceptor` + 기존 `JwtTokenProvider` | — |
| 비동기 재계산 | `@Async` 또는 별도 스레드풀 | 이동시간 ODsay 호출 |

> 실서비스라면 인메모리 브로커 대신 Redis Pub/Sub·RabbitMQ를 써 다중 서버 간 메시지를 공유한다.
> 캡스톤 단일 서버 환경에서는 인메모리로 충분하다.

### 프론트엔드

| 항목 | 기술 | 비고 |
|------|------|------|
| WebSocket 클라이언트 | `@stomp/stompjs` | STOMP 클라이언트 |
| WebSocket 폴백 | `sockjs-client` | WebSocket 미지원 브라우저 대응 |
| 상태 관리 | Pinia (기존) | 협업 상태(presence·grab·참여자)도 관리 |

---

## 11. 구현 순서

```
1. 권한 레이어 (실시간과 무관하게 선행, 지금 해도 이득)
    ├── trip_collaborator 테이블 신설
    ├── TripRole resolveRole + assertCanView/Edit/Manage 헬퍼
    ├── TripServiceImpl 6곳 소유자 체크 → 헬퍼로 교체
    └── 협업자 초대/제거/목록 + 회원 검색 API

2. WebSocket 기반
    ├── spring-boot-starter-websocket 의존성
    ├── WebSocketConfig (인메모리 브로커, /ws 엔드포인트, SockJS)
    ├── JwtChannelInterceptor (CONNECT 인증, SUBSCRIBE/SEND 권한)
    └── findTripIdByBlockId 매퍼

3. 데이터 변경 이벤트 + 비동기 재계산
    ├── 편집 처리 → 서버 권위 정규화 → 즉시 브로드캐스트
    ├── 이동시간 비동기 재계산 → TRANSIT_RECALCULATED 브로드캐스트
    └── 프론트: @stomp/stompjs 연결 + 이벤트 핸들러(Echo 방지)

4. Presence · 소프트 락
    ├── /pointer 스트림 + /presence 재전파 (throttle, DB 미저장)
    ├── grab 맵 + 소프트 락 거부 로직
    ├── SessionDisconnectEvent + 스테일 타임아웃
    └── 프론트: 라이브 커서 렌더링 + grab 락 표시

5. 재연결 재동기화
    └── 재연결 시 GET /api/trips/{id} 스냅샷 재조회
```

---

## 12. 조심해야 할 포인트

### 12-1. WebSocket 인증은 Security 필터 밖
HTTP 필터 체인은 STOMP 메시지에 적용되지 않는다. `ChannelInterceptor`로 CONNECT 시 JWT를 검증하고,
SUBSCRIBE 시 `assertCanView`, 편집 SEND 시 `assertCanEdit`를 재검증한다. 검증 없이 `/ws`를 열면
누구나 타인 일정을 구독할 수 있다.

### 12-2. 데이터 채널과 presence 채널을 반드시 분리
마우스 좌표는 초당 수십 건이다. 영속 데이터 이벤트와 같은 채널에 섞으면 처리·렌더가 밀린다.
presence는 별도 채널 + throttle + **DB 미저장**.

### 12-3. 이동시간 fan-out
블록 이동은 그 날 전체 이동시간을 바꾼다. 옮긴 블록 하나만 브로드캐스트하면 타 참가자 화면의
이동시간이 어긋난다. 반드시 날짜 단위 `TRANSIT_RECALCULATED`로 보낸다 (6장).

### 12-4. Echo 방지
브로드캐스트는 본인에게도 전달된다. `actorId`로 자신이 발생시킨 이벤트를 무시하지 않으면
블록 이동이 두 번 적용된다.

### 12-5. grab 락 누수 방지
포인터 스트림이 멈추면(크래시·네트워크 끊김) 락이 남는다. SessionDisconnectEvent + 스테일
타임아웃(~5초)으로 반드시 정리한다 (7-3).

### 12-6. SockJS와 CORS
SockJS 폴백은 HTTP long-polling을 쓰므로 CORS가 WebSocket 엔드포인트에도 적용돼야 한다.
`setAllowedOriginPatterns("*")`는 개발 전용, 배포 시 허용 Origin을 명시한다.

### 12-7. 서버 권위 정규화 누락 주의
클라이언트가 계산한 `display_order`를 그대로 믿지 말 것. 동시 편집 시 겹칠 수 있으므로 서버가
재정규화한 정식 순서를 브로드캐스트해야 모두가 수렴한다.
