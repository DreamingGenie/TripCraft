# TripCraft — UI 전면 리디자인 명세 v1.0

> **작성일**: 2026.05.29  
> **범위**: 전체 페이지 구조 재설계 + 신규 페이지 추가  
> **배경**: 기능 중심 누적 개발로 인한 UI 복잡도 해소, 사용성 중심 전면 재설계

---

## 1. 변경 요약

| 페이지 | 변경 유형 | 핵심 변경 내용 |
|--------|-----------|---------------|
| `/` LandingView | **신규** | 서비스 소개 랜딩 페이지 |
| `/about` AboutView | **신규** | 팀·기술 스택 소개 |
| `*` NotFoundView | **신규** | 404 에러 페이지 |
| `/auth` AuthView | **구조 변경** | Split-screen 레이아웃 |
| `/explore` ExploreView | **구조 변경** | 지도 중심 재설계 |
| `/schedule` ScheduleView | **구조 변경** | 타임라인 중심 재설계 |
| `/community` CommunityView | **구조 변경** | 단일 컬럼 피드 |
| `/admin` AdminView | **스타일 교체** | TripCraft 팔레트 통일 |
| GNB | **개선** | 로고·nav 스타일 강화 |

---

## 2. 신규 페이지

### 2-1. LandingView (`/`)
비로그인 사용자에게 표시. 로그인 상태면 `/explore`로 자동 리다이렉트.

**구조**
```
GNB (랜딩 전용 — 로그인·탐색 버튼)
Hero Section  — 헤드라인 + 서브텍스트 + CTA 버튼 2개 + mock UI 패널
Features Section  — 4열 카드 (탐색 / 일정 / 이동 계산 / 커뮤니티)
CTA Band  — --purple-900 배경, "지금 시작하기" 버튼
Footer  — 로고 + 팀명 + 링크
```

**파일**: `src/views/LandingView.vue`, `src/assets/css/landing.css`

---

### 2-2. AboutView (`/about`)
**구조**: 프로젝트 설명 → 기술 스택 뱃지 → 팀원 카드(전진·송정기) → CTA  
**파일**: `src/views/AboutView.vue` (scoped style)

---

### 2-3. NotFoundView (`/:pathMatch(.*)`)
**구조**: 중앙 정렬, 큰 "404" 숫자, "홈으로" + "탐색" 버튼  
**파일**: `src/views/NotFoundView.vue` (scoped style)

---

## 3. 페이지별 구조 변경

### 3-1. AuthView — Split-screen

**이전**: 단순 중앙 카드 폼  
**이후**: 좌(45%) 브랜드 패널 + 우(55%) 폼 패널

```
┌─────────────────────┬──────────────────────────┐
│  --purple-900 배경   │  로그인 / 회원가입 탭    │
│  TripCraft 로고     │  input 필드              │
│  슬로건             │  submit 버튼             │
│  feature bullets    │  "탐색 먼저 해볼게요" 링크│
└─────────────────────┴──────────────────────────┘
```

- 720px 이하: 브랜드 패널 숨김, 폼 패널만 표시
- JS 로직(handleLogin, handleRegister) 100% 유지

---

### 3-2. ExploreView — 지도 중심 재설계

**이전**: [내 일정 사이드바 220px] [카드 목록 300px] [지도]  
**이후**: 지도 전체 배경 + float 패널들

```
┌───────────────────────────────────────────────────┐
│ ┌──────────────────┐                              │
│ │  LEFT PANEL      │                              │
│ │  (360px, float)  │     NAVER MAP (전체 화면)    │
│ │                  │                              │
│ │  🔍 검색창       │                              │
│ │  필터 칩 (인라인) │                              │
│ │  N개의 장소      │    ┌──────────────────────┐  │
│ │  카드 목록 1열   │    │  내 일정 트레이       │  │
│ └──────────────────┘    └──────────────────────┘  │
└───────────────────────────────────────────────────┘
```

**Left Panel (360px)**
- 지도 위에 float, 3중 그림자 (1px close + 8px mid + 24px purple haze)
- 검색창 44px, 필터 칩 항상 노출 (드롭다운 패널 제거)
- 카드 1열, 이미지 128px, 무한 스크롤

**내 일정 트레이 (Trip Tray)**
- 위치: `position: fixed; bottom: 24px; right: 24px`
- 기본 상태: 접힌 48px pill "📋 내 일정 (N개)"
- 드래그 시작 시 자동 확장 → 일정 카드들이 드롭 타겟으로 표시
- 각 일정 카드: `@dragover.prevent` + `@drop="onDropToTrip($event, s)"`
- drop-over 시 teal border + scale(1.01)

**추가된 UI state**
```js
const trayOpen = ref(false)
const isDraggingCard = ref(false)
```

---

### 3-3. ScheduleView — 타임라인 중심 재설계

