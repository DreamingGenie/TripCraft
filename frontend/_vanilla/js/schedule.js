/* ============================================================
   TripCraft — schedule.js  (v2)
   내 일정 페이지 — 시간표 그리드 인터랙션
   - 시간 축 자동 생성 (0:00 ~ 23:30)
   - 후보군 카드 드래그 → 날짜 컬럼 드롭 (30분 스냅)
   - 이벤트 블록 삭제
   - 초기 스크롤: 오전 8시 위치
   ============================================================ */

const HOUR_PX     = 60;          // 1시간 = 60px
const SNAP_MIN    = 30;          // 30분 단위 스냅
const DEFAULT_DUR = 120;         // 기본 이벤트 길이: 2시간 (분)

/* 카테고리 → 색상 매핑 */
const CAT_COLOR = {
  '관광지': 'purple',
  '문화시설': 'teal',
  '음식점': 'pink',
  '숙박': 'blue',
  '레포츠': 'amber',
};

/* ================================================================
   시간 축 생성 (0:00 ~ 23:30, 30분 단위)
   ================================================================ */
function buildTimeAxis() {
  const axis = document.getElementById('time-axis');
  if (!axis) return;

  for (let h = 0; h < 24; h++) {
    // 정각 레이블
    const mark = document.createElement('div');
    mark.className = 'time-mark';
    mark.style.top = (h * HOUR_PX) + 'px';
    mark.textContent = String(h).padStart(2, '0') + ':00';
    axis.appendChild(mark);

    // 30분 레이블 (연하게)
    const halfMark = document.createElement('div');
    halfMark.className = 'time-mark half';
    halfMark.style.top = (h * HOUR_PX + 30) + 'px';
    halfMark.textContent = String(h).padStart(2, '0') + ':30';
    axis.appendChild(halfMark);
  }
}

/* ================================================================
   드래그 앤 드롭
   ================================================================ */
let _dragData = null;  // { name, cat, color }

function initDragDrop() {
  // 드래그 가능한 후보군 카드
  document.querySelectorAll('.cand-card[draggable="true"]').forEach(card => {
    card.addEventListener('dragstart', e => {
      _dragData = {
        name:  card.dataset.name  || card.querySelector('.cand-name')?.textContent || '장소',
        cat:   card.dataset.cat   || '',
        color: card.dataset.color || 'purple',
        bar:   card.dataset.bar   || '#534AB7',
        card,
      };
      e.dataTransfer.effectAllowed = 'move';
      setTimeout(() => card.classList.add('dragging'), 0);
    });

    card.addEventListener('dragend', () => {
      card.classList.remove('dragging');
      clearDropPreviews();
    });
  });

  // 날짜 컬럼 드롭존
  document.querySelectorAll('.day-col').forEach(col => {
    col.addEventListener('dragover', e => {
      e.preventDefault();
      if (!_dragData) return;
      col.classList.add('drag-over');

      // 드롭 미리보기 위치 계산
      const rect    = col.getBoundingClientRect();
      const relY    = e.clientY - rect.top + col.closest('.timetable-wrapper').scrollTop
                      - col.closest('.timetable-body').offsetTop;
      const snapped = snapToGrid(relY);

      showDropPreview(col, snapped);
    });

    col.addEventListener('dragleave', e => {
      if (!col.contains(e.relatedTarget)) {
        col.classList.remove('drag-over');
        clearDropPreviews(col);
      }
    });

    col.addEventListener('drop', e => {
      e.preventDefault();
      col.classList.remove('drag-over');
      if (!_dragData) return;

      const rect  = col.getBoundingClientRect();
      const wrapper = col.closest('.timetable-wrapper');
      const body    = col.closest('.timetable-body');
      // 스크롤 위치 반영
      const relY  = e.clientY - rect.top + wrapper.scrollTop - body.offsetTop;
      const snapped = snapToGrid(relY);

      clearDropPreviews(col);
      placeEvent(col, snapped, _dragData);
      markCandAsPlaced(_dragData.card);
      showToast('"' + _dragData.name + '" 일정에 추가됐어요');
      _dragData = null;
    });
  });
}

/* ── 30분 단위 스냅 ── */
function snapToGrid(px) {
  const snapPx = SNAP_MIN * (HOUR_PX / 60);  // 30px
  return Math.round(px / snapPx) * snapPx;
}

/* ── 드롭 미리보기 ── */
function showDropPreview(col, topPx) {
  clearDropPreviews(col);
  const preview = document.createElement('div');
  preview.className = 'drop-preview';
  preview.style.top    = topPx + 'px';
  preview.style.height = DEFAULT_DUR + 'px';
  preview.textContent  = _dragData ? _dragData.name : '';
  col.appendChild(preview);
}
function clearDropPreviews(col) {
  const targets = col ? [col] : document.querySelectorAll('.day-col');
  targets.forEach(c => c.querySelectorAll('.drop-preview').forEach(p => p.remove()));
}

/* ── 이벤트 블록 생성 ── */
function placeEvent(col, topPx, data) {
  const block = document.createElement('div');
  block.className = 'event-block';
  block.dataset.color = data.color;
  block.style.top    = topPx + 'px';
  block.style.height = DEFAULT_DUR + 'px';  // 기본 2시간

  const startMin = topPx;           // px ≈ minutes (1px = 1min at 60px/hr)
  const endMin   = startMin + DEFAULT_DUR;

  block.innerHTML = `
    <span class="event-name">${data.name}</span>
    <span class="event-time">${minsToTime(startMin)} – ${minsToTime(endMin)}</span>
    <button class="event-del" onclick="removeEvent(this)">✕</button>
  `;
  col.appendChild(block);
}

/* ── 후보군 카드 배치 완료 처리 ── */
function markCandAsPlaced(card) {
  if (!card) return;
  card.classList.add('placed');
  card.removeAttribute('draggable');
  const nameEl = card.querySelector('.cand-name');
  if (nameEl) nameEl.classList.add('placed');
}

/* ── 이벤트 삭제 ── */
function removeEvent(btn) {
  const block = btn.closest('.event-block');
  if (block) {
    block.remove();
    showToast('장소가 삭제됐어요');
  }
}

/* ── 픽셀 → 시간 문자열 (60px = 1시간) ── */
function minsToTime(px) {
  const totalMin = Math.round(px);     // 1px = 1min
  const h = Math.floor(totalMin / 60) % 24;
  const m = totalMin % 60;
  return String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0');
}

/* ================================================================
   초기화
   ================================================================ */
document.addEventListener('DOMContentLoaded', () => {
  buildTimeAxis();
  initDragDrop();

  // 오전 8시 위치로 초기 스크롤
  const wrapper = document.getElementById('timetable-wrapper');
  if (wrapper) wrapper.scrollTop = 8 * HOUR_PX;  // 480px
});