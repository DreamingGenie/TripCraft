/* TripCraft — auth.js */

const API_BASE = 'http://localhost:8080';

async function handleSubmit() {
  const btn = document.getElementById('d-submit');
  if (btn) btn.disabled = true;
  const isLogin = document.getElementById('tab-login').classList.contains('active');
  try {
    if (isLogin) await doLogin();
    else await doSignup();
  } finally {
    if (btn) btn.disabled = false;
  }
}

async function doLogin() {
  const email    = document.getElementById('login-email').value.trim();
  const password = document.getElementById('login-password').value;

  if (!email || !password) {
    showToast('이메일과 비밀번호를 입력해주세요.');
    return;
  }

  let res, json;
  try {
    res  = await fetch(API_BASE + '/api/auth/login', {
      method:      'POST',
      headers:     { 'Content-Type': 'application/json' },
      credentials: 'include',
      body:        JSON.stringify({ email, password }),
    });
    json = await res.json();
  } catch (e) {
    showToast('서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.');
    return;
  }

  if (json.success) {
    localStorage.setItem('tripcraft_token', json.data.accessToken);
    localStorage.setItem('tripcraft_nickname', email.split('@')[0]);
    window.location.href = 'explore.html';
  } else {
    handleAuthError(res.status, json);
  }
}

async function doSignup() {
  const nickname = document.getElementById('signup-nickname').value.trim();
  const email    = document.getElementById('signup-email').value.trim();
  const password = document.getElementById('signup-password').value;
  const confirm  = document.getElementById('signup-password-confirm').value;

  if (!nickname || !email || !password) {
    showToast('모든 항목을 입력해주세요.');
    return;
  }
  if (password.length < 8) {
    showToast('비밀번호는 8자 이상이어야 합니다.');
    return;
  }
  if (password !== confirm) {
    showToast('비밀번호가 일치하지 않습니다.');
    return;
  }

  let res, json;
  try {
    res  = await fetch(API_BASE + '/api/auth/signup', {
      method:      'POST',
      headers:     { 'Content-Type': 'application/json' },
      credentials: 'include',
      body:        JSON.stringify({ email, password, nickname }),
    });
    json = await res.json();
  } catch (e) {
    showToast('서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.');
    return;
  }

  if (json.success) {
    showToast('회원가입이 완료됐어요. 로그인해주세요.');
    setTimeout(() => authSwitch('login'), 1000);
  } else {
    handleAuthError(res.status, json);
  }
}

function handleAuthError(status, json) {
  if (status === 409)      showToast('이미 사용 중인 이메일입니다.');
  else if (status === 401) showToast('이메일 또는 비밀번호가 올바르지 않습니다.');
  else if (status === 400) showToast(json.message || '입력값을 확인해주세요.');
  else                     showToast(json.message || '오류가 발생했습니다. 다시 시도해주세요.');
}

document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('#login-fields input, #signup-fields input').forEach(input => {
    input.addEventListener('keydown', e => { if (e.key === 'Enter') handleSubmit(); });
  });
});
