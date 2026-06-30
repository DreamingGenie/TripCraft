# TripCraft — Figma 화면 설계 명세서

> **버전**: v0.2 | **기준**: 기획서 v0.2 | **작성일**: 2026.05  
> **변경 이력**: v0.1 → v0.2 — 일정 생성 모달 추가, 후보군 플로우 반영, 도시별 카테고리화, 즐겨찾기 자동 연동

---

## 1. 디자인 토큰

### 1-1. 색상 (Color Styles)

| Token 이름 | Hex | 용도 |
|---|---|---|
| `primary/purple-900` | `#534AB7` | 주요 액션, 활성 상태, 로고 |
| `primary/purple-100` | `#CECBF6` | 보더, 강조선 |
| `primary/purple-50` | `#EEEDFE` | 활성 칩 배경, 선택 상태 배경 |
| `semantic/teal-600` | `#0F6E56` | 관광지 블록 |
| `semantic/teal-50` | `#E1F5EE` | 관광지 블록 배경 |
| `semantic/pink-600` | `#993556` | 음식점 블록 |
| `semantic/pink-50` | `#FBEAF0` | 음식점 블록 배경 |
| `semantic/blue-600` | `#185FA5` | 숙박·정보 블록 |
| `semantic/blue-50` | `#E6F1FB` | 숙박·정보 블록 배경 |
| `semantic/amber-600` | `#854F0B` | 공지, 레포츠 블록 |
| `semantic/amber-50` | `#FAEEDA` | 공지 배경 |
| `neutral/gray-border` | `#D3D1C7` | 기본 선, 구분선 |
| `neutral/gray-muted` | `#888780` | 보조 텍스트, 레이블 |
| `neutral/gray-dark` | `#5F5E5A` | 서브 텍스트 |
| `neutral/text-primary` | `#2C2C2A` | 본문 텍스트 |
| `neutral/bg-page` | `#F5F4F0` | 페이지 배경 |
| `neutral/bg-surface` | `#FFFFFF` | 카드·패널 배경 |

### 1-2. 타이포그래피 (Text Styles)

| Style 이름 | Size | Weight | Line Height | 용도 |
|---|---|---|---|---|
| `heading/page` | 18px | 500 | 1.4 | 페이지 제목 |
| `heading/section` | 14px | 500 | 1.4 | 섹션 제목, 게시글 제목 |
| `body/regular` | 13px | 400 | 1.6 | 본문, 카드 내용 |
| `body/small` | 12px | 400 | 1.5 | 보조 설명, 날짜 |
| `label/default` | 11px | 400 | 1.4 | 카드 이름, 폼 레이블 |
| `label/bold` | 11px | 500 | 1.4 | 블록 타이틀, 사이드바 섹션명 |
| `caption` | 10px | 400 | 1.3 | 카테고리 태그, 메타 정보 |
| `caption/upper` | 10px | 500 | 1.3 | 사이드바 섹션 레이블 (uppercase) |

**폰트 스택**: `-apple-system`, `BlinkMacSystemFont`, `"Segoe UI"`, `"Noto Sans KR"`, `sans-serif`

### 1-3. 간격 (Spacing)

| Token | px | 용도 |
|---|---|---|
| `space/4` | 4px | 아이콘 + 텍스트 간격 |
| `space/6` | 6px | 인라인 요소 간격 |
| `space/8` | 8px | 카드 내부 패딩, 컴포넌트 간 |
| `space/12` | 12px | 패널 패딩 |
| `space/14` | 14px | 사이드바·메인 패딩 |
| `space/20` | 20px | GNB 수평 패딩 |
| `space/28` | 28px | 모달·인증 카드 패딩 |

### 1-4. 테두리 반경 (Border Radius)

| Token | px | 용도 |
|---|---|---|
| `radius/sm` | 4px | 미니 태그, 뱃지 |
| `radius/md` | 6px | 버튼, 블록, 입력 필드 |
| `radius/lg` | 8px | 카드, 검색바, 모달 |
| `radius/xl` | 12px | 관광지 카드 |
| `radius/pill` | 20px | 필터 칩, 이동시간 필 |
| `radius/full` | 50% | 아바타 |

### 1-5. 선 두께

- 기본 보더: `0.5px solid`
- 포커스 링: `1.5px solid primary/purple-900`
- 타임라인 연결선: `1px solid neutral/gray-border`
- 드롭존 비활성: `0.5px dashed neutral/gray-border`
- 드롭존 활성 (hover): `1.5px dashed primary/purple-900`

---

## 2. 공통 컴포넌트

### 2-1. GNB (Global Navigation Bar)

