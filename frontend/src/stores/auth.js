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

  async function logout() {
    try {
      await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' })
    } catch {}
    user.value = null
  }

  return { user, isLoggedIn, fetchMe, login, signup, logout }
})
