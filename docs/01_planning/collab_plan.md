# 일정 협업 기능 구현 계획

> **작성일**: 2026-06-22  
> **작성자**: 전진  
> **관련 기능**: G01 — 실시간 공동 편집 (Phase 2 로드맵)

---

## 목차

1. [기능 개요 및 단계 정의](#1-기능-개요-및-단계-정의)
2. [Phase 1 — 읽기 전용 공유 링크](#2-phase-1--읽기-전용-공유-링크)
3. [Phase 2 — 저장-새로고침 협업](#3-phase-2--저장-새로고침-협업)
4. [Phase 3 — 실시간 동시 편집](#4-phase-3--실시간-동시-편집)
5. [기술 스택 및 라이브러리](#5-기술-스택-및-라이브러리)
6. [DB 스키마 변경 계획](#6-db-스키마-변경-계획)
7. [단계별 의존성 및 구현 순서](#7-단계별-의존성-및-구현-순서)
8. [조심해야 할 포인트](#8-조심해야-할-포인트)

---

## 1. 기능 개요 및 단계 정의

여행 일정을 동행자와 함께 편집·조율하는 기능을 3단계로 나눠 점진적으로 구현한다.

| 단계 | 이름 | 핵심 경험 | 난이도 |
|------|------|-----------|--------|
| **Phase 1** | 읽기 전용 공유 | 링크를 아는 사람은 일정을 볼 수 있다 | 낮음 |
| **Phase 2** | 저장-새로고침 협업 | 저장 버튼을 누르면 동행자가 새로고침 후 확인한다 | 중간 |
| **Phase 3** | 실시간 동시 편집 | 노션처럼 수정이 즉시 반영된다 | 높음 |

각 단계는 이전 단계 위에 쌓인다. Phase 1 없이 Phase 2를 구현할 수 없고, Phase 2 없이 Phase 3를 구현할 수 없다.

---

## 2. Phase 1 — 읽기 전용 공유 링크

### 2-1. 개념

일정 소유자가 공유 링크를 생성하면 UUID 토큰이 발급된다.  
해당 링크를 아는 사람은 누구나(비회원 포함) 일정을 **읽기 전용**으로 열람할 수 있다.  
커뮤니티 `is_public` 공유와 다르게, 링크를 모르면 접근할 수 없는 **비공개 공유**다.

```
소유자: [공유 링크 생성] → UUID 토큰 발급
         ↓ 링크 복사·전달
동행자: https://tripcraft.com/trip/shared/{shareToken} 접속
         ↓ 토큰으로 일정 조회 (인증 불필요)
         읽기 전용 뷰 표시 (편집 버튼 없음)
```

### 2-2. 백엔드 구현

**추가할 API**

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST` | `/api/trips/{id}/share` | 공유 링크 생성·재발급 (소유자만) |
| `DELETE` | `/api/trips/{id}/share` | 공유 링크 비활성화 |
| `GET` | `/api/trips/shared/{shareToken}` | 토큰으로 일정 조회 (인증 불필요) |

**`trip` 테이블 컬럼 추가**

```sql
ALTER TABLE trip
    ADD COLUMN share_token  VARCHAR(36) NULL COMMENT '공유 링크 UUID (NULL=비공개)',
    ADD COLUMN share_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '공유 활성화 여부';

CREATE UNIQUE INDEX uq_trip_share_token ON trip(share_token);
```

**주요 로직**

- 공유 링크 생성 시 `UUID.randomUUID()`로 토큰 발급 → DB 저장
- `GET /api/trips/shared/{shareToken}` — SecurityConfig에서 `permitAll()` 처리
- 응답에 편집용 필드(소유자 정보 등) 제외한 읽기 전용 DTO 반환

### 2-3. 프론트엔드 구현

- `/trip/shared/:token` 라우트 추가 (인증 불필요)
- 기존 `ScheduleView`를 `readOnly` prop으로 분기 — 드래그·삭제·저장 버튼 비노출
- 소유자 화면에 "공유 링크 복사" 버튼 추가

### 2-4. 조심할 점

- **토큰 추측 방지**: `UUID.randomUUID()`는 122비트 엔트로피로 충분. `SMALLINT` 시퀀스 ID는 절대 사용 금지.
- **링크 비활성화**: 소유자가 링크를 끄면 기존 토큰으로 접근 불가. `share_enabled = 0`으로 처리 (토큰은 유지, 재활성화 가능).
- **공유 중인 일정 삭제**: 현재 `existsPostByTripId` 로직과 유사하게, 공유 활성화 상태이면 삭제 전 확인 처리 필요.

---

## 3. Phase 2 — 저장-새로고침 협업

### 3-1. 개념

공유된 일정에 특정 회원을 **편집자(EDITOR)**로 초대할 수 있다.  
편집자는 일정을 수정하고 저장 버튼을 누를 수 있다.  
다른 편집자가 새로고침하면 저장된 내용을 확인한다.  
동시에 같은 부분을 수정하면 **나중에 저장한 사람이 이긴다(Last Write Wins)**가 아니라,  
**낙관적 잠금(Optimistic Locking)**으로 충돌을 감지하고 사용자에게 알린다.

```
편집자 A: 블록 수정 → [저장] 클릭 → version=5 로 저장 성공
편집자 B: 같은 블록 수정 → [저장] 클릭 → version=4 로 시도
                                           → 서버: "이미 변경됨" 409 반환
                                           → B에게 알림: "동행자가 수정했습니다. 최신 상태를 불러오세요."
```

### 3-2. 백엔드 구현

**추가할 API**

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST` | `/api/trips/{id}/collaborators` | 편집자 초대 (이메일 또는 닉네임으로) |
| `DELETE` | `/api/trips/{id}/collaborators/{memberId}` | 편집자 제거 |
| `GET` | `/api/trips/{id}/collaborators` | 협업자 목록 조회 |

**`trip_collaborator` 테이블 신설**

```sql
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
);
```

**낙관적 잠금을 위한 `trip` 컬럼 추가**

```sql
ALTER TABLE trip
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금 버전';
```

**저장 흐름 (낙관적 잠금)**

```
클라이언트: PUT /api/trips/{id}/blocks  { blocks: [...], version: 5 }
서버: UPDATE trip SET version = 6, updated_at = NOW()
      WHERE id = {id} AND version = 5
      → affected rows = 0 이면 → 409 Conflict 반환
      → affected rows = 1 이면 → 블록 저장 진행
```

**권한 체계**

```
소유자(owner) > 편집자(EDITOR) > 뷰어(VIEWER) > 비회원(공유 링크)

소유자: 모든 권한 (초대·제거·삭제·편집)
편집자: 편집·저장 가능, 초대 불가
뷰어: 읽기만 가능 (Phase 1과 동일)
비회원: 공유 링크 있으면 읽기만 가능
```

모든 수정 API에서 소유자 또는 EDITOR 여부를 서버에서 검증한다.

### 3-3. 프론트엔드 구현

- `version` 값을 Pinia store에 보관, 저장 요청마다 함께 전송
- 409 응답 시 "동행자가 일정을 수정했습니다. 최신 상태를 불러오시겠습니까?" 모달 표시
- 모달 확인 시 일정 재조회 → 로컬 변경사항은 폐기 (충돌 병합 UI는 Phase 3에서)
- 헤더 또는 사이드바에 현재 협업자 목록 표시

### 3-4. 조심할 점

- **권한 검증은 반드시 서버에서**: 클라이언트에서 편집 버튼을 숨겨도 API는 항상 소유자·편집자 여부를 체크해야 한다.
- **낙관적 잠금 vs 비관적 잠금**: 비관적 잠금(`SELECT FOR UPDATE`)은 DB 레벨 락으로 성능 문제가 있다. 낙관적 잠금이 적합하나, 충돌이 자주 발생하는 상황(같은 블록을 동시에 편집)에서는 사용자 경험이 나빠진다. Phase 2는 "가끔 같이 보는" 수준을 타깃으로 한다.
- **탈퇴 처리**: `trip_collaborator.member_id` FK는 `ON DELETE CASCADE`이므로 탈퇴 시 자동 제거.

---

## 4. Phase 3 — 실시간 동시 편집

### 4-1. 개념

WebSocket으로 상시 연결을 유지하며, 한 편집자가 블록을 배치·이동·삭제하면  
서버를 거쳐 같은 일정을 열고 있는 모든 편집자에게 즉시 반영된다.

```
편집자 A  ─── WebSocket ──→  서버(STOMP Broker)  ──→  편집자 B
               블록 추가 이벤트 발행                    실시간 화면 업데이트
```

### 4-2. 핵심 기술 선택

#### WebSocket + STOMP 선택 이유

| 방식 | 특징 | 탈락 이유 |
|------|------|-----------|
| Polling | 구현 단순, 실시간 아님 | 지연 발생, 불필요한 요청 |
| SSE (Server-Sent Events) | 서버→클라이언트 단방향 | 클라이언트→서버 메시지 불가 |
| **WebSocket + STOMP** | 양방향, pub/sub 지원 | — (채택) |
| WebRTC | P2P, 서버 부하 낮음 | 구현 복잡도 과도, 시그널링 서버 필요 |

STOMP(Simple Text Oriented Messaging Protocol)는 WebSocket 위의 메시징 프로토콜로,  
pub/sub 채널(`/topic/trip/{tripId}`)을 쉽게 구성할 수 있어 Spring에서 가장 자연스럽다.

#### 충돌 해결 전략 선택

| 전략 | 설명 | 탈락 이유 |
|------|------|-----------|
| OT (Operational Transformation) | Google Docs 방식, 문자 단위 변환 | 구조화된 블록 데이터에 맞지 않음, 구현 극도로 복잡 |
| CRDT | Figma·Notion 방식, 분산 데이터 구조 | 올바른 방향이나 구현 복잡도가 매우 높음 |
| Last Write Wins | 가장 단순, 나중 저장이 덮어씀 | 변경 유실 위험 |
| **블록 단위 잠금** | 블록 편집 시작 시 잠금 획득 | — (채택, 아래 설명) |

**블록 단위 잠금 방식 (Pessimistic Lock at Block Level)**

일정 전체가 아닌 **블록 하나**를 편집 단위로 보고 잠금을 건다.

```
편집자 A: 블록 3번 드래그 시작 → LOCK_ACQUIRED 이벤트 브로드캐스트
편집자 B: 블록 3번 → 잠금 표시(반투명) → 편집 불가

편집자 A: 드래그 완료 → BLOCK_MOVED 이벤트 브로드캐스트 → LOCK_RELEASED
편집자 B: 즉시 반영, 잠금 해제
```

블록 추가·삭제처럼 잠금이 불필요한 조작은 이벤트만 브로드캐스트한다.  
후보군 추가·삭제도 동일하게 이벤트로 처리한다.

### 4-3. STOMP 채널 설계

**구독 채널**
```
/topic/trip/{tripId}    — 일정의 모든 변경 이벤트 구독
/user/queue/errors      — 개인 오류 메시지 (잠금 실패 등)
```

**발행 채널 (클라이언트 → 서버)**
```
/app/trip/{tripId}/block.add      — 블록 추가
/app/trip/{tripId}/block.move     — 블록 이동
/app/trip/{tripId}/block.delete   — 블록 삭제
/app/trip/{tripId}/block.update   — 블록 메모·시간 수정
/app/trip/{tripId}/block.lock     — 블록 잠금 요청
/app/trip/{tripId}/block.unlock   — 블록 잠금 해제
/app/trip/{tripId}/candidate.add  — 후보군 추가
/app/trip/{tripId}/candidate.delete — 후보군 삭제
```

**이벤트 메시지 형식**
```json
{
  "type": "BLOCK_MOVED",
  "actorId": 42,
  "actorNickname": "전진",
  "payload": {
    "blockId": 15,
    "tripDate": "2026-07-10",
    "displayOrder": 2,
    "startTime": "10:00"
  },
  "timestamp": "2026-07-01T14:30:00"
}
```

### 4-4. 백엔드 구현 핵심

**의존성 추가**
```kotlin
// build.gradle.kts
implementation("org.springframework.boot:spring-boot-starter-websocket")
```

**WebSocket 설정**
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/user");  // 인메모리 브로커
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();  // SockJS fallback
    }
}
```

**JWT 인증 통합**

STOMP CONNECT 프레임에 `Authorization` 헤더를 실어 전송한다.  
`ChannelInterceptor`로 인증을 처리한다.

```java
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            // JWT 검증 후 Principal 설정
        }
        return message;
    }
}
```

**잠금 관리**

잠금 상태는 메모리(ConcurrentHashMap)에 보관한다.  
DB에 저장하지 않는 이유: 잠금은 연결 단위 휘발성 상태이며, 서버 재시작 시 자동 해제되어야 한다.

```java
// Map<blockId, lockHolder(memberId)>
private final ConcurrentHashMap<Long, Long> blockLocks = new ConcurrentHashMap<>();
```

클라이언트가 연결을 끊으면 해당 사용자가 보유한 잠금을 모두 해제하고  
`LOCK_RELEASED` 이벤트를 브로드캐스트한다.

### 4-5. 프론트엔드 구현 핵심

**의존성 추가**
```bash
npm install @stomp/stompjs sockjs-client
```

**STOMP 연결 (Pinia store)**
```js
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const client = new Client({
  webSocketFactory: () => new SockJS('/ws'),
  connectHeaders: { Authorization: `Bearer ${accessToken}` },
  onConnect: () => {
    client.subscribe(`/topic/trip/${tripId}`, (message) => {
      const event = JSON.parse(message.body)
      handleTripEvent(event)  // Pinia store 상태 업데이트
    })
  },
  reconnectDelay: 3000,  // 연결 끊김 시 3초 후 재연결
})
```

**이벤트 핸들러 패턴**
```js
function handleTripEvent(event) {
  switch (event.type) {
    case 'BLOCK_MOVED':   applyBlockMove(event.payload); break
    case 'BLOCK_ADDED':   applyBlockAdd(event.payload);  break
    case 'BLOCK_DELETED': applyBlockDelete(event.payload); break
    case 'LOCK_ACQUIRED': markBlockLocked(event.payload.blockId, event.actorNickname); break
    case 'LOCK_RELEASED': markBlockUnlocked(event.payload.blockId); break
  }
}
```

---

## 5. 기술 스택 및 라이브러리

### 백엔드

| 항목 | 기술 | 비고 |
|------|------|------|
| WebSocket | `spring-boot-starter-websocket` | 신규 추가 |
| 메시징 프로토콜 | STOMP (Spring 내장) | 별도 의존성 없음 |
| 메시지 브로커 | Simple Broker (인메모리) | 단일 서버 환경에 적합 |
| WebSocket 인증 | `ChannelInterceptor` + 기존 JWT | — |

> **참고**: 실 서비스라면 인메모리 브로커 대신 RabbitMQ·Redis Pub/Sub 같은 외부 브로커를 사용한다.  
> 다중 서버(수평 확장) 환경에서 서버 간 메시지 공유가 필요하기 때문이다.  
> 캡스톤 단일 서버 환경에서는 인메모리 브로커로 충분하다.

### 프론트엔드

| 항목 | 기술 | 비고 |
|------|------|------|
| WebSocket 클라이언트 | `@stomp/stompjs` | STOMP 프로토콜 클라이언트 |
| WebSocket 폴백 | `sockjs-client` | WebSocket 미지원 브라우저 대응 |
| 상태 관리 | Pinia (기존) | 협업 상태(잠금·참여자)도 여기서 관리 |

---

## 6. DB 스키마 변경 계획

### Phase 1

```sql
-- trip 테이블에 공유 링크 컬럼 추가
ALTER TABLE trip
    ADD COLUMN share_token   VARCHAR(36)  NULL    COMMENT '공유 링크 UUID',
    ADD COLUMN share_enabled TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '공유 활성화 여부';

CREATE UNIQUE INDEX uq_trip_share_token ON trip(share_token);
```

### Phase 2

```sql
-- 낙관적 잠금 버전
ALTER TABLE trip
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금 버전';

-- 협업자 테이블
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

### Phase 3

추가 스키마 변경 없음.  
잠금 상태는 서버 메모리에서 관리, 연결 단위 휘발성 데이터이므로 DB 저장 불필요.

---

## 7. 단계별 의존성 및 구현 순서

```
Phase 1 (읽기 전용 공유)
    ├── share_token 컬럼 추가
    ├── GET /api/trips/shared/:token API
    ├── POST/DELETE /api/trips/:id/share API
    └── 프론트: /trip/shared/:token 라우트 + readOnly 뷰

Phase 2 (저장-새로고침 협업)     ← Phase 1 완료 후
    ├── version 컬럼 추가
    ├── trip_collaborator 테이블 신설
    ├── 초대 API + 권한 검증 미들웨어
    ├── 저장 API에 낙관적 잠금 적용
    └── 프론트: 409 처리 + 협업자 UI

Phase 3 (실시간 동시 편집)       ← Phase 2 완료 후
    ├── spring-boot-starter-websocket 의존성 추가
    ├── WebSocketConfig + JwtChannelInterceptor
    ├── 각 도메인 Controller → WebSocket 이벤트 브로드캐스트 추가
    ├── 블록 잠금 매니저 구현
    └── 프론트: @stomp/stompjs 연결 + 이벤트 핸들러
```

---

## 8. 조심해야 할 포인트

### 8-1. WebSocket 인증 — Spring Security 기본 설정으로는 부족

Spring Security의 HTTP 필터 체인은 HTTP 요청에만 적용된다.  
WebSocket 핸드셰이크 이후의 STOMP 메시지에는 적용되지 않는다.

`ChannelInterceptor`를 반드시 구현해 STOMP CONNECT 프레임에서 JWT를 검증해야 한다.  
검증 없이 WebSocket 엔드포인트를 열면 누구나 다른 사람의 일정을 구독할 수 있다.

```
잘못된 흐름: SecurityConfig에서 /ws permitAll → WebSocket 연결 허용
             → STOMP SUBSCRIBE /topic/trip/999 → 타인 일정 구독 가능

올바른 흐름: CONNECT 시 ChannelInterceptor에서 JWT 검증
             → SUBSCRIBE 시 tripId 소유자/협업자 여부 재검증
```

### 8-2. 잠금 타임아웃 필수

편집자가 블록을 잠근 채 브라우저를 강제 종료하면 잠금이 영원히 남는다.  
STOMP `SessionDisconnectEvent` 리스너로 연결 끊김을 감지해 잠금을 해제해야 한다.  
추가로 잠금 획득 시간을 기록하고 일정 시간(예: 30초) 이상 활동이 없으면 자동 해제한다.

### 8-3. 이벤트 순서 보장

같은 블록에 대해 이벤트가 순서 없이 도착할 수 있다.  
인메모리 브로커는 단일 스레드로 처리되므로 단일 서버에서는 대부분 순서가 보장되지만,  
클라이언트 측에서 `timestamp` 기준으로 이벤트를 정렬해 적용하는 방어 로직을 넣는 것이 좋다.

### 8-4. Phase 2 충돌 UX — 변경 유실을 사용자가 인지해야 한다

낙관적 잠금으로 409가 반환되면 클라이언트는 최신 상태를 다시 로드해야 한다.  
이때 **사용자가 편집하던 내용이 버려진다는 것을 명확히 알려야 한다**.  
"동행자가 일정을 변경했습니다. 내 변경사항은 저장되지 않았습니다. 최신 일정을 불러오시겠습니까?"  
같이 구체적인 안내가 필요하다.

### 8-5. 자기 자신 이벤트 처리 (Echo 방지)

WebSocket 이벤트는 브로드캐스트로 자기 자신에게도 전달된다.  
이벤트에 `actorId`를 포함해 자신이 발생시킨 이벤트는 클라이언트에서 무시해야 한다.  
그렇지 않으면 블록 이동이 두 번 적용되는 버그가 발생한다.

```js
function handleTripEvent(event) {
  if (event.actorId === auth.user.id) return  // 내가 발생시킨 이벤트는 무시
  applyEvent(event)
}
```

### 8-6. SockJS와 CORS

SockJS는 WebSocket을 지원하지 않는 환경에서 HTTP long-polling으로 폴백한다.  
이 과정에서 HTTP 요청이 발생하므로 CORS 설정이 WebSocket 엔드포인트에도 적용되어야 한다.  
`setAllowedOriginPatterns("*")`는 개발 환경 전용이며, 배포 시 허용 Origin을 명시해야 한다.

### 8-7. 단계별 롤백 고려

각 Phase는 독립적으로 배포 가능하도록 설계한다.  
Phase 1 배포 후 문제가 생기면 Phase 2로 넘어가지 않아도 된다.  
DB 마이그레이션도 Phase별로 분리해 관리한다.