```
높이: 48px
배경: neutral/bg-surface
하단 보더: 0.5px / neutral/gray-border
수평 패딩: 20px
```

| 요소 | 규격 |
|---|---|
| 로고 | heading/section · primary/purple-900 · letter-spacing -0.3px |
| Nav Link (기본) | label/default · neutral/gray-muted · padding 4px 10px · radius/md |
| Nav Link (활성) | primary/purple-900 · bg primary/purple-50 |
| 버튼 — 로그인 (ghost) | label/default · 0.5px border · radius/md · padding 5px 12px |
| 버튼 — 회원가입 (primary) | label/default · bg primary/purple-900 · white · radius/md · padding 5px 12px |

### 2-2. FilterChip

```
기본: padding 3px 9px · radius/pill · border 0.5px neutral/gray-border · caption · color neutral/gray-muted
선택됨: bg primary/purple-50 · border primary/purple-900 · color primary/purple-900
```

Variants: `State=Default`, `State=Selected`

### 2-3. AttractionCard

```
border: 0.5px neutral/gray-border · radius/xl · overflow hidden
선택됨: border 0.5px primary/purple-900
후보군 등록됨: border 0.5px semantic/teal-600 · 우상단 체크 뱃지 표시

이미지 영역: height 64px · bg neutral/bg-secondary
정보 영역: padding 6px 8px
  장소명: label/bold · ellipsis
  카테고리: caption · neutral/gray-muted
  즐겨찾기 아이콘: ti-star / ti-star-filled · float right · color amber #FAC775
  후보군 추가 버튼: "+ 일정에 추가" · caption · primary/purple-900 · hover bg primary/purple-50
```

Variants: `State=Default`, `State=Selected`, `State=Candidate`

### 2-4. CandidateCard (후보군 카드)

후보군 사이드바에서 드래그 가능한 장소 카드.

```
border: 0.5px neutral/gray-border · radius/md · padding 8px 10px
background: neutral/bg-surface
cursor: grab · active: grabbing

구성:
  왼쪽: 카테고리 색상 세로 바 (3px · 높이 100% · radius 2px)
  중앙: 장소명(label/bold) + 카테고리(caption, muted)
  우측: ti-grip-vertical (14px, muted) — 드래그 핸들

자동 추가(즐겨찾기 연동): 우상단 ti-star-filled (amber, 11px) 뱃지
```

Variants: `Source=Manual`, `Source=Favorite`, `State=Default`, `State=Dragging`, `State=Placed`

### 2-5. CityGroup (도시 그룹 헤더)

```
padding: 8px 0 4px
display: flex · align-items center · gap 6px
도시명: label/bold · color neutral/text-primary
장소 수: caption · neutral/gray-muted · (N개)
접기/펼치기: ti-chevron-down (12px) · rotate 180deg when collapsed
하단: border-bottom 0.5px neutral/gray-border · margin-bottom 6px
```

### 2-6. PlaceBlock (타임라인 확정 블록)

```
border-radius: 6px · padding: 7px 10px · border: 0.5px solid [색상 100] · bg: [색상 50]
cursor: grab (재배치 가능)
제목: label/bold
메타: caption · display flex · gap 4px — ti-clock + 시간 범위
삭제 버튼: ti-x (11px) · float right · hover visible
```

카테고리별 색상 매핑:

| 카테고리 | Border | Background |
|---|---|---|
| 관광지 | `primary/purple-100` | `primary/purple-50` |
| 음식점 | `semantic/pink-100` | `semantic/pink-50` |
| 숙박 | `semantic/blue-100` | `semantic/blue-50` |
| 문화시설 | `semantic/teal-100` | `semantic/teal-50` |
| 레포츠 | `semantic/amber-100` | `semantic/amber-50` |

### 2-7. TransitPill

```
padding: 2px 8px · radius/pill · bg neutral/bg-secondary · border 0.5px neutral/gray-border
caption · color neutral/gray-muted · 아이콘 11px (ti-bus / ti-subway / ti-walk)
```

### 2-8. PostCard

```
border: 0.5px neutral/gray-border · radius/xl · padding 12px
hover: border neutral/gray-dark
구성: Avatar + 작성자 + 날짜 / 제목 / MiniBlock 미리보기 / 통계 행
```

---

## 3. 화면 명세

---

### 화면 M — 일정 생성 모달 (F05)

GNB "내 일정" 탭 최초 진입 시 또는 "+ 새 일정 만들기" 클릭 시 표시.

**레이아웃**: 화면 중앙 오버레이 모달

