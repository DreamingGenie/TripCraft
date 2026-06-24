# feature/collab — 협업 커서 좌표 동기화 재설계 (해상도·스크롤 독립)

## Context

`feature/collab`은 실시간 협업 편집을 구현 중이다. 협업자의 마우스 커서, 집은 블록의
실루엣(ghost), 이동/드롭 결과를 모든 참여자 화면에 실시간 표시한다. 대부분 동작하지만
**좌표 동기화가 절대 픽셀 기반**이라 근본 결함이 있다: 사용자마다 모니터·브라우저 창 크기가
다르고, 같은 시간표라도 세로 스크롤 위치(예: A는 1~8시, B는 9~15시 표시)가 다르다.
절대좌표를 그대로 공유하면 "5시 성심당 블록을 가리킨다"가 다른 화면에선 엉뚱한 위치에 찍힌다.

목표: 커서/ghost를 **절대 픽셀이 아니라 "영역(zone)별 의미 좌표"로 송수신**하여, 창 크기·
스크롤·지도 패널 폭이 달라도 모두가 "같은 의미의 지점"을 가리키게 한다. 동시에 좌표 로직을
composable로 분리해 2,273줄 God Component(`ScheduleBoard.vue`)의 부담을 일부 던다.

내일 작업은 **이 좌표계 재설계 + 오류 해결 + 리팩토링**으로 한정한다(신규 기능 없음).

### 사용자 확정 사항
- 지도 커서: **비율 근사(컨테이너 0~1)** 우선. lat/lng 정밀 변환은 추후 개선 목표(`docs/01_planning/improvements.md`에 등재).
- 정확도 목표: **의미 단위 정확**(같은 day·시간·블록을 가리키면 OK). 픽셀 단위 완벽 일치는 비목표.
- 구조: 좌표 변환을 **composable로 추출**.
- 후보군 영역 + 시간표 영역은 하나의 좌표 관리 단위로 취급 가능.

---

## 현재 구조 (탐색 결과)

전송측 `ScheduleBoard.vue`:
- `onPointerMove` (1932): 일반 커서 — **순수 뷰포트 `x,y`만 전송** ← 가장 큰 결함
- `onDragOver` (1774): grab 중 — `x,y`(절대) + `snapDayIndex` + `snapTop`(컬럼 내 px) + `cursorRelY`(=scrollTop+clientY-wrapperTop, 콘텐츠 좌표) + `grabOffsetX/Y` 전송
- `onEventDragStart` (1695): `grabOffsetX=e.offsetX`, `grabOffsetY=clientY-blockTop`

수신측 `ScheduleBoard.vue` (Teleport to body, 503-537):
- 일반 커서: `left:p.x, top:p.y` (절대) — 창/스크롤 다르면 어긋남
- ghost: `left:p.x-grabOffsetX`(절대 가로), `top:collabGhostTop(p)`
- `collabGhostTop` (1976): `snapDayIndex>=0`이면 `wrapperTop - receiverScroll + cursorRelY - grabOffsetY` (세로는 콘텐츠 좌표로 이미 보정됨 ✓)
- `collabGhostWidth` (1990): day-col 실제 폭 사용 ✓
- `collabDropPreviewStyle` (1999): `rect.top + snapTop` (스크롤 자동 보정 ✓)
- `dayColAt` (1985): `wrapperEl` 범위로 한정 ✓

백엔드 `TripPresenceController.java`:
- `PresenceState` record(45): x,y,snapDayIndex,snapTop,cursorRelY,grabOffsetX/Y 명시 필드
- `handlePointer`(46): payload→record, `broadcastPresence`로 통과(passthrough). 좌표는 가공 안 함.

DOM: `wrapperEl`(스크롤 컨테이너) > `timetable-body` > `time-axis` + `day-cols` > `day-col`(시간 0~24시 = 1440px 고정, 60px/시간). 지도: `mapEl`(`naverMapInstance`), 패널 폭 가변/닫힘 가능.

