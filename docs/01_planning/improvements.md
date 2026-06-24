# 개선점 · 기술 부채 정리

> 시간·우선순위·리스크 때문에 **의도적으로 미루었거나 미처 다루지 못한 항목**을 기록한다.
> "안 한 것이 아니라, 알고 미룬 것"임을 남겨 후속 작업/인수인계의 백로그로 삼는다.
> 작성 기준일: 2026-06-23 · 대상 브랜치: `master`
>
> 각 항목 형식: **무엇 / 위치 / 왜 부채인가 / 미진행 사유 / 개선 방향 / 우선순위**

---

## 1. `ScheduleBoard.vue` God Component

- **위치**: `frontend/src/components/ScheduleBoard.vue` (약 2,273줄 = template 541 / script 1,613 / style 118, 함수 ~70개)
- **왜 부채인가**: 한 컴포넌트가 ① 후보군 사이드바 ② 타임테이블 드래그앤드롭 ③ 이동시간 Pill ④ 지도 패널(Naver) ⑤ 실시간 협업, 5개 도메인을 모두 담당. 변경 영향 범위 파악·디버깅 비용이 큼.
- **미진행 사유**: 테스트 안전망 부재 + 제출 임박 시점이라 대규모 분리의 회귀 리스크가 정리 이득보다 큼. ②·⑤는 `days`/ghost 등 공유 reactive 상태에 강결합되어 분리 시 prop/emit 드릴링이 오히려 복잡.
- **개선 방향**: 자식 컴포넌트가 아닌 **composable 추출**을 독립도 높은 순서로 점진 적용 — ④ 지도 → `composables/useTripMap.js` (가장 독립적, 300줄+ 즉시 감량), ③ Pill → `useTransitPills.js`. ②·⑤는 마지막.
- **우선순위**: 중간 (유지보수 지속 시 높음)

## 2. 자동화 테스트 부재

- **위치**: 백엔드(`backend/src/test`)·프론트엔드 전반
- **왜 부채인가**: 회귀를 잡아줄 안전망이 없어 1번 같은 리팩토링·기능 확장의 진입장벽이 됨. 권한 판정, 드래그앤드롭, 이동시간 계산 등 핵심 경로가 수동 검증에만 의존.
- **미진행 사유**: 기능 구현 우선, 기간 내 커버리지 확보 시간 부족.
- **개선 방향**: 위험·복잡 경로부터 최소 단위로 — 백엔드 권한 헬퍼(`TripServiceImpl.resolveRole/assert*`)·STOMP 인증(`JwtChannelInterceptor`) 단위 테스트, 프론트 DnD/일정 계산 로직 테스트.
- **우선순위**: 높음

## 3. 레거시 · 신규 UX 병행 (중복 유지보수)

- **위치**: 라우팅 `frontend/src/router/index.js` — 구) `/explore`(`ExploreView.vue`, 1,503줄) + `/schedule`(`ScheduleView.vue`) / 신) `/plan`(`PlanView.vue`, 1,698줄, beta). `ScheduleBoard.vue`를 `PlanView`·`ScheduleView`가 공유. 네비게이션(`AppHeader.vue`)에 세 진입점 모두 노출.
- **왜 부채인가**: "탐색+일정 통합 작업실 `/plan`"이 기존 두 화면을 대체할 의도였으나 구 화면이 병행 운영 중. 동일 기능이 두 경로에 존재해 수정 시 양쪽을 맞춰야 함.
- **미진행 사유**: `/plan`이 beta 단계라 안정화 전까지 레거시를 남겨둠. 전환 완료·구 화면 제거 시점 미결정.
- **개선 방향**: `/plan` 안정화 후 ① 네비게이션을 `/plan` 단일화 → ② `/explore`·`/schedule` 라우트 및 `ScheduleView`·중복 탐색 코드 제거. 결정 전까지 "어느 쪽이 정본인지" 문서화.
- **우선순위**: 중간