```
배경 오버레이: rgba(0,0,0,0.35)
모달 카드:
  width: 400px
  background: neutral/bg-surface
  border: 0.5px neutral/gray-border
  border-radius: radius/lg
  padding: 28px
```

| 요소 | 규격 |
|---|---|
| 제목 | heading/section "새 여행 일정 만들기" |
| 닫기 버튼 | ti-x (16px) · 우상단 · color muted |
| 필드 — 여행 제목 | label/default "여행 이름" · 입력 필드 (placeholder: "ex. 제주 여름 여행") |
| 필드 — 날짜 범위 | label/default "여행 기간" · 날짜 피커 2개 (출발일 ~ 귀환일) · 사이에 "—" 구분 |
| 날짜 피커 스타일 | padding 8px 10px · border 0.5px · radius/md · bg neutral/bg-secondary · body/small |
| 자동 계산 텍스트 | caption · neutral/gray-muted "총 N박 N일" (날짜 선택 시 동적 표시) |
| 필드 — 인원 (선택) | label/default "여행 인원" · 숫자 입력 또는 +/- 버튼 |
| 취소 버튼 | ghost · width 50% |
| 만들기 버튼 | primary · width 50% |
| 버튼 행 | display flex · gap 8px · margin-top 20px |

**입력 유효성**:
- 여행 제목: 필수, 최대 30자
- 날짜: 필수, 출발일 ≤ 귀환일
- 만들기 버튼: 필수 항목 미입력 시 disabled 상태

---

### 화면 A — 관광지 탐색 (F01 · F02 · F03 · F04 · F06)

**레이아웃**: 3단 분할 (필터 사이드바 188px + 관광지 목록 300px + 지도 flex)

#### A-1. 필터 사이드바 (188px)

| 요소 | 규격 |
|---|---|
| 컨테이너 | padding 14px · border-right 0.5px |
| 섹션 레이블 | caption/upper · uppercase · letter-spacing 0.5px · color neutral/gray-muted · mb 8px |
| 지역 필터 | FilterChip 다중 선택 (시도 단위) |
| 카테고리 필터 | FilterChip 다중 선택 |
| 즐겨찾기 섹션 | caption/upper "즐겨찾기" + ti-star (amber) |
| 즐겨찾기 아이템 | ti-star-filled (amber 13px) + label/default · gap 6px · 클릭 시 해당 장소 상세 이동 |

#### A-2. 관광지 목록 (300px)

| 요소 | 규격 |
|---|---|
| 검색바 | height 34px · bg neutral/bg-secondary · border 0.5px · radius/lg · ti-search 14px |
| 결과 수 | caption · neutral/gray-muted · mb 8px |
| 카드 그리드 | 2열 · gap 8px |

**AttractionCard 상태별 표시**:
```
기본: 카드 하단에 "일정에 추가 +" 버튼 hover 시 노출
후보군 등록됨(Candidate): 우상단 초록 체크 뱃지 + 버튼 "추가됨" (비활성, teal 색상)
선택됨(Selected): 카드 테두리 primary/purple-900 강조
```

**"일정에 추가" 버튼 동작**:
1. 활성 일정이 없으면 → 일정 선택 드롭다운 또는 "일정 만들기" 유도
2. 활성 일정이 있으면 → 즉시 후보군 등록, 카드 상태 Candidate로 전환
3. 해당 도시 최초 추가 시 → 즐겨찾기 동일 도시 장소 자동 추가 토스트 표시

**자동 추가 토스트 (즐겨찾기 연동)**:
```
위치: 화면 하단 중앙
bg: neutral/text-primary · color white · border-radius radius/lg · padding 10px 16px
텍스트: "[도시명]의 즐겨찾기 N개가 후보군에 추가됐어요"
아이콘: ti-star-filled (amber) 좌측
지속 시간: 3초 후 자동 사라짐
```

#### A-3. 지도 영역 (flex: 1)

```
Kakao Maps 연동 영역
마커: 20×20px · radius 50% 50% 50% 0 · rotate -45deg · 카테고리 색상
  후보군 등록된 마커: 외곽 흰색 2px 링 추가
줌 컨트롤: 28×28px · 우하단 (bottom 12, right 12)
지역 레이블: bg surface · border 0.5px · radius/md · padding 5px 14px · caption · 상단 중앙
```

---

### 화면 B — 내 일정 (F06 · F07 · F08 · F09)

**레이아웃**: 2단 분할 (후보군 사이드바 220px + 타임라인 메인 flex)

#### B-1. 후보군 사이드바 (220px)

```
border-right: 0.5px neutral/gray-border
padding: 12px
overflow-y: scroll
background: neutral/bg-surface
```

