<template>
  <main id="main">
    <div class="kakao-callback">
      <p>카카오 로그인 처리 중...</p>
    </div>
  </main>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const router = useRouter()
const auth = useAuthStore()
const toast = useToastStore()

onMounted(async () => {
  const params = new URLSearchParams(window.location.search)
  const code = params.get('code')
  const error = params.get('error')

  if (error || !code) {
    toast.show('카카오 로그인이 취소되었습니다.')
    router.replace('/auth')
    return
  }

  // 백엔드가 access/refresh 토큰을 HttpOnly 쿠키로 심으므로 응답 바디에서 토큰을 읽지 않는다.
  const result = await auth.kakaoLogin(code)
  if (result.ok) {
    router.replace('/plan')
  } else {
    toast.show(result.message || '카카오 로그인에 실패했습니다.')
    router.replace('/auth')
  }
})
</script>

<style scoped>
.kakao-callback {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  color: var(--text-secondary, #666);
}
</style>
