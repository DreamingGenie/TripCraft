/* ============================================================
   TripCraft — explore.js
   관광지 탐색 페이지 인터랙션
   - 필터 패널 토글 (지역/카테고리)
   - 일정 카드 선택 (어떤 일정에 추가할지)
   - 관광지 → 후보군 추가
   ============================================================ */

/* ── 현재 선택된 일정 ID ── */
let activeScheduleId = 'schedule-1';

/* ── 필터 상태 ── */
const filterState = {
  regions: new Set(),
  categories: new Set(),
  panelOpen: false,
};

/* ================================================================
   필터 패널
   ================================================================ */
function toggleFilterPanel() {
  const panel = document.getElementById('filter-panel');
  const btnRegion = document.getElementById('btn-filter-region');
  const btnCat = document.getElementById('btn-filter-cat');

  filterState.panelOpen = !filterState.panelOpen;

  if (filterState.panelOpen) {
    panel.classList.add('open');
    btnRegion.classList.add('open');
    btnCat.classList.add('open');
  } else {
    panel.classList.remove('open');
    btnRegion.classList.remove('open');
    btnCat.classList.remove('open');
  }
}

function applyFilter() {
  // 선택된 칩 수집
  filterState.regions.clear();
  filterState.categories.clear();

  document.querySelectorAll('#filter-region-chips .chip.sel').forEach(c => {
    filterState.regions.add(c.dataset.value);
  });
  document.querySelectorAll('#filter-cat-chips .chip.sel').forEach(c => {
    filterState.categories.add(c.dataset.value);
  });

  // 필터 버튼 배지 업데이트
  updateFilterBadge('btn-filter-region', filterState.regions.size, '지역');
  updateFilterBadge('btn-filter-cat', filterState.categories.size, '카테고리');

  // 초기화 버튼 표시
  const clearBtn = document.getElementById('btn-filter-clear');
  const hasAny = filterState.regions.size > 0 || filterState.categories.size > 0;
  clearBtn.style.display = hasAny ? '' : 'none';

  // 패널 닫기
  toggleFilterPanel();

  // 결과 수 업데이트 (실제로는 API 호출)
  updateResultCount();

  showToast('필터가 적용됐어요');
}

function updateFilterBadge(btnId, count, label) {
  const btn = document.getElementById(btnId);
  if (!btn) return;
  if (count > 0) {
    btn.classList.add('has-selection');
    btn.innerHTML = label + ' <span class="filter-count-badge">' + count + '</span> <span class="filter-arrow">▾</span>';
  } else {
    btn.classList.remove('has-selection');
    btn.innerHTML = label + ' <span class="filter-arrow">▾</span>';
  }
}

function clearFilters() {
  // 모든 칩 선택 해제
  document.querySelectorAll('#filter-region-chips .chip, #filter-cat-chips .chip').forEach(c => {
    c.classList.remove('sel');
  });
  filterState.regions.clear();
  filterState.categories.clear();

  updateFilterBadge('btn-filter-region', 0, '지역');
  updateFilterBadge('btn-filter-cat', 0, '카테고리');
  document.getElementById('btn-filter-clear').style.display = 'none';

  updateResultCount();
  showToast('필터가 초기화됐어요');
}

function updateResultCount() {
  const el = document.getElementById('result-count');
  if (!el) return;
  // TODO: 실제 필터링 로직 연동
  const hasFilter = filterState.regions.size > 0 || filterState.categories.size > 0;
  el.textContent = hasFilter ? '필터 적용 중 · XX개의 장소' : '20개의 장소';
}

/* ================================================================
   일정 카드 선택
   ================================================================ */
function selectSchedule(scheduleId) {
  activeScheduleId = scheduleId;

  // 모든 카드 비활성화
  document.querySelectorAll('.schedule-card').forEach(card => {
    card.classList.remove('active');
    const btn = card.querySelector('.schedule-select-btn');
    if (btn) btn.textContent = '선택';
  });

  // 선택된 카드 활성화
  const target = document.getElementById(scheduleId);
  if (target) {
    target.classList.add('active');
    const btn = target.querySelector('.schedule-select-btn');
    if (btn) btn.textContent = '✓ 선택됨';
  }

  const name = target ? target.querySelector('.schedule-card-name').textContent : '';
  showToast('"' + name + '" 일정에 추가할 준비가 됐어요');
}

/* ================================================================
   관광지 → 후보군 추가
   ================================================================ */
function addToSchedule(btn) {
  const card = btn.closest('.attr-card');
  if (!card) return;

  card.classList.add('candidate');

  // 체크 배지 추가
  if (!card.querySelector('.candidate-badge')) {
    const badge = document.createElement('div');
    badge.className = 'candidate-badge';
    badge.textContent = '✓';
    card.appendChild(badge);
  }

  btn.textContent = '✓ 추가됨';
  btn.classList.add('added');
  btn.onclick = null;

  // 일정 카드의 장소 목록에 반영 (간단 UI 업데이트)
  const placeName = card.querySelector('.card-name')?.textContent?.replace('★', '').trim();
  appendPlaceToActiveSchedule(placeName, card);

  showToast('"' + placeName + '" 후보군에 추가됐어요');
}

function appendPlaceToActiveSchedule(name, card) {
  if (!activeScheduleId || !name) return;
  const scheduleCard = document.getElementById(activeScheduleId);
  if (!scheduleCard) return;

  // 카드 지역 정보 추출
  const catText = card.querySelector('.card-cat')?.textContent || '';
  const regionMatch = catText.match(/·\s*(.+)$/);
  const region = regionMatch ? regionMatch[1].trim() : '기타';

  // 해당 지역 영역 찾기
  let regionEl = scheduleCard.querySelector('[data-region="' + region + '"]');
  if (!regionEl) {
    // 새 지역 영역 생성
    const regionDiv = document.createElement('div');
    regionDiv.className = 'schedule-region';
    regionDiv.innerHTML =
      '<span class="region-name">📍 ' + region + '</span>' +
      '<div class="region-places" data-region="' + region + '"></div>';
    scheduleCard.querySelector('.schedule-regions').appendChild(regionDiv);
    regionEl = regionDiv.querySelector('.region-places');
  }

  const chip = document.createElement('span');
  chip.className = 'place-chip';
  chip.textContent = name;
  regionEl.appendChild(chip);
}

/* ================================================================
   칩 클릭 토글
   ================================================================ */
document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('#filter-region-chips .chip, #filter-cat-chips .chip').forEach(chip => {
    chip.addEventListener('click', () => chip.classList.toggle('sel'));
  });
});
