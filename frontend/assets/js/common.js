/* ============================================================
   TripCraft — common.js
   모든 페이지 공통 유틸: 토스트, 일정 생성 모달
   ============================================================ */

/* ── 토스트 ── */
function showToast(msg, duration = 3000) {
  const toast = document.getElementById('toast');
  if (!toast) return;
  toast.innerHTML = '⭐ ' + msg;
  toast.classList.add('show');
  clearTimeout(toast._timeout);
  toast._timeout = setTimeout(() => toast.classList.remove('show'), duration);
}

/* ── 일정 생성 모달 ── */
let _personCount = 2;

function openScheduleModal() {
  const overlay = document.getElementById('modal-overlay');
  if (overlay) overlay.classList.add('open');
}

function closeScheduleModal() {
  const overlay = document.getElementById('modal-overlay');
  if (overlay) overlay.classList.remove('open');
}

function calcNights() {
  const s = new Date(document.getElementById('m-start').value);
  const e = new Date(document.getElementById('m-end').value);
  const el = document.getElementById('m-nights');
  if (!el) return;
  if (!isNaN(s) && !isNaN(e) && e >= s) {
    const n = Math.floor((e - s) / 86400000);
    el.textContent = '총 ' + n + '박 ' + (n + 1) + '일';
  } else {
    el.textContent = '';
  }
}

function checkModalForm() {
  const t = document.getElementById('m-title');
  if (!t) return;
  const countEl = document.getElementById('m-title-count');
  if (countEl) countEl.textContent = t.value.length + '/30';
  const s = document.getElementById('m-start').value;
  const e = document.getElementById('m-end').value;
  const ok = t.value.trim() && s && e && new Date(e) >= new Date(s);
  const btn = document.getElementById('m-create');
  if (btn) {
    btn.disabled = !ok;
    btn.style.background = ok ? 'var(--purple-900)' : 'var(--gray-border)';
  }
}

function adjustPersonCount(delta) {
  _personCount = Math.min(20, Math.max(1, _personCount + delta));
  const el = document.getElementById('m-count');
  if (el) el.textContent = _personCount + '명';
}

function createSchedule() {
  closeScheduleModal();
  showToast('새 일정이 생성됐어요!');
  // TODO: API 연동 후 실제 일정 생성 로직으로 교체
  setTimeout(() => {
    window.location.href = 'schedule.html';
  }, 800);
}

/* ── GNB 인증 영역 ── */
function initGnb() {
  const area = document.getElementById('gnb-auth');
  if (!area) return;
  const token    = localStorage.getItem('tripcraft_token');
  const nickname = localStorage.getItem('tripcraft_nickname');
  if (token) {
    area.innerHTML =
      `<span class="gnb-user">${nickname ? nickname + '님' : '회원님'}</span>` +
      `<button class="btn-ghost" onclick="logout()">로그아웃</button>`;
  } else {
    area.innerHTML =
      `<button class="btn-ghost" onclick="window.location.href='auth.html'">로그인</button>` +
      `<button class="btn-primary" onclick="window.location.href='auth.html'">회원가입</button>`;
  }
}

async function logout() {
  const token = localStorage.getItem('tripcraft_token');
  try {
    await fetch('http://localhost:8080/api/auth/logout', {
      method:      'POST',
      credentials: 'include',
      headers:     token ? { 'Authorization': 'Bearer ' + token } : {},
    });
  } catch (e) { /* 네트워크 에러 무시 */ }
  localStorage.removeItem('tripcraft_token');
  localStorage.removeItem('tripcraft_nickname');
  showToast('로그아웃됐어요.');
  setTimeout(() => { window.location.href = 'auth.html'; }, 800);
}

/* ── 모달 외부 클릭 닫기 ── */
document.addEventListener('DOMContentLoaded', () => {
  initGnb();

  const overlay = document.getElementById('modal-overlay');
  if (overlay) {
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) closeScheduleModal();
    });
  }
});
