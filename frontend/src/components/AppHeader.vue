<template>
  <header id="gnb">
    <RouterLink class="logo" to="/">TripCraft</RouterLink>
    <RouterLink class="nav-link" to="/explore" active-class="active">관광지 탐색</RouterLink>
    <RouterLink class="nav-link" to="/schedule" active-class="active">내 일정</RouterLink>
    <RouterLink class="nav-link" to="/calendar" active-class="active">내 여행 일지</RouterLink>
    <RouterLink class="nav-link" to="/community" active-class="active">커뮤니티</RouterLink>
    <RouterLink v-if="auth.user?.role === 'ADMIN'" class="nav-link nav-admin" to="/admin" active-class="active">관리자</RouterLink>
    <span class="gnb-spacer"></span>

    <template v-if="auth.isLoggedIn">
      <!-- 닉네임 드롭다운 트리거 -->
      <div class="gnb-user-wrap" ref="wrapRef">
        <button class="gnb-user-btn" @click="toggleMenu" :aria-expanded="menuOpen">
          {{ auth.user?.nickname }}님 ▾
        </button>

        <div v-show="menuOpen" class="gnb-dropdown" @click="menuOpen = false">
          <RouterLink class="dropdown-item" to="/mypage/profile">내 정보 수정</RouterLink>
          <RouterLink class="dropdown-item" to="/mypage/map">방문 지도</RouterLink>
          <RouterLink class="dropdown-item" to="/mypage/posts">내가 쓴 글</RouterLink>
          <RouterLink class="dropdown-item" to="/mypage/bookmarks">북마크</RouterLink>
          <RouterLink class="dropdown-item" to="/mypage/likes">좋아요한 글</RouterLink>
        </div>
      </div>

      <button class="btn-ghost" @click="handleLogout">로그아웃</button>
    </template>

    <template v-else>
      <RouterLink class="btn-ghost" to="/auth">로그인</RouterLink>
      <RouterLink class="btn-primary" to="/auth">회원가입</RouterLink>
    </template>
  </header>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useRouter } from 'vue-router'

const auth  = useAuthStore()
const toast = useToastStore()
const router = useRouter()

const menuOpen = ref(false)
const wrapRef  = ref(null)

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function onOutsideClick(e) {
  if (wrapRef.value && !wrapRef.value.contains(e.target)) {
    menuOpen.value = false
  }
}

onMounted(() => document.addEventListener('click', onOutsideClick, true))
onBeforeUnmount(() => document.removeEventListener('click', onOutsideClick, true))

async function handleLogout() {
  await auth.logout()
  toast.show('로그아웃됐어요.')
  router.push('/auth')
}
</script>

<style scoped>
.gnb-user-wrap {
  position: relative;
}

.gnb-user-btn {
  font-size: var(--font-size-xs);
  color: var(--gray-dark);
  background: none;
  border: none;
  padding: 5px 8px;
  border-radius: var(--radius-md);
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.12s;
}
.gnb-user-btn:hover {
  background: var(--bg-page);
}

.gnb-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 148px;
  background: var(--bg-surface);
  border: 0.5px solid var(--gray-border);
  border-radius: var(--radius-md);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.10);
  z-index: 100;
  overflow: hidden;
}

.dropdown-item {
  display: block;
  padding: 9px 16px;
  font-size: var(--font-size-xs);
  color: var(--text-primary);
  text-decoration: none;
  transition: background 0.1s;
}
.dropdown-item:hover {
  background: var(--bg-page);
}
.dropdown-item.router-link-active {
  color: var(--purple-900);
  font-weight: 500;
  background: var(--purple-50);
}
</style>
