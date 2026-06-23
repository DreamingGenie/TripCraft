<template>
  <header id="gnb">
    <RouterLink class="logo" to="/">TripCraft</RouterLink>

    <!-- 1차 탭 3개: 탐색 / 내 여행 / 커뮤니티 (§2.2/§2.4) -->
    <!-- 탐색: 맥락 라우팅 (로그인 + 현재여행 → /plan/:id, 아니면 /discover) -->
    <button class="nav-link" :class="{ active: isExploreActive }" @click="goExplore">탐색</button>
    <RouterLink class="nav-link" to="/trips" active-class="active">내 여행</RouterLink>
    <RouterLink class="nav-link" to="/community" active-class="active">커뮤니티</RouterLink>
    <RouterLink v-if="auth.user?.role === 'ADMIN'" class="nav-link nav-admin" to="/admin" active-class="active">관리자</RouterLink>

    <span class="gnb-spacer"></span>

    <!-- 현재여행 칩 (로그인 + 여행 보유 시 노출) -->
    <TripChip v-if="auth.isLoggedIn" />

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
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useActiveTripStore } from '@/stores/activeTrip'
import { useRouter, useRoute } from 'vue-router'
import TripChip from '@/components/TripChip.vue'

const auth  = useAuthStore()
const toast = useToastStore()
const activeTripStore = useActiveTripStore()
const router = useRouter()
const route  = useRoute()

const menuOpen = ref(false)
const wrapRef  = ref(null)

// 탐색 탭 활성: /discover 또는 /plan(explore) 화면일 때
const isExploreActive = computed(
  () => route.path === '/discover' || route.path.startsWith('/plan'),
)

// §2.4 맥락 라우팅: 로그인 + 현재여행 있으면 작업실(explore), 아니면 공개 탐색
function goExplore() {
  if (auth.isLoggedIn && activeTripStore.id != null) {
    router.push(`/plan/${activeTripStore.id}`)
  } else {
    router.push('/discover')
  }
}

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
/* 탐색 탭은 button — 전역 .nav-link 와 동일 폭/패딩 보정 */
button.nav-link {
  font-family: inherit;
  cursor: pointer;
}

.gnb-user-wrap {
  position: relative;
}

.gnb-user-btn {
  font-size: var(--text-base);
  color: var(--gray-dark);
  background: none;
  border: none;
  padding: 8px 14px;
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
  min-width: 220px;
  background: var(--bg-surface);
  border: 0.5px solid var(--gray-border);
  border-radius: var(--radius-md);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.10);
  z-index: 100;
  overflow: hidden;
}

.dropdown-item {
  display: block;
  padding: 12px 20px;
  font-size: var(--text-sm);
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
