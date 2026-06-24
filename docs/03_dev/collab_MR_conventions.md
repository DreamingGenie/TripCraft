# feat(collab): 실시간 협업 일정 편집 기능 구현

> 사후 작성 MR. `feature/collab` 브랜치가 리뷰 없이 `master`로 바로 머지되어(`8d37bdf`),
> CLAUDE.md의 MR 설명 컨벤션에 맞춰 머지된 코드 기준으로 재작성한 기록.
> 범위: `5a2b802..10f6ab9` (`feature/collab`)
>
> **문서 위치**: 이 MR 설명 문서는 개발 산출물이므로 `docs/03_dev/` 에 보관한다.
> (`docs/04_logs/` 의 일자별 작업 일지와 구분 — 일지는 시간순 기록, 본 문서는 기능 단위 MR 산출물)

## ⚠️ 배포·머지 전 필수 선행: DB 마이그레이션

이 기능은 **새 테이블 `trip_collaborator` 에 의존**한다. 코드 머지/배포 전에
**반드시 아래 SQL 마이그레이션을 먼저 적용**해야 한다. 적용하지 않으면 협업자 조회·초대·
권한 판정(`resolveRole`, `TripCollaboratorMapper.*`)이 즉시 실패한다.

```
docs/02_design/migration_collab_v1.sql   # trip_collaborator 테이블 신설
```

순서: **① migration SQL 적용 → ② 백엔드/프론트 배포**. (역순 금지)

## 1. 개요

일정(Trip)을 소유자 외 회원이 함께 편집하는 **실시간 협업 편집** 기능을 추가한다.
협업자 초대·관리(역할 OWNER/EDITOR/VIEWER), WebSocket(STOMP) 기반 블록 변경·커서·드래그
실시간 동기화, 서버 권위 권한 판정을 포함한다.

## 2. 변경 내용

### ① WebSocket/STOMP 인프라 및 인증

| 파일                                           | 작업                                                                                         |
| ---------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `global/config/WebSocketConfig.java`           | STOMP 엔드포인트(`/ws`, SockJS), 메시지 브로커(`/topic`), 앱 프리픽스(`/app`), 인터셉터 등록 |
| `global/security/JwtHandshakeInterceptor.java` | 핸드셰이크 시 `access_token` 쿠키 검증 → 세션 속성 `MEMBER_ID_ATTR`(상수)에 memberId 저장    |
| `global/security/JwtChannelInterceptor.java`   | `ChannelInterceptor.preSend` 구현. STOMP 커맨드별 분기                                       |
| `global/security/SecurityConfig.java`          | `/ws/**` 시큐리티 예외                                                                       |
| `build.gradle.kts`·`application.yml`           | `spring-boot-starter-websocket` 의존성·설정                                                  |

**`JwtChannelInterceptor` 신규 함수 (권한 검증의 핵심)**

- `preSend(...)` — `switch(command)` 로 `CONNECT/SUBSCRIBE/SEND` 라우팅
- `handleConnect(accessor)` — 세션의 memberId로 `UsernamePasswordAuthenticationToken` 생성해 `accessor.setUser()` (Principal 주입)
- `handleSubscribe(accessor)` — `/topic/trip/{tripId}` 구독 시 `tripMapper.findById` + `tripCollaboratorMapper.findByTripAndMember` 로 조회권 확인. **세션 속성 `tripAccess:{tripId}` 에 캐시** → 같은 trip 재구독 시 DB 재조회 생략
- `handleSend(accessor)` — `/app/trip/{tripId}/...` 발행 시 동일 검증, **SUBSCRIBE 캐시 공유**
- `getMemberIdFromSession(accessor)`, `parseTripId(destination, index)` — 헬퍼

> 이 SEND 캐시가 ③의 presence 부하 절감 리팩토링의 전제(중복 검증 제거 근거).

### ② 협업자 도메인·CRUD (서버 권위 권한)

**DB**

| 파일                                     | 작업                                                                                                                                 |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `docs/02_design/migration_collab_v1.sql` | `trip_collaborator`(`trip_id`,`member_id`,`role ENUM('EDITOR','VIEWER')`,`invited_at`, `UNIQUE(trip_id,member_id)`, FK CASCADE) 신설 — **코드보다 먼저 적용 필수**(상단 ⚠️ 참조) |

**도메인/DTO**

