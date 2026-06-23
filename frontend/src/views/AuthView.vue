<template>
  <main id="main">
  <section id="screen-auth">

    <!-- 좌측 브랜드 패널 -->
    <div class="auth-brand">
      <div class="auth-brand-logo">TripCraft</div>
      <h2 class="auth-brand-headline">여행을 함께,<br>더 스마트하게</h2>
      <p class="auth-brand-sub">관광지 탐색부터 이동 시간 계산까지<br>모든 여행 계획을 한 곳에서</p>
      <div class="auth-brand-features">
        <div class="auth-brand-feature">
          <span class="auth-feature-check">✓</span>
          전국 관광지 탐색
        </div>
        <div class="auth-brand-feature">
          <span class="auth-feature-check">✓</span>
          드래그로 일정 구성
        </div>
        <div class="auth-brand-feature">
          <span class="auth-feature-check">✓</span>
          이동 시간 자동 계산
        </div>
      </div>
    </div>

    <!-- 우측 폼 패널 -->
    <div class="auth-form-panel">
      <div class="auth-form-inner">
        <h1 class="auth-form-title">{{ tab === 'login' ? '로그인' : '회원가입' }}</h1>
        <p class="auth-form-hint">{{ tab === 'login' ? 'TripCraft에 오신 걸 환영합니다.' : '새 계정을 만들어 여행을 시작하세요.' }}</p>

        <div class="tab-switcher">
          <button class="auth-tab" :class="{ active: tab === 'login' }" @click="tab = 'login'">로그인</button>
          <button class="auth-tab" :class="{ active: tab === 'signup' }" @click="tab = 'signup'">회원가입</button>
        </div>

        <!-- 로그인 -->
        <div v-show="tab === 'login'">
          <label class="field-label">이메일</label>
          <input class="field-input" type="email" v-model="login.email"
                 placeholder="hello@example.com" autocomplete="email" @keydown.enter="submit" />
          <label class="field-label">비밀번호</label>
          <input class="field-input" type="password" v-model="login.password"
                 placeholder="••••••••" autocomplete="current-password" @keydown.enter="submit" />
        </div>

        <!-- 회원가입 -->
        <div v-show="tab === 'signup'">
          <label class="field-label">이름 (닉네임)</label>
          <input class="field-input" type="text" v-model="signup.nickname"
                 placeholder="홍길동" @keydown.enter="submit" />
          <label class="field-label">이메일</label>
          <input class="field-input" type="email" v-model="signup.email"
                 placeholder="hello@example.com" autocomplete="email" @keydown.enter="submit" />
          <label class="field-label">비밀번호</label>
          <input class="field-input" type="password" v-model="signup.password"
                 placeholder="•••••••• (8자 이상)" autocomplete="new-password" @keydown.enter="submit" />
          <label class="field-label">비밀번호 확인</label>
          <input class="field-input" type="password" v-model="signup.confirm"
                 placeholder="••••••••" autocomplete="new-password" @keydown.enter="submit" />
        </div>

        <button class="submit-btn" :disabled="loading" @click="submit">
          {{ loading ? '처리 중...' : (tab === 'login' ? '로그인' : '회원가입') }}
        </button>

        <div class="divider">
          <div class="divider-line"></div>
          <span class="divider-text">또는</span>
          <div class="divider-line"></div>
        </div>

        <button class="kakao-btn" @click="kakaoChoice = true">💬 카카오로 로그인</button>

        <!-- 카카오 계정 선택 팝업 -->
        <div v-if="kakaoChoice" class="kakao-modal-overlay" @click.self="kakaoChoice = false">
          <div class="kakao-modal">
            <p class="kakao-modal-title">카카오 로그인</p>
            <button class="kakao-modal-btn" @click="chooseKakao(false)">빠른 로그인 <small>(최근 계정)</small></button>
            <button class="kakao-modal-btn secondary" @click="chooseKakao(true)">다른 계정으로 로그인</button>
            <button class="kakao-modal-cancel" @click="kakaoChoice = false">취소</button>
          </div>
        </div>

        <p class="switch-link">
          <template v-if="tab === 'login'">
            아직 계정이 없으신가요?
            <a @click="tab = 'signup'">회원가입</a>
          </template>
          <template v-else>
            이미 계정이 있으신가요?
            <a @click="tab = 'login'">로그인</a>
          </template>
        </p>

        <RouterLink class="explore-link" to="/explore">탐색 먼저 해볼게요 →</RouterLink>
      </div>
    </div>

  </section>
  </main>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const router = useRouter()
