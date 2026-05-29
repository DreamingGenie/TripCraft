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

        <button class="kakao-btn" disabled>💬 카카오로 로그인</button>

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