| 파일                                | 추가 내용                                                       |
| ----------------------------------- | --------------------------------------------------------------- |
| `plan/domain/TripCollaborator.java` | 협업자 엔티티(`id,tripId,memberId,role,invitedAt`)              |
| `plan/domain/TripRole.java`         | `enum { OWNER, EDITOR, VIEWER }`                                |
| `plan/dto/CollaboratorItem.java`    | record `(memberId, nickname, role, profileImageUrl)`            |
| `plan/dto/TripDetailResponse.java`  | `ownerNickname`, `myRole` 필드 추가 (서버가 역할 판정해 내려줌) |

**Mapper** (`TripCollaboratorMapper.java` + `.xml`)

- `insert(TripCollaborator)` / `delete(tripId, memberId)`
- `findByTripId(tripId)` / `findItemsByTripId(tripId)`(→ `CollaboratorItem`, 닉네임·프로필 조인)
- `findByTripAndMember(tripId, memberId): Optional<TripCollaborator>` (권한 판정의 기본 단위)
- `TripMapper.findCollaboratingByMemberId(memberId)` 추가 — 초대받은 일정 목록

**Controller** (`TripController.java`)

- `GET /api/trips/collaborating` → `getCollaboratingTrips`
- `GET /api/trips/{id}/collaborators` → `getCollaborators`
- `POST /api/trips/{id}/collaborators` (body: `memberId`,`role`) → `inviteCollaborator`, 201
- `DELETE /api/trips/{id}/collaborators/{targetMemberId}` → `removeCollaborator`

**Service** (`TripServiceImpl.java` — 권한 헬퍼가 리뷰 핵심)

- 신규 의존성: `collaboratorMapper`, `memberMapper`, `SimpMessagingTemplate messaging`
- `resolveRole(tripId, memberId): TripRole` — 소유자면 `OWNER`, 협업자면 해당 role, 아니면 `null`
- `assertCanView` (null이면 403) / `assertCanEdit` (null·VIEWER면 403) / `assertCanManage` (OWNER 아니면 403)
- `nickname(memberId)`, `broadcast(tripId, TripEvent)` — `/topic/trip/{id}` 발행 헬퍼
- `getCollaboratingTrips` / `getCollaborators`(`assertCanView`) / `inviteCollaborator`(`assertCanManage` + 소유자 자기초대 400 + 대상 존재 검증) / `removeCollaborator`(`assertCanManage`)
- **기존 메서드 권한 분기 일괄 교체**: 기존 `trip.getMemberId().equals(memberId)` 직접 비교를 `assertCanView/Edit/Manage` 호출로 치환 (`getTripDetail`, `deleteTrip`, `addCandidate`, `removeCandidate`, `placeBlock`, `updateBlock`, `removeBlock` 등)
- `placeBlock`에 후보군 소속 검증 추가(`candidate.getTripId().equals(tripId)` 아니면 403)

**회원 검색(초대용)** — `member/controller/MemberController.java`

- `GET /api/members/search?q=` → `searchMembers` (id·nickname·email Map 반환)
- `MemberMapper.searchByNicknameOrEmail(q)` — 닉네임/이메일 LIKE, `LIMIT 20` (`#{}` 바인딩 준수)

### ③ 실시간 Presence (커서·드래그·블록 잠금)

**`TripPresenceController.java`** (`@MessageMapping`)

- 상태 맵 4종: `presenceMap`(tripId→sessionId→`PresenceState`), `grabMap`(tripId→blockId→memberId), `sessionMemberMap`, `sessionTripMap`
- `record PresenceState(memberId, nickname, x, y, interaction, targetBlockId, lastSeen, snapDayIndex, snapTop, cursorRelY, grabOffsetX, grabOffsetY)`
- `handlePointer(tripId, payload, principal, headerAccessor)` — `@MessageMapping("/trip/{tripId}/pointer")`. payload 파싱 → `PresenceState` 갱신 → `interaction=="grab"`+targetBlockId면 grab 등록, 아니면 해제 → `broadcastPresence`
- `getGrabOwner(tripId, blockId)` — 블록 잠금 소유자 조회(서비스에서 사용)
- `evictStale()` — `@Scheduled(fixedDelay=2000)`, 5초(`STALE_MILLIS`) 무응답 세션 정리
- `onDisconnect(SessionDisconnectEvent)` — 세션 종료 시 presence·grab 해제
- `broadcastPresence(tripId)` — `PRESENCE_UPDATE` 이벤트를 `/topic/trip/{id}/presence`로 발행

**브로드캐스트 이벤트** — `plan/dto/TripEvent.java`

- record `(type, actorId, actorNickname, payload, timestamp)` + 정적 팩토리 `of(type, actorId, actorNickname, payload)`
- 타입: `BLOCK_ADDED/MOVED/DELETED`, `CANDIDATE_ADDED/REMOVED`, `TRANSIT_RECALCULATED`, `PRESENCE_UPDATE`
- 편집 서비스 메서드들이 변경 직후 `broadcast(...)` 호출해 타 협업자에 반영