## 4. 실시간 Presence 권한을 SEND 캐시에 위임 (의도적 트레이드오프)

- **위치**: `backend/.../plan/controller/TripPresenceController.java:53` (`handlePointer`) ↔ `global/security/JwtChannelInterceptor.java` (`handleSend`, 세션 속성 `tripAccess:{tripId}` 캐시)
- **왜 부채인가**: 커서 throttle·keepalive로 호출이 잦은 `handlePointer`에서 매번 DB 권한 조회를 제거하고 SEND 프레임 단계의 1회 검증+세션 캐시에 의존. 부하는 줄었으나, **세션 수명 동안 권한 회수(협업자 삭제·역할 강등)가 즉시 반영되지 않는 창**이 생김.
- **미진행 사유**: `refactor(collab) b3590d9`에서 부하 절감을 우선해 도입한 의도적 설계. 캐시 무효화 경로는 별도 검증 미완.
- **개선 방향**: 협업자 삭제/역할 변경 시 해당 세션 캐시 무효화 또는 강제 재구독(disconnect) 트리거. 최소한 만료 권한 윈도우의 허용 범위를 명문화.
- **우선순위**: 중간 (보안·정합성 관점)

## 5. Controller의 `Map<String, Object>` 직접 파싱 / 응답

- **위치**: `plan/controller/TripController.java:60` (`inviteCollaborator` — `body.get("memberId").toString()` 수동 파싱), `:167` (`updateDefaultTransitMode`), `member/controller/MemberController.java:129` (`searchMembers` — DTO 대신 `Map` 응답)
- **왜 부채인가**: 요청/응답이 `Map` 기반이라 컴파일 타임 타입 안전성·Bean Validation·Swagger 스키마 이점을 못 받음. 잘못된 키/타입은 런타임 NPE/`NumberFormatException`으로만 드러남.
- **미진행 사유**: 빠른 구현을 위해 임시로 `Map` 사용.
- **개선 방향**: 전용 요청 DTO(`InviteCollaboratorRequest` 등)·응답 DTO(`MemberSearchItem`) 도입 + `@Valid` 적용.
- **우선순위**: 낮음

## 6. 빈 `catch {}` 로 인한 에러 무시

- **위치**: `frontend/src/components/ScheduleBoard.vue` 7곳(1094, 1362, 1411, 1427, 1593, 1595, 1684), `stores/auth.js:69`, `components/MyPageLayout.vue:48`, `views/CommunityView.vue:188` 등
- **왜 부채인가**: 예외를 삼켜 실패가 조용히 무시됨 → 지도 경로·이동시간·프로필 로딩 등이 말없이 깨져도 원인 추적이 어려움.
- **미진행 사유**: "실패해도 화면은 동작" 우선의 방어 코드로 작성, 로깅/사용자 피드백은 후순위.
- **개선 방향**: 최소 `console.warn`/사용자 토스트로 가시화하고, 무시해도 되는 경우는 그 이유를 주석으로 명시.
- **우선순위**: 낮음~중간

## 7. 협업 지도 커서 좌표 — lat/lng 미변환 (현재 지도 커서 숨김 처리)

- **위치**: `frontend/src/composables/useCollabCursor.js` (`map` zone — `mapRatioX/mapRatioY`, `cursorStyle` map 분기), `components/ScheduleBoard.vue`(커서 오버레이 `v-if`, `naverMapInstance`)
- **왜 부채인가**: 커서 좌표계 재설계로 시간표는 의미 좌표(dayIndex·colRatioX·contentY)로 정밀 동기화했으나, 지도 영역은 **컨테이너 내 0~1 비율**로만 근사 가능하다. 두 사용자의 지도 줌·팬·패널 폭이 다르면 같은 비율이 다른 지리 지점을 가리켜 어긋난다.
- **현재 상태(2026-06-24)**: 어긋난 커서를 보여주느니 **지도 zone 커서를 숨김 처리**함. 송신측은 여전히 `mapRatioX/Y`를 실어 보내지만(payload 호환 유지), 수신측 오버레이 `v-if`가 `p.zone === 'timetable'`로 한정되어 map zone 커서가 렌더되지 않는다. lat/lng 구현 완료 시 이 `v-if`만 풀면 즉시 활성화 가능.
- **미진행 사유**: 의미 단위 정확(같은 day·시간·블록)을 1차 목표로 합의(사용자 확정). lat/lng 변환은 Naver Maps projection API 검증 시간이 추가로 필요.

