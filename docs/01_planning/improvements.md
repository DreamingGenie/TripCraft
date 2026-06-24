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

> **2차 적용 완료(2026-06-24)**: 아래 9-1·9-2·9-3·9-5 적용. 9-4는 재검증 결과 핵심 주장이 오진이라 분석 정정. grab 해제 누락 버그(드래그 종료 시 서버 grabMap이 안 비워져 stale까지 잠금 잔존)도 함께 수정.

- **9-1. displayOrder 서버 권위 할당 — ✅ 적용** — `placeBlock`은 클라 값을 무시하고 `nextDisplayOrder`(MAX+1)로 서버가 할당, 다른 날짜로 이동 시에도 그 날짜 기준 재할당. 동시 배치 시 순서 충돌을 완화. *잔여*: 두 `MAX+1` SELECT가 동시에 같은 값을 읽는 좁은 race는 남음(완전 차단은 `trip_block.trip_id` 컬럼 추가 + `UNIQUE(trip_id, trip_date, display_order)` 필요 — 스키마 확장이라 보류). "다른 블록을 같은 시간대에 겹치게 놓는" 시각적 겹침은 start_time 기반이라 별개이며 커서 인지에 위임.
- **9-2. removeCandidate TOCTOU — ✅ 적용** — 선제 `existsBlockByCandidateId` 체크는 친절 메시지용으로 유지하고, 실제 `deleteById`를 `DataIntegrityViolationException`(FK RESTRICT) 캐치로 감싸 동시 placeBlock이 끼어든 경우도 동일 메시지(409)로 변환.
- **9-3. 이벤트 시퀀스 — ✅ 적용(부분)** — `TripEvent.seq`(일정별 `AtomicLong` 단조 증가)를 `broadcast()`에서 스탬프, 프론트 `handleTripEvent`가 `seq <= lastEventSeq`면 무시(중복·역전 제거). *잔여*: 재연결 중 유실분 재조회 엔드포인트는 미구현(현재 reconnect `loadTrip` 전체 재조회로 수렴하므로 데이터 유실은 없음).
- **9-4. presence in-memory 경합 — ⚠️ 분석 정정(코드 변경 없음)** — 기존 "`computeIfAbsent` 복합 연산 비원자성" 주장은 **오진**: `ConcurrentHashMap.computeIfAbsent`는 원자적이라 동시 호출자가 같은 내부 맵을 받는다. 남은 항목(재연결 유령 sessionId, `broadcastPresence` 스냅샷 TOCTOU, 세션맵 2-put 비대칭)은 모두 **자가 회복·표시 수준**(다음 broadcast/stale evict로 수렴)이라 핫 패스(커서 이동마다)에 락을 거는 비용이 이득보다 큼 → 의도적으로 현행 유지. 우선순위 낮음으로 하향.
- **9-5. STOMP 권한 캐시 무효화 — ✅ 적용** — `TripAccessVersion`(일정별 세대 카운터) 도입. `inviteCollaborator`·`removeCollaborator`·`setShareAccess`가 `bump(tripId)` 호출, `JwtChannelInterceptor`가 캐시된 세대와 현재 세대가 다르면 SUBSCRIBE/SEND에서 권한 재검증. 협업자 제거 시 다음 프레임부터 차단.
- **9-6. 커서 throttle·broadcast 부하 (낮음, 미적용)** — keepalive(4초)·mousemove마다 전체 참가자 목록 재직렬화·broadcast. 대규모 동시 접속 시 대역폭. 개선: 좌표 델타 전송 또는 broadcast 합치기(coalescing). → 인원 확장 시 우선순위 상승([[#11 다중 사용자 위험]] 참조).
- **9-7. 블록 이벤트 부분 패치 (낮음, 미적용)** — `BLOCK_*` 수신 시 전체 `loadTrip` 재조회 → optimistic UI와 겹쳐 깜빡임. 현재 `TRANSIT_RECALCULATED`만 부분 패치. 개선: 블록 이벤트도 부분 반영.

---

## 10. CRDT / OT(자동 충돌 병합) 도입 검토

> 현재 정책은 "충돌 감지 → 거부 → 재조회·재시도"(낙관적 락). 자동 병합은 하지 않는다.
> 구글 닥스류의 **자동 병합**을 도입하려면 어느 정도 일인지 사전 검토.

**OT(Operational Transformation)** — 각 편집을 연산(op)으로 표현하고, 동시 op를 서버가 변환(transform)해 순서 무관하게 같은 결과로 수렴시킴. 중앙 서버 필요. (구글 닥스 방식)
**CRDT(Conflict-free Replicated Data Type)** — 자료구조 자체가 교환법칙을 만족하게 설계돼, 변환 없이 병합만으로 수렴. P2P/오프라인에 강함. (Yjs/Automerge)

### 우리 도메인에 맞는가
우리 편집 단위는 텍스트가 아니라 **블록(날짜·시각·길이·순서)** 이라, 일반 라이브러리(Yjs는 텍스트/배열/맵 중심)를 그대로 못 쓴다. 블록을 CRDT 맵/리스트로 모델링하는 **커스텀 설계**가 필요하다.

### 예상 작업량 (대략)
| 작업 | 규모 |
|------|------|
| 편집을 op 단위로 재설계(move/resize/place/remove를 의도 보존 연산으로) | 큼 |
| 서버 op 로그·버전 벡터(또는 Yjs 문서) 저장소 + 영속화 | 큼 |
| 클라이언트 로컬 적용 + 서버 변환/병합 + 재동기화 | 큼 |
| 기존 REST CRUD·`loadTrip` 재조회 모델을 op 스트림으로 대체 | 매우 큼(아키텍처 전환) |
| 동시성 테스트 하네스(다자·지연·재정렬 시나리오) | 큼 |

체감 **수 주(2~4주+) 규모의 아키텍처 전환**. 라이브러리(Yjs)를 써도 블록 모델 매핑·영속화·권한 통합에 상당한 통합 비용.

### 예상 효과
- **얻는 것**: 같은 블록 동시 편집도 거부 없이 자동 병합(거부 토스트 사라짐), 오프라인 편집 후 병합, 진짜 실시간 공동편집 UX.
- **한계**: CRDT/OT는 "**의도 충돌**"까지 해결하진 못함 — 예: A는 09:00, B는 14:00으로 같은 블록을 옮기면 둘 중 하나로 수렴할 뿐, "사용자가 원한 결과"를 보장하진 않는다(여전히 한쪽 의도 소실). 즉 자동 병합은 **데이터 정합성**은 보장하나 **의미 충돌**은 못 없앤다.

### 결론(권고)
현재 규모(여행 2~5인, 충돌이 잦지 않고 커서로 상호 인지)에는 **낙관적 락 + 거부·재시도로 충분**. CRDT/OT는 ROI가 낮다. **블록 단위 동시 편집이 일상이 되거나, 오프라인 편집·셀 단위 텍스트 협업이 요구사항이 될 때** 재검토할 후보로 남긴다. 우선순위: **낮음(현 시점), 조건부 상승**.

## 11. 다중 사용자(2인 초과) 위험 — 인원이 늘면 달라지는 것

> 테스트는 2인 기준이었지만 여행은 2~5인, 그 이상도 가능하다. "충돌이 드물다"는 전제는 **인원의 제곱에 비례해 약해진다**(동시 편집 쌍 ≈ nC2). 인원 확장 시 아래가 현실화된다.

- **11-1. 낙관적 락 거부율 상승** — 같은 블록·같은 날짜에 작업이 몰리면 409 재시도가 잦아져 "자꾸 튕긴다"는 체감. → grab 선제 차단의 가치가 커지고, 잦으면 §10(자동 병합) 재검토 트리거.
- **11-2. broadcast 부하 폭증 (→ 9-6 우선순위 상승)** — presence는 **인원마다** keepalive(4초)+커서 이동을 보내고, 매 포인터가 **전체 참가자 목록**을 재직렬화해 **전원에게** 재전송한다. 트래픽이 대략 O(n²)로 증가. 5인까지는 무난하나 그 이상이면 좌표 델타·coalescing·throttle 강화가 필요.
- **11-3. transit 재계산 중복** — 여러 명이 같은 날짜를 연달아 편집하면 afterCommit 재계산이 인원수만큼 중복 실행되고 외부 API 호출도 곱절. → 날짜 단위 디바운스/단일 비행(in-flight 합치기) 필요.
- **11-4. grab 잠금 경합** — 인원이 많으면 "내가 잡으려는 블록을 남이 잡고 있는" 빈도가 올라가 편집 흐름이 막힐 수 있음. grab 시각화(상대 커서/ghost)가 완충하지만, 5인 초과면 잠금 대기 UX(누가 잡았는지 + 양보 요청) 고려.
- **11-5. displayOrder MAX+1 race 확대 (→ 9-1 잔여)** — 동시 배치자가 많을수록 `MAX+1` 동시 읽기 충돌 확률↑. 인원 확장이 현실화되면 `trip_id` 컬럼 + UNIQUE로 하드 보장 승격.
- **11-6. 권한 캐시·세션 정리 부하** — presence in-memory 맵(§9-4)·세대 카운터가 인원·세션 수에 비례. 표시 수준이라 치명적이진 않으나, 유령 참가자 정리(stale evict 5초)가 인원 많을 때 더 눈에 띔.

> 요약: **데이터 정합성은 인원이 늘어도 낙관적 락+grab+권한세대로 지켜진다.** 인원 확장 시 먼저 무너지는 건 **정합성이 아니라 성능·UX**(broadcast 부하, 거부율, 재계산 중복)다. 따라서 다음 보강 1순위는 §9-6(broadcast 효율)과 §11-3(재계산 디바운스).

---

## (검수 결과) 부채가 아니거나 이미 양호한 항목

- **MyBatis `${}` 미사용** — 매퍼 전수 확인 결과 `#{}` 바인딩만 사용. SQL Injection 컨벤션 준수.
- **TODO/FIXME 방치 없음** — 소스 전반에 미해결 표식 없음.
- **`collab.js disconnect()` ReferenceError** — 별도 수정 완료(MR로 처리), 본 목록에서 제외.

---

## 미구현 / 축소된 기능 (요구사항 대비)

> 시간 부족으로 빠지거나 범위를 줄인 기능을 여기에 기록한다. (담당자 추가 작성)

- _(예: …)_