const auth = useAuthStore()
const toast = useToastStore()

const tab = ref('login')
const loading = ref(false)
const kakaoChoice = ref(false)   // 카카오 계정 선택 팝업 표시 여부

const login = reactive({ email: '', password: '' })
const signup = reactive({ nickname: '', email: '', password: '', confirm: '' })

async function submit() {
  if (loading.value) return
  loading.value = true
  try {
    if (tab.value === 'login') {
      await doLogin()
    } else {
      await doSignup()
    }
  } finally {
    loading.value = false
  }
}

async function doLogin() {
  if (!login.email || !login.password) {
    toast.show('이메일과 비밀번호를 입력해주세요.')
    return
  }
  const result = await auth.login(login.email, login.password)
  if (result.ok) {
    router.push('/explore')
  } else {
    toast.show(result.status === 401 ? '이메일 또는 비밀번호가 올바르지 않습니다.' : (result.message || '오류가 발생했습니다.'))
  }
}

function chooseKakao(differentAccount) {
  kakaoChoice.value = false
  startKakaoLogin(differentAccount)
}

function startKakaoLogin(differentAccount = false) {
  const restKey = import.meta.env.VITE_KAKAO_REST_KEY
  const redirect = import.meta.env.VITE_KAKAO_REDIRECT_URI
  if (!restKey || !redirect) {
    toast.show('카카오 로그인이 아직 설정되지 않았습니다.')
    return
  }
  let url = `https://kauth.kakao.com/oauth/authorize?client_id=${restKey}`
    + `&redirect_uri=${encodeURIComponent(redirect)}&response_type=code`
    + `&scope=profile_nickname,profile_image`   // 닉네임·프로필사진 동의 명시 요청
  // 기본: 카카오 세션 있으면 원클릭(기존 계정). '다른 계정'은 로그인 화면 강제.
  if (differentAccount) {
    url += `&prompt=login`
  }
  window.location.href = url
}

async function doSignup() {
  if (!signup.nickname || !signup.email || !signup.password) {
    toast.show('모든 항목을 입력해주세요.')
    return
  }
  if (signup.password.length < 8) {
    toast.show('비밀번호는 8자 이상이어야 합니다.')
    return
  }
  if (signup.password !== signup.confirm) {
    toast.show('비밀번호가 일치하지 않습니다.')
    return
  }
  const result = await auth.signup(signup.email, signup.password, signup.nickname)
  if (result.ok) {
    toast.show('회원가입이 완료됐어요. 로그인해주세요.')
    tab.value = 'login'
    signup.nickname = signup.email = signup.password = signup.confirm = ''
  } else {
    toast.show(result.status === 409 ? '이미 사용 중인 이메일입니다.' : (result.message || '오류가 발생했습니다.'))
  }
}
</script>

<style scoped>
.kakao-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.kakao-modal {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  width: 280px;
  max-width: 90vw;
  display: flex;
  flex-direction: column;
  gap: 10px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.2);
}
.kakao-modal-title {
  margin: 0 0 4px;
  font-weight: 600;
  text-align: center;
}
.kakao-modal-btn {
  padding: 12px;
  border: none;
  border-radius: 8px;
  background: #fee500;
  color: #191600;
  font-weight: 600;
  cursor: pointer;
}
.kakao-modal-btn.secondary {
  background: #f2f2f2;
  color: #333;
}
.kakao-modal-btn small {
  font-weight: 400;
  opacity: 0.7;
}
.kakao-modal-cancel {
  padding: 8px;
  border: none;
  background: none;
  color: #888;
  cursor: pointer;
  font-size: 0.9rem;
}
</style>