### 구현 계획 (바로 착수 가능)

설계: 커서 픽셀 ↔ 지리 좌표를 Naver projection으로 변환. **`mapRatioX/Y`는 삭제하지 말고 fallback으로 유지**, `mapLat/mapLng`를 추가하는 점진 방식(회귀 위험 0).

1. **`ScheduleBoard.vue`** — 모듈 `let naverMapInstance`를 composable에 **getter로 주입**(`getMap: () => naverMapInstance`). 인스턴스가 `initMap` 이후 늦게 할당되므로 값이 아닌 getter여야 함.
2. **`useCollabCursor.js` 송신** — `buildPointerPayload` map 분기에서 `getMap()?.getProjection()`이 있으면 `fromOffsetToCoord(new naver.maps.Point(clientX - mapRect.left, clientY - mapRect.top))` → `mapLat/mapLng` 추가(비율도 함께 유지).
3. **`useCollabCursor.js` 수신** — `cursorStyle` map 분기에서 `mapLat/mapLng`+projection 준비 시 `fromCoordToOffset(new naver.maps.LatLng(lat, lng))` → `mapRect.left/top + offset`. 없으면 기존 비율 fallback.
4. **`ScheduleBoard.vue` 오버레이 `v-if`** — `p.zone === 'timetable'` 제한을 `(p.zone === 'timetable' || p.zone === 'map')`로 환원.
5. **`TripPresenceController.java`** — `PresenceState`에 `mapLat/mapLng` 추가, `handlePointer` 파싱·`broadcastPresence` 출력에 동일 키. 비율 필드는 유지.
6. **`collab.js`** — participants 주석 스키마에 `mapLat/mapLng` 추가(코드 영향 없음).

**엣지 케이스**: ① `getProjection()`이 init 직후 null → guard 후 비율 fallback. ② `fromOffsetToCoord` 기준점은 지도 컨테이너 좌상단이므로 `clientX - mapRect.left` 변환 필수. ③ 수신측 패널 닫힘 시 표시 안 함(현행 유지).

**작업량**: 구현 ~1.5~2h(projection 타이밍·역변환 디버깅), 검증 2계정·서로 다른 줌/팬/폭 2창 E2E ~30m. 리스크 낮음(map 분기만 교체, timetable 무관, 백엔드는 필드 추가).

- **우선순위**: 낮음~중간 (지도 협업 빈도에 따라)

## 8. 디버그 `console` 로그 잔존

- **위치**: `frontend/src/stores/collab.js`(2곳), `views/PlanView.vue`, `views/ExploreView.vue`, `components/CollaboratorPanel.vue`
- **왜 부채인가**: 프로덕션 콘솔 노이즈. 일부는 페이로드 노출 가능성.
- **미진행 사유**: 개발 중 디버깅 흔적 미정리.
- **개선 방향**: 빌드 시 제거(예: vite `esbuild.drop`) 또는 정식 로깅 유틸로 일원화.
- **우선순위**: 낮음

---

## 9. 협업 동시성 — 견고 범위 적용 후 남은 항목

