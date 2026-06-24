// 협업 커서·ghost 좌표 송수신 composable.
//
// 절대 픽셀이 아니라 "zone(영역) + 의미 좌표"로 주고받아, 창 크기·세로 스크롤·
// 지도 패널 폭이 달라도 모두가 "같은 의미의 지점"을 가리키게 한다.
//
//  - timetable: dayIndex(어느 day 컬럼) + colRatioX(컬럼 내 가로 비율) + contentY(시간축 콘텐츠 px)
//  - map      : mapRatioX/mapRatioY(컨테이너 내 비율) — 추후 lat/lng 정밀 변환으로 대체 예정
//  - grab     : grabRatioX(블록 폭 대비 잡은 가로)/grabOffsetMin(블록 top으로부터 분)
//
// 세로축은 '.day-cols'(시간 원점, 0~1440px 고정)를, 가로축은 개별 '.day-col'을 기준으로 삼는다.
// 두 컨테이너의 getBoundingClientRect()는 스크롤을 이미 반영하므로 별도 scrollTop 보정이 필요 없다.

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v))

export function useCollabCursor({ wrapperEl, mapEl, timetableScrollTop, ghostBlockHeight, HOUR_PX = 60, SNAP = 30 }) {
  const PX_PER_MIN = HOUR_PX / 60
  const CONTENT_MAX = 24 * 60 * PX_PER_MIN  // 시간축 콘텐츠 전체 높이 (1440px)

  // ── DOM 조회 (수신/송신 공통) ──
  function dayCols() {
    return wrapperEl.value?.querySelector('.day-cols') ?? null
  }
  function dayColAt(index) {
    // wrapperEl 내부로 범위 한정 — 한 페이지에 보드가 둘 이상이어도 충돌하지 않게
    if (index == null || index < 0) return null
    return wrapperEl.value?.querySelectorAll('.day-col')[index] ?? null
  }
  function zoneOf(target) {
    if (mapEl.value && mapEl.value.contains(target)) return 'map'
    // 타임테이블 헤더(day 라벨)·시간축 거터·사이드바 등은 제외하고, 실제 격자(.day-cols)만 협업 영역으로 본다.
    const grid = dayCols()
    if (grid && grid.contains(target)) return 'timetable'
    return 'other'
  }

  // ── 송신: 이벤트 → 정규화 payload ──
  function buildPointerPayload(e, { interaction = '', dragState = null, nickname = '' } = {}) {
    const zone = zoneOf(e.target)
    const payload = {
      zone,
      interaction,
      nickname,
      targetBlockId: interaction === 'grab' ? (dragState?.data?.id ?? null) : null,
      dayIndex: -1,
      colRatioX: 0,
      contentY: 0,
      mapRatioX: 0,
      mapRatioY: 0,
      grabRatioX: dragState?.grabRatioX ?? 0,
      grabOffsetMin: dragState?.grabOffsetMin ?? 0,
    }

    if (zone === 'timetable') {
      const dc = dayCols()
      if (dc) {
        payload.contentY = clamp(e.clientY - dc.getBoundingClientRect().top, 0, CONTENT_MAX)
      }
      // 커서가 놓인 day 컬럼 탐색
      const cols = wrapperEl.value?.querySelectorAll('.day-col') ?? []
      for (let i = 0; i < cols.length; i++) {
        const r = cols[i].getBoundingClientRect()
        if (e.clientX >= r.left && e.clientX <= r.right) {
          payload.dayIndex = i
          payload.colRatioX = (e.clientX - r.left) / r.width
          break
        }
      }
      // 컬럼 밖(시간축·여백)이면 day-cols 영역 기준 비율로 fallback
      if (payload.dayIndex < 0 && dc) {
        const r = dc.getBoundingClientRect()
        payload.colRatioX = clamp((e.clientX - r.left) / r.width, 0, 1)
      }
    } else if (zone === 'map' && mapEl.value) {
      const r = mapEl.value.getBoundingClientRect()
      payload.mapRatioX = clamp((e.clientX - r.left) / r.width, 0, 1)
      payload.mapRatioY = clamp((e.clientY - r.top) / r.height, 0, 1)
    }

    return payload
  }

  // ── 수신: payload → 자기 DOM 기준 역변환 ──

  // 시간축 콘텐츠 좌표 → 수신측 뷰포트 y. day-cols rect.top이 스크롤을 이미 반영.
  // timetableScrollTop을 읽어 스크롤 시 오버레이가 재렌더되도록 한다(rect는 reactive가 아님).
  function contentToViewportY(contentY) {
    void timetableScrollTop?.value
    const dc = dayCols()
    if (!dc) return null
    return dc.getBoundingClientRect().top + contentY
  }

  // 일반 커서 위치
  function cursorStyle(p) {
    if (p.zone === 'map') {
      if (!mapEl.value) return { display: 'none' }
      const r = mapEl.value.getBoundingClientRect()
      return {
        left: (r.left + (p.mapRatioX ?? 0) * r.width) + 'px',
        top:  (r.top  + (p.mapRatioY ?? 0) * r.height) + 'px',
      }
    }
    if (p.zone === 'timetable') {
      const top = contentToViewportY(p.contentY ?? 0)
      if (top == null) return { display: 'none' }
      const col = dayColAt(p.dayIndex)
      let left
      if (col) {
        const r = col.getBoundingClientRect()
        left = r.left + (p.colRatioX ?? 0) * r.width
      } else {
        const dc = dayCols()
        if (!dc) return { display: 'none' }
        const r = dc.getBoundingClientRect()
        left = r.left + (p.colRatioX ?? 0) * r.width
      }
      return { left: left + 'px', top: top + 'px' }
    }
    // 'other' — 사이드바·헤더 등은 의미 좌표가 없어 표시하지 않음 (알려진 한계)
    return { display: 'none' }
  }

  // grab 중 ghost 블록 (timetable zone에서만 의미)
  function ghostStyle(p) {
    if (p.zone !== 'timetable') return { display: 'none' }
    const col = dayColAt(p.dayIndex)
    const width = col ? col.getBoundingClientRect().width - 10 : 160
    const ghostTopContent = (p.contentY ?? 0) - (p.grabOffsetMin ?? 0) * PX_PER_MIN
    const top = contentToViewportY(ghostTopContent)
    if (top == null) return { display: 'none' }
    let left
    if (col) {
      const r = col.getBoundingClientRect()
      const cursorX = r.left + (p.colRatioX ?? 0) * r.width
      left = cursorX - (p.grabRatioX ?? 0) * width
    } else {
      const dc = dayCols()
      if (!dc) return { display: 'none' }
      const r = dc.getBoundingClientRect()
      left = r.left + (p.colRatioX ?? 0) * r.width - (p.grabRatioX ?? 0) * width
    }
    return {
      left: left + 'px',
      top: top + 'px',
      width: width + 'px',
      height: ghostBlockHeight(p.targetBlockId) + 'px',
    }
  }

  // 스냅된 drop 미리보기 (timetable zone grab에서만)
  function dropPreviewStyle(p) {
    if (p.zone !== 'timetable' || p.dayIndex < 0) return { display: 'none' }
    const col = dayColAt(p.dayIndex)
    if (!col) return { display: 'none' }
    const r = col.getBoundingClientRect()
    const ghostTopContent = Math.max(0, (p.contentY ?? 0) - (p.grabOffsetMin ?? 0) * PX_PER_MIN)
    const snapTopContent = Math.round(ghostTopContent / SNAP) * SNAP
    const top = contentToViewportY(snapTopContent)
    if (top == null) return { display: 'none' }
    return {
      left:  (r.left + 5) + 'px',
      top:   top + 'px',
      width: (r.width - 10) + 'px',
      height: ghostBlockHeight(p.targetBlockId) + 'px',
    }
  }

  return {
    zoneOf,
    dayColAt,
    buildPointerPayload,
    cursorStyle,
    ghostStyle,
    dropPreviewStyle,
  }
}