### ④ 프론트엔드

**`stores/collab.js`** (Pinia, STOMP 클라이언트)

- 상태: `participants`, `grabMap`(blockId→memberId), `colorMap`(memberId→hex), `connected`
- 내부 변수: `stompClient`, `reconnectTimer`, `isReconnecting`, `keepaliveTimer`, 핸들러 `onTripEvent/onPresence/onReconnect`
- `setHandlers({tripEvent, presence, reconnect})` — 콜백 주입
- `connect(tripId)` — 기존 클라이언트 정리 후 `_doConnect`
- `_doConnect(tripId)` — 핸드셰이크 전 `tripApi.get`으로 토큰 갱신 → STOMP 연결, `/topic/trip/{id}`·`/presence` 구독, `onDisconnect` 시 3초 후 재연결(`isReconnecting`)
- `startKeepalive/stopKeepalive` — 4초 간격 빈 pointer 발행으로 presence 유지
- `sendPointer(tripId, payload)`, `disconnect()`, `isGrabbedByOther(blockId, myMemberId)`, `assignColors(list, myMemberId)`(PALETTE 8색)

| 파일                                                    | 작업                                                                       |
| ------------------------------------------------------- | -------------------------------------------------------------------------- |
| `components/CollaboratorPanel.vue`                      | 협업자 목록·회원검색 초대·역할표시·삭제 패널(신규)                         |
| `components/ScheduleBoard.vue`                          | 협업자 커서·드래그 실루엣·drop-preview 시각화 이식, presence 송수신 연결   |
| `api/trip.js`                                           | `collaborating`/`collaborators`(GET·POST·DELETE)/`searchMembers` 호출 추가 |
| `components/ScheduleModal.vue`·`App.vue`·`schedule.css` | 패널 연동·스타일                                                           |
| `package.json`·`vite.config.js`                         | `@stomp/stompjs`·`sockjs-client` 의존성, dev 서버 `/ws` 프록시·LAN host    |

### ⑤ 리팩토링 / 문서

| 항목                                          | 내용                                              |
| --------------------------------------------- | ------------------------------------------------- |
| `b3590d9 refactor(collab)`                    | presence 부하 절감·컴포넌트 독립성 (아래 §4 참조) |
| `docs/04_logs/2026-06-22.md`, `2026-06-23.md` | 작업 일지                                         |

## 3. 사후 리팩토링 분석 — `b3590d9 refactor(collab): presence 부하 절감 및 컴포넌트 독립성 개선`

### ① `TripPresenceController.handlePointer` — 커서 권한 검증

- 원래: 커서 이동마다 `tripMapper.findById` + `collaboratorMapper.findByTripAndMember`로 DB 2회 조회.
- 수정: 검증을 `JwtChannelInterceptor.handleSend()`(SEND 단계 검증+세션 캐시)에 위임하고 제거. 미사용 `TripMapper`·`TripCollaboratorMapper` 의존성 삭제 → throttle·keepalive 고빈도 경로 DB 부하 제거.

### ② `stores/collab.js`

- `connect()`: 이전 STOMP 클라이언트 미정리 → 누수·중복 구독. 재호출 시 `deactivate` 후 재연결하도록 보강. 미사용 `activeTripId` 변수 제거.
- grab 판정: `targetBlockId`만으로 잠금 처리 → 커서 이동 시 오인. `interaction === 'grab'` 인 경우만 잠금(서버 정의 일치).
- 죽은 코드 `resetColors()` 제거.

### ③ `components/ScheduleBoard.vue`

- 협업자 이미지: `loadTrip()`마다 재조회 → `connectCollab()`(일정 전환 1회)로 이동.
- `day-col` DOM 조회: 전역 `document.querySelectorAll` → `wrapperEl` 범위 한정 `dayColAt()` 헬퍼.
- 외부 클릭 감지: 전역 클래스 셀렉터 → `collabPanelRef`/`collabBtnRef` ref 기반.
- drop-pulse keyframe: 외부 `schedule.css` 의존 → 컴포넌트 내부 `collab-drop-pulse`로 격리.

### 후속 수정

- 위 리팩토링이 `collab.js`의 `let activeTripId` 선언만 제거하고 `disconnect()` 내 `activeTripId = null` 잔존 → ES 모듈 strict mode에서 `ReferenceError`. 사후 리뷰 중 발견하여 해당 줄 삭제로 수정.