> **적용 완료(2026-06-24)**: ① `trip_block.version` 낙관적 락(같은 블록 동시 수정 → 409·재조회) ② grab 서버 게이트(드래그 중 블록 선제 차단) ③ transit 전용 업데이트 분리 + 외부 API를 변경 트랜잭션 커밋 후(afterCommit)로 이동. 충돌 기준은 `version`이 "같은 row"에만 작용 → 무관 편집·transit 재계산은 오탐 없음.
> 아래는 비용·일정상 이번에 미룬 항목. 위 적용을 전제로 후속 보강.

- **9-1. displayOrder 정합성 (우선순위: 높음)** — 서로 다른 블록이 같은 `(trip_date, display_order)`를 가질 수 있음(UNIQUE 없음, "앱 레이어 관리" 주석). 서로 다른 블록을 같은 슬롯에 놓는 충돌은 `version`(다른 row)으로 못 막음. 개선: 서버 권위 정규화(배치/이동 시 서버가 순서 재할당 후 브로드캐스트) 또는 `UNIQUE(trip_date, display_order)` + 재시도. 현재는 커서 인지 + LWW에 의존.
- **9-2. removeCandidate TOCTOU (중간)** — `existsBlockByCandidateId` 체크와 `deleteById` 사이에 동시 `placeBlock`이 끼면 `ON DELETE RESTRICT`로 FK 예외 발생(사용자에겐 의도와 다른 메시지). 개선: 후보 행 `SELECT … FOR UPDATE` 또는 RESTRICT 예외를 "배치된 블록이 있습니다"로 변환.
- **9-3. 브로드캐스트 이벤트 순서·유실 (중간)** — `TripEvent`에 시퀀스 번호 없음. 순서 역전·유실 감지 불가, 재연결 시 `loadTrip` 전체 재조회에만 의존. 개선: seq 부여 + 클라이언트 gap 감지 시 재동기화, 재연결 누락분 조회 엔드포인트.
- **9-4. presence in-memory 경합 (중간, 표시 수준)** — `TripPresenceController`의 `computeIfAbsent` 복합 연산 비원자성, 재연결 시 유령 참가자(중복 sessionId), `broadcastPresence` 스냅샷 TOCTOU, 세션맵 원자성. 데이터 유실은 아니고 커서 깜빡임/유령 커서/잔존 grab 수준. 개선: tripId 단위 동기화 또는 세션 상태 단일 객체화 + 멱등 정리.
- **9-5. STOMP 권한 캐시 무효화 (중간)** — 협업자 제거/강등 시 세션 캐시(`tripAccess:{id}`)가 즉시 무효화되지 않아, 세션 수명 동안 presence 전송이 통과(§4 연장). 개선: 권한 변경 이벤트로 해당 세션 캐시 무효화 또는 강제 재구독.
- **9-6. 커서 throttle·broadcast 부하 (낮음)** — keepalive(4초)·mousemove마다 전체 참가자 목록 재직렬화·broadcast. 대규모 동시 접속 시 대역폭. 개선: 좌표 델타 전송 또는 broadcast 합치기(coalescing).
- **9-7. 블록 이벤트 부분 패치 (낮음)** — `BLOCK_*` 수신 시 전체 `loadTrip` 재조회 → optimistic UI와 겹쳐 깜빡임. 현재 `TRANSIT_RECALCULATED`만 부분 패치. 개선: 블록 이벤트도 부분 반영.

---

## (검수 결과) 부채가 아니거나 이미 양호한 항목

- **MyBatis `${}` 미사용** — 매퍼 전수 확인 결과 `#{}` 바인딩만 사용. SQL Injection 컨벤션 준수.
- **TODO/FIXME 방치 없음** — 소스 전반에 미해결 표식 없음.
- **`collab.js disconnect()` ReferenceError** — 별도 수정 완료(MR로 처리), 본 목록에서 제외.

---

## 미구현 / 축소된 기능 (요구사항 대비)

> 시간 부족으로 빠지거나 범위를 줄인 기능을 여기에 기록한다. (담당자 추가 작성)

- _(예: …)_
