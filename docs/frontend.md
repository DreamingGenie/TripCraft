# 프론트엔드 아키텍처 — TripCraft (Vue 3)

Vite 기반 Vue 3 SPA. 상태는 Pinia, 라우팅은 Vue Router, 서버 통신은 `fetch` 래퍼.
소스: `frontend/src/`.

---

## 1. 디렉터리 구성

| 경로 | 책임 |
|------|------|
| `views/` (18) | 라우트 단위 페이지 (Landing·Explore·Plan·Community·MyPage 등) |
| `components/` (15) | 재사용 UI (ScheduleBoard·CalendarBoard·AttractionChat·TiptapEditor·*Modal 등) |
| `stores/` (5) | Pinia 전역 상태 |
| `api/` (7) | 도메인별 API 모듈 + 공통 `http` 래퍼 |
| `composables/` | `useCollabCursor`(협업 커서 좌표)·`usePostList`(목록 페이징) |
| `router/` | 라우트 정의 + 네비게이션 가드 |
| `config/` | 런타임 설정(`collab.js` 협업 타이밍 등) |
| `assets/`·`utils/` | 스타일·유틸 |

## 2. API 레이어 — `api/http.js`

`fetch` 기반 경량 래퍼(axios 미사용). 모든 요청 `credentials: 'include'`(쿠키 전송).
- **공통 응답 언래핑**: 백엔드 `ApiResponse<T>`에서 `json.data`만 반환. `success=false`/비 2xx면 `message`로 `Error` throw(`err.status` 포함).
- **401 자동 갱신**: 401 응답 시 `POST /api/auth/refresh` 후 **원요청 1회 재시도** → 실패하면 throw. (멀티파트는 `requestForm` 별도 경로)
- 메서드: `http.{get,post,put,patch,del,postForm}`.
- 도메인 모듈: `attraction.js`·`member.js`·`place.js`·`post.js`·`transit.js`·`trip.js`가 `http`를 래핑.

## 3. 상태 관리 — Pinia stores

| store | 역할 |
|-------|------|
| `auth` | 로그인 상태·사용자(`role` 포함)·로그인/로그아웃. 라우터 가드의 권한 판단 근거 |
| `activeTrip` | 현재 편집 중인 일정 상세·후보군·블록 |
| `collab` | 실시간 협업 STOMP 클라이언트·참가자(presence)·grab 잠금 |
| `attractionChat` | 관광지 AI 챗봇 멀티턴 대화 상태 |
| `toast` | 전역 토스트 알림 |

## 4. 라우팅 — `router/`

- 코드 스플리팅(`() => import(...)`)으로 뷰 지연 로딩.
- `meta.requiresAuth` / `meta.requiresAdmin`를 네비게이션 가드에서 검사 → 미인증은 `/auth`, 비관리자는 `/plan`으로 리다이렉트.
- `/mypage`는 중첩 라우트(trips·profile·places·map·posts·bookmarks·likes), 레거시 경로(`/explore`·`/trips`·`/calendar`)는 리다이렉트로 흡수.
- `/plan/:tripId?`가 일정 편집 핵심 화면. 비로그인 사용자가 `/discover` 접근 시 로그인 상태면 `/plan`으로 보냄.

## 5. 실시간 협업 클라이언트 — `stores/collab.js`

- `@stomp/stompjs` + `sockjs-client`로 `/ws` 연결. **인증은 쿠키**(`access_token`)로 핸드셰이크 시 처리 → `connectHeaders` 비움.
- **연결 전 프리페치**: `tripApi.get(tripId)`를 먼저 호출해 만료 쿠키를 `http.js` 401 갱신 로직으로 최신화한 뒤 핸드셰이크(인증 안정화).
- **관전(observer) 모드**: 비로그인 익명 사용자는 구독만(수신) — 전송·keepalive·프리페치 없음. 백엔드가 공유(비 PRIVATE) 일정에 한해 익명 SUBSCRIBE 허용.
- **수동 재연결**(`reconnectDelay: 0` + 자체 타이머), 재호출 시 이전 클라이언트 정리(중복 구독·누수 방지), 재연결 시 `loadTrip()` 콜백으로 상태 재동기화.
- 구독: `/topic/trip/{id}`(블록 변경 `TripEvent`)·`/topic/trip/{id}/presence`(참가자 커서/아바타). 발행: `/app/trip/{id}/pointer`.
- 커서 좌표는 절대 픽셀이 아닌 **zone 기반 비율 좌표**(`useCollabCursor.js`) — 화면 크기가 달라도 일관. 튜닝값은 `config/collab.js`.

서버 측 채널 권한·presence 처리는 [features/auth-security.md](features/auth-security.md)·[features/realtime-collab.md](features/realtime-collab.md) 참조.

## 6. 시각 디자인
컴포넌트 스타일·토큰은 [design-system.md](design-system.md) ("Warm Editorial Workspace").