**상단 일정 선택 행**:
```
display flex · align-items center · gap 6px · mb 12px · pb 10px · border-bottom 0.5px
일정명: label/bold · flex 1 · color primary/purple-900
변경 버튼: caption · neutral/gray-muted · ti-chevron-down 12px
```

**CityGroup + CandidateCard 구조**:
```
도시 그룹 1 (예: 서울 · 3개)
  ├─ CandidateCard — 경복궁  [즐겨찾기 아이콘]
  ├─ CandidateCard — 광장시장
  └─ CandidateCard — 남산서울타워 [즐겨찾기 아이콘]

도시 그룹 2 (예: 경주 · 4개)
  ├─ CandidateCard — 불국사
  ├─ CandidateCard — 석굴암
  ├─ CandidateCard — 첨성대
  └─ CandidateCard — 동궁과월지

[+ 관광지 탐색으로 추가하기] 버튼 (최하단)
  border: 0.5px dashed neutral/gray-border · radius/md · caption · text-align center · padding 8px
```

**CandidateCard 상태**:
```
기본: 드래그 가능 (cursor grab)
Placed(배치됨): 텍스트 취소선 · opacity 0.4 · 드래그 불가 (이미 일정에 배치된 경우)
Favorite(자동추가): 우상단 ti-star-filled amber 11px 뱃지
```

#### B-2. 날짜 탭 바

```
height: 40px · padding 10px 14px · border-bottom 0.5px
Day 탭: 기본 border 0.5px · 활성 bg primary/purple-900 · color white · radius/md
저장 버튼: margin-left auto · caption · ti-download 13px
```

#### B-3. 타임라인 영역

```
padding: 10px 14px · overflow-y scroll · gap 4px
```

**TimelineRow 구조**:
```
display: flex · align-items flex-start · gap 8px

  시간 레이블: caption · neutral/gray-muted · width 36px · padding-top 6px
  커넥터:
    dot (8×8px, radius/full, border 1.5px [카테고리 색상], bg white, mt 6px)
    line (1px, bg neutral/gray-border, flex 1, mt 2px)
  PlaceBlock: 2-6 컴포넌트 참조
```

**TransitRow**:
```
margin: 0 0 4px 60px
display: flex · gap 8px
TransitPill 1~2개 (자동 삽입)
```

**DropZone**:
```
기본: border 0.5px dashed neutral/gray-border · radius/lg · padding 16px · caption · muted · text-align center
드래그 오버 시: border 1.5px dashed primary/purple-900 · bg primary/purple-50 · color primary/purple-900
```

**드래그 앤 드롭 동작 정의**:
```
1. CandidateCard를 잡아 타임라인 DropZone으로 드래그
2. 시간 슬롯 위에 올리면 DropZone 활성 상태로 전환
3. 놓으면 PlaceBlock 생성, 해당 CandidateCard는 Placed 상태로 전환
4. ODsay API 호출 → 이전·다음 PlaceBlock과의 TransitPill 자동 삽입
5. PlaceBlock을 다른 슬롯으로 드래그하면 TransitPill 재계산
6. PlaceBlock의 ti-x 클릭 시 삭제, 해당 CandidateCard Placed 해제
```

---

### 화면 C — 커뮤니티 (F10 · F11)

**레이아웃**: 2단 분할 (메인 flex + 우측 사이드바 200px)

#### C-1. 메인 게시판

| 요소 | 규격 |
|---|---|
| 헤더 행 | display flex · space-between · mb 12px |
| 페이지 제목 | heading/section "여행 일정 공유게시판" |
| 일정 공유 버튼 | btn-primary · ti-edit 13px |
| PostCard 목록 | gap 8px |

**PostCard 내부 구성**:
```
상단: Avatar(28px) + 작성자명 + 소개 + 날짜(ml-auto)
제목: heading/section · mb 6px
MiniBlock 미리보기: display flex · gap 6px · overflow hidden
  MiniBlock: padding 3px 8px · radius/sm · border 0.5px · caption · 카테고리 색상
  초과 수: "+N" · caption · muted
통계: ti-heart + ti-message + ti-eye · caption · muted · gap 12px
```

#### C-2. 우측 사이드바 (200px)

```
border-left: 0.5px · padding 12px

섹션 — 공지사항
  label/bold + ti-bell
  NoticeItem: badge-notice(amber) + label/default + border-bottom 0.5px

섹션 — 인기 여행지
  FilterChip wrap

섹션 — 이번 주 인기
  순위(caption/bold, purple) + 일정명(caption, muted)
```

---

### 화면 D — 로그인 / 회원가입 (F12)

**레이아웃**: 화면 중앙 정렬 · bg neutral/bg-secondary