**진단**: 세로축(시간)은 콘텐츠 높이가 모두 동일(1440px)이라 `cursorRelY` 방식이 옳다. 결함은
(a) 일반 커서가 이 보정을 전혀 안 함, (b) 가로축이 절대 픽셀(창 폭 의존), (c) 지도 영역 미보정.

---

## 좌표 모델 (핵심 설계)

커서를 **"어느 zone + 그 zone의 의미 좌표"**로 표현. 전송측이 정규화, 수신측이 자기 DOM 기준 역변환.

```
payload = {
  zone: 'timetable' | 'map' | 'other',
  interaction: '' | 'grab',
  nickname, targetBlockId,

  // zone === 'timetable'
  dayIndex,     // 커서가 놓인 day 컬럼 인덱스 (컬럼 밖이면 -1)
  colRatioX,    // 0~1: day 컬럼 내 가로 비율 (컬럼 밖이면 day-cols 영역 기준 비율)
  contentY,     // 시간축 콘텐츠 좌표(px) = scrollTop + (clientY - wrapperTop). 0~1440, 모두 동일

  // zone === 'map'
  mapRatioX, mapRatioY,   // 0~1: 지도 컨테이너 내 비율 (추후 lat/lng로 대체 예정)

  // grab 보정 (블록 기준 정규화 → 폭/높이 달라도 안전)
  grabRatioX,   // 0~1: 블록 폭 대비 잡은 가로 위치
  grabOffsetMin // 분 단위: 블록 top으로부터 잡은 세로 거리 (= grabOffsetY를 px→분)
}
```

핵심 불변식:
- **세로(시간)**: `contentY`는 콘텐츠 좌표라 전 참여자 동일. 수신측 `viewportY = wrapperTop - myScrollTop + contentY`.
- **가로(day)**: `dayIndex` + `colRatioX`로 표현 → 수신측 `left = col.rect.left + colRatioX*col.rect.width`. 창 폭 무관.
- **지도**: 컨테이너 비율 → `left = mapRect.left + mapRatioX*mapRect.width`. (줌/팬 다르면 어긋남 — 추후 lat/lng로 해결, 의도된 한계)

---

## 변경 계획

### 1. 신규 composable `frontend/src/composables/useCollabCursor.js`
좌표 송수신 로직을 한 곳에 모은다. ScheduleBoard에서 ref들을 주입받아 사용.

- 입력: `wrapperEl`, `mapEl`, `timetableScrollTop`, `days`, 상수(`PX_PER_MIN`, `SNAP`)
- `zoneOf(e)` — `mapEl.contains(target)` → 'map', `wrapperEl.contains(target)` → 'timetable', else 'other'
- `buildPointerPayload(e, { interaction, dragState })` — zone 판정 + 위 모델로 정규화. `onPointerMove`/`onDragOver`가 호출
- `cursorStyle(p)` — 일반 커서 left/top (zone별 역변환)
- `ghostStyle(p)` — grab 블록 left(=col.left + colRatioX*width 보정)/top/width/height
- `dropPreviewStyle(p)` — 스냅 미리보기
- `dayColAt(index)` 이동(현재 ScheduleBoard 함수 재사용)

### 2. 전송측 정리 (`ScheduleBoard.vue`)
- `onPointerMove`(1932): `collab.sendPointer(tripId, buildPointerPayload(e, { interaction:'' }))`로 교체 → **일반 커서도 zone/정규화 좌표 전송**(핵심 버그 수정)
- `onDragOver`(1774): 동일하게 `buildPointerPayload(e, { interaction:'grab', dragState })` 사용. `x,y,cursorRelY,snapTop` 수동 계산 제거
- `onEventDragStart`(1695)·`onCandDragStart`(1688): grabOffset을 `grabRatioX`,`grabOffsetMin`으로 환산 저장

### 3. 수신측 정리 (`ScheduleBoard.vue`)
- 템플릿(503-537): `:style` 바인딩을 `cursorStyle(p)`/`ghostStyle(p)`/`dropPreviewStyle(p)` 호출로 교체
- 기존 `collabGhostTop`/`collabGhostWidth`/`collabDropPreviewStyle`/`grabberColor` 등은 composable로 이전 또는 위임
- 일반 커서 표시 조건에 `zone` 반영(지도 커서는 `mapEl` 존재·패널 열림 시에만)

