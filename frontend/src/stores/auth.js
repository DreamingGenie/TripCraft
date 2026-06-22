import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useToastStore } from './toast'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const isLoggedIn = computed(() => !!user.value)

  async function fetchMe() {
    try {
      const res = await fetch('/api/auth/me', { credentials: 'include' })
      if (res.ok) {
        const json = await res.json()
        user.value = json.data
      } else {
        user.value = null
      }
    } catch {
      user.value = null
    }
  }

  async function login(email, password) {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ email, password }),
    })
    const json = await res.json()
    if (json.success) {
      await fetchMe()
      return { ok: true }
    }
    return { ok: false, status: res.status, message: json.message }
  }

  async function signup(email, password, nickname) {
    const res = await fetch('/api/auth/signup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ email, password, nickname }),
    })
    const json = await res.json()
    return { ok: json.success, status: res.status, message: json.message }
  }

  async function kakaoLogin(code) {
    const res = await fetch('/api/auth/kakao', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ code }),
    })
    const json = await res.json()
    if (json.success) {
      await fetchMe()
      return { ok: true }
    }
    return { ok: false, status: res.status, message: json.message }
  }

  async function logout() {
    const wasKakao = user.value?.socialProvider === 'kakao'
    try {
      await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' })
    } catch {}
    user.value = null
    // 카카오로 로그인한 경우 카카오 세션도 함께 로그아웃 (이후 재로그인 시 카카오가 다시 인증 요구)
    if (wasKakao) {
      const restKey = import.meta.env.VITE_KAKAO_REST_KEY
      const logoutRedirect = import.meta.env.VITE_KAKAO_LOGOUT_REDIRECT_URI
      if (restKey && logoutRedirect) {
        window.location.href =
          `https://kauth.kakao.com/oauth/logout?client_id=${restKey}`
          + `&logout_redirect_uri=${encodeURIComponent(logoutRedirect)}`
      }
    }
  }

  return { user, isLoggedIn, fetchMe, login, signup, kakaoLogin, logout }
})
