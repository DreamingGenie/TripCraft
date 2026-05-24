<template>
  <header id="gnb">
    <RouterLink class="logo" to="/explore">TripCraft</RouterLink>
    <RouterLink class="nav-link" to="/explore" active-class="active">관광지 탐색</RouterLink>
    <RouterLink class="nav-link" to="/schedule" active-class="active">내 일정</RouterLink>
    <RouterLink class="nav-link" to="/community" active-class="active">커뮤니티</RouterLink>
    <span class="gnb-spacer"></span>
    <template v-if="auth.isLoggedIn">
      <span class="gnb-user">{{ auth.user?.nickname }}님</span>
      <button class="btn-ghost" @click="handleLogout">로그아웃</button>
    </template>
    <template v-else>
      <RouterLink class="btn-ghost" to="/auth">로그인</RouterLink>
      <RouterLink class="btn-primary" to="/auth">회원가입</RouterLink>
    </template>
  </header>
</template>

<script setup>
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useRouter } from 'vue-router'

const auth = useAuthStore()
const toast = useToastStore()
const router = useRouter()

async function handleLogout() {
  await auth.logout()
  toast.show('로그아웃됐어요.')
  router.push('/auth')
}
</script>