**인증 카드 (340px)**:
```
bg: neutral/bg-surface · border 0.5px (강조) · radius/xl · padding 28px
```

| 영역 | 규격 |
|---|---|
| 로고 | 20px/500 · primary/purple-900 · text-align center |
| 슬로건 | caption · muted · mt 2px |
| 탭 스위처 | border 0.5px · radius/lg · 활성 탭 bg primary/purple-900 · white |
| 필드 레이블 | caption · muted · mb 4px · mt 12px |
| 입력 필드 | caption · padding 8px 10px · border 0.5px · radius/md · bg secondary · focus: border primary/purple-900 |
| 제출 버튼 | width 100% · bg primary/purple-900 · white · padding 9px · radius/lg · body/small 500 |
| 구분선 | caption · muted · ::before ::after height 0.5px |
| 카카오 버튼 | width 100% · border 0.5px · bg secondary · body/small |
| 하단 전환 링크 | caption · muted · 링크 primary/purple-900 |

**회원가입 추가 필드**: 이름 · 이메일 · 비밀번호 · 비밀번호 확인

---

## 4. 인터랙션 및 상태 전환

### 4-1. 핵심 플로우 인터랙션

| 단계 | 트리거 | 결과 |
|---|---|---|
| 일정 생성 | "+ 새 일정 만들기" 클릭 | 모달 M 표시 |
| 일정 생성 완료 | 모달 "만들기" 클릭 | 모달 닫힘 · 활성 일정 설정 · 화면 B 이동 |
| 후보군 추가 | AttractionCard "일정에 추가" | 카드 → Candidate 상태 · 후보군 사이드바에 CandidateCard 추가 |
| 도시 최초 추가 | 후보군에 새 도시 첫 등록 | 즐겨찾기 동일 도시 장소 자동 추가 + 토스트 표시 |
| 일정 배치 | CandidateCard → DropZone 드롭 | PlaceBlock 생성 · TransitPill 자동 삽입 · CandidateCard → Placed |
| 블록 재배치 | PlaceBlock 다른 슬롯으로 드래그 | TransitPill 재계산 |
| 블록 제거 | PlaceBlock ti-x 클릭 | PlaceBlock 삭제 · CandidateCard Placed 해제 · TransitPill 재계산 |
| 즐겨찾기 토글 | 장소 ti-star 클릭 | 별 아이콘 토글 · 즐겨찾기 사이드바 갱신 |

### 4-2. 컴포넌트 상태 매트릭스

| 컴포넌트 | 상태 목록 |
|---|---|
| `FilterChip` | Default · Selected |
| `AttractionCard` | Default · Hover · Selected · Candidate |
| `CandidateCard` | Default · Dragging · Placed · Favorite |
| `PlaceBlock` | Default · Dragging · Hover |
| `DropZone` | Idle · DragOver · Filled |
| `DayTab` | Default · Active |
| `AuthTab` | Default · Active |

---

## 5. Figma 파일 구조 권장

```
TripCraft/
├── 🎨 Design Tokens
│   ├── Colors
│   ├── Typography
│   └── Spacing & Radius
│
├── 🧩 Components
│   ├── GNB
│   ├── FilterChip            [Variants: State]
│   ├── AttractionCard        [Variants: State × Category]
│   ├── CandidateCard         [Variants: State × Source]
│   ├── CityGroup             [Variants: Collapsed/Expanded]
│   ├── PlaceBlock            [Variants: State × Category]
│   ├── TransitPill           [Variants: Mode(Bus/Subway/Walk)]
│   ├── DropZone              [Variants: State]
│   ├── PostCard
│   ├── MiniBlock             [Variants: Category]
│   ├── NoticeItem
│   └── FormField / AuthCard
│
└── 📱 Screens
    ├── M — 일정 생성 모달
    ├── A — 관광지 탐색
    │   ├── A-1 기본 상태
    │   ├── A-2 후보군 추가 직후 (토스트)
    │   └── A-3 도시 자동 추가 토스트
    ├── B — 내 일정
    │   ├── B-1 빈 상태 (후보군 없음)
    │   ├── B-2 후보군 있음 / 미배치
    │   └── B-3 일정 확정 후
    ├── C — 커뮤니티
    └── D — 로그인·회원가입
```

> 컴포넌트는 **Auto Layout + Variants + Interactive Components** 로 제작 권장.  
> 드래그 동작은 Figma Prototype의 **Drag** 인터랙션으로 시연 가능.

---

*본 문서는 기획서 v0.2 기준 초안이며, 설계·개발 과정에서 지속 업데이트됩니다.*