### 4. 백엔드 정합 (`TripPresenceController.java`)
- `PresenceState` record 필드를 새 모델로 교체(`zone, dayIndex, colRatioX, contentY, mapRatioX, mapRatioY, grabRatioX, grabOffsetMin`)
- `handlePointer`(46): payload 파싱 필드 교체. `broadcastPresence`(137) 출력 맵도 동일 키로
- grab 잠금 판정(`interaction=='grab' && targetBlockId`)은 유지

### 5. 오류 해결 · 엣지 케이스
- 컬럼 밖(시간축/여백) 커서: `dayIndex=-1`, `colRatioX`는 day-cols 영역 기준 → 수신측 fallback 처리
- 스크롤 경계(콘텐츠 0/1440 클램프), 패널 닫힘 중 map zone 수신 시 무시
- keepalive 빈 payload(`collab.js` startKeepalive)도 새 스키마에 맞춰 필드 갱신
- (병행) `collab.js disconnect()` 미선언 `activeTripId` 참조 — 이미 MR #12에서 수정됨, 머지 후 충돌 없는지 확인

---

## Critical Files
- `frontend/src/composables/useCollabCursor.js` (신규)
- `frontend/src/components/ScheduleBoard.vue` (전송/수신/템플릿)
- `frontend/src/stores/collab.js` (keepalive payload 스키마)
- `backend/src/main/java/com/tripcraft/plan/controller/TripPresenceController.java` (PresenceState/handlePointer/broadcastPresence)

## 재사용할 기존 자산
- `dayColAt`(ScheduleBoard:1985) — wrapper 범위 한정 day-col 조회
- `timetableScrollTop` ref(scroll 핸들러에서 갱신) — 콘텐츠↔뷰포트 변환의 기준
- `cursorRelY` 개념 — 세로 콘텐츠 좌표(이미 검증된 보정식). `contentY`로 일반화
- `collab.js`의 `sendPointer`/presence 구독·`grabMap`·`assignColors`(좌표와 무관, 변경 없음)

---

## Verification (end-to-end)
1. 백엔드 실행 후, **두 브라우저 창을 의도적으로 다른 크기**로 띄우고 같은 trip의 `/plan`(또는 `/schedule`)에 다른 계정으로 동시 접속.
2. **세로 스크롤 독립 검증**: A는 1~8시, B는 9~15시가 보이도록 각자 스크롤. A가 마우스를 "5시 블록"에 올리면 B 화면에서도 동일 블록 위에 A의 커서가 찍히는지 확인(스크롤 밖이면 안 보이거나 경계 처리).
3. **가로(창 폭) 독립 검증**: 두 창의 폭을 크게 다르게. A가 특정 day 컬럼 블록을 가리키면 B의 (폭이 다른) 같은 day 컬럼 같은 블록에 찍히는지.
4. **드래그 ghost**: A가 블록을 집어 다른 시간으로 끌 때, B 화면에서 ghost·drop-preview가 같은 의미 위치를 따라가는지. 잡은 지점(grab offset)이 블록 비율로 유지되는지.
5. **지도 비율 근사**: 지도 패널을 같은 폭으로 연 두 창에서 A가 지도 위를 가리킬 때 B의 같은 비율 위치에 찍히는지(줌/팬 동일 가정). 폭이 다르거나 줌이 다르면 어긋남은 알려진 한계로 기록.
6. 회귀: 본인 드래그/드롭/리사이즈, transit pill, 지도 경로 렌더링이 그대로 동작하는지.

## 범위 밖 (추후)
- 지도 lat/lng 정밀 동기화(`map.getProjection().fromOffsetToCoord`/`fromCoordToOffset`) → `improvements.md` 등재
- ScheduleBoard 전체 분해(④지도·③Pill composable화)는 별건