**이전**: [후보군 사이드바 240px 고정] [타임라인]  
**이후**: 토글 가능한 사이드바 + 전체 화면 타임라인

```
┌─────────────────────────────────────────────────────┐
│  TOOLBAR (52px)                                     │
│  [☰] 서울 3박4일 ▾  ·  3박4일 · 2명  |  [+] [💾] │
├──────────┬──────────────────────────────────────────┤
│ SIDEBAR  │  Day1    Day2    Day3    Day4             │
│ (240px)  │  ──────  ──────  ──────  ──────          │
│ 토글 가능 │  [경복궁 블록]                           │
│          │  🚌 40분                                 │
│ 접으면   │  [광장시장 블록]                         │
│ 타임라인  │                                          │
│ 전체 차지│                                          │
└──────────┴──────────────────────────────────────────┘
```

**툴바**
- `[☰]` 버튼: `sidebarOpen.value = !sidebarOpen.value`
- 일정 `<select>`: pill 스타일
- 인원/날짜 meta pill
- `[+ 새 일정]`: --purple-900 solid pill
- `[💾 저장]`: ghost pill

**사이드바 토글**
- `v-show="sidebarOpen"` + width/opacity transition 250ms
- 지역 헤더: border-left 3px --purple-900, --purple-50 bg
- 배치된 후보(`.placed`): opacity 0.4 + strike-through

**이벤트 블록**
- border-radius 10px, border-left 4px (카테고리별 색상)
- hover: translateY(-1px) + 8px×20px shadow
- drop-preview: pulsing opacity 애니메이션

**추가된 UI state**
```js
const sidebarOpen = ref(true)
```

---

### 3-4. CommunityView — 단일 컬럼 피드

**이전**: [콘텐츠] [우측 사이드바 210px]  
**이후**: max-width 720px 단일 컬럼

```
┌────────────────────────────────────────────────┐
│  여행 이야기         [최신순][인기순] [✏글쓰기] │
├────────────────────────────────────────────────┤
│  📢 공지 배너 (notices 있을 때만)               │
├────────────────────────────────────────────────┤
│  게시글 카드 (hover lift)                       │
│  게시글 카드                                    │
│  ...                                           │
│  [< 1 2 3 >]                                   │
└────────────────────────────────────────────────┘
```

- 우측 사이드바 제거
- 공지사항: 상단 그라디언트 배너 (`v-if="notices.length"`)
- 인기글: 공지 배너 내 또는 별도 섹션
- 상세 뷰: 동일 max-width, line-height 1.9 읽기 최적화

---

### 3-5. AdminView — 팔레트 통일

- `--color-primary: #2563eb` (파란색) 완전 제거
- 모든 버튼, 링크, 포인트 → `--purple-900`
- 결과 ok → `--teal-50` / `--teal-600`
- 카드 `border-left: 4px solid --purple-900` 포인트 추가

---

## 4. 라우터 변경

```js
// 변경
{ path: '/', component: LandingView }           // 기존: redirect '/explore'

// 추가
{ path: '/about', component: AboutView }
{ path: '/:pathMatch(.*)*', component: NotFoundView }
```

**라우터 가드**: 기존 requiresAuth, requiresAdmin 유지

---

## 5. CSS 파일 현황

| 파일 | 상태 | 비고 |
|------|------|------|
| `variables.css` | 유지 | 디자인 토큰 (변경 없음) |
| `common.css` | 수정 | GNB 로고 강화, nav active 하단 라인 |
| `explore.css` | 전면 재작성 | 지도 중심 레이아웃, tray 스타일 |
| `schedule.css` | 전면 재작성 | 툴바, 사이드바 토글, 이벤트 블록 |
| `community.css` | 전면 재작성 | 단일 컬럼, 공지 배너, modal 애니메이션 |
| `auth.css` | 전면 재작성 | split-screen 레이아웃 |
| `landing.css` | **신규** | 랜딩 페이지 전용 |

---

## 6. 보호된 비즈니스 로직

아래 로직은 리디자인 과정에서 일절 변경되지 않음.

- **API 호출**: `searchAttractions`, `fetchRegions`, `tripApi.*`, `postApi.*`
- **인증**: `useAuthStore`, `auth.fetchMe`
- **데이터 변환**: `searchResultGroups`, `candidateRegionGroups`, `cityGroups`
- **드래그 핸들러**: `onCardDragStart`, `onDropToTrip`, `onEventDragStart`, `onDrop`, `onResizeStart/Move/End`
- **지도**: `loadNaverScript`, `initMap`, `updateMarkers`, `fitMap`, `clearMarkers`
- **무한 스크롤**: `loadMore`, `backgroundLoadAll`, `checkScroll`, `loadSeq`
- **이동 시간**: `getTransitPills`, `openTransitDetail`, `handleTransitSelect`
