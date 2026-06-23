<template>
  <main id="main" class="mypage-main">
    <div class="mypage-shell">
      <header class="mypage-top">
        <div class="mypage-avatar">
          <img v-if="profileImageUrl" :src="profileImageUrl" alt="" class="mypage-avatar-img" />
          <span v-else>{{ (auth.user?.nickname || '?')[0] }}</span>
        </div>
        <div class="mypage-top-info">
          <p class="mypage-greeting">{{ auth.user?.nickname || '회원' }}님</p>
          <p class="mypage-email">{{ auth.user?.email }}</p>
        </div>
      </header>

      <nav class="mypage-tabs">
        <RouterLink
          v-for="t in tabs" :key="t.to" :to="t.to"
          class="mypage-tab" active-class="active"
        >
          <svg class="mypage-tab-ico" viewBox="0 0 24 24" width="15" height="15" fill="none"
               stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path :d="t.icon" />
          </svg>{{ t.label }}
        </RouterLink>
      </nav>

      <section class="mypage-content">
        <RouterView />
      </section>
    </div>
  </main>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { memberApi } from '@/api/member'

const auth = useAuthStore()
const profileImageUrl = ref(null)

// 라인 SVG path 데이터(단일 아이콘 세트, design_system §2·§6)
const tabs = [
  { to: '/mypage/trips',     label: '내 여행',   icon: 'M3 7l6-3 6 3 6-3v13l-6 3-6-3-6 3V7ZM9 4v13M15 7v13' },
  { to: '/mypage/profile',   label: '내 정보',   icon: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM4.5 20a7.5 7.5 0 0 1 15 0' },
  { to: '/mypage/map',       label: '방문 지도', icon: 'm9 4-6 2.4v13.6L9 18l6 2 6-2.4V4l-6 2.4-6-2.4ZM9 4v14M15 6v14' },
  { to: '/mypage/posts',     label: '내가 쓴 글', icon: 'M12 20h9M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z' },
  { to: '/mypage/bookmarks', label: '북마크',    icon: 'M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z' },
  { to: '/mypage/likes',     label: '좋아요',    icon: 'M20.8 5.6a5.5 5.5 0 0 0-7.8 0L12 6.6l-1-1a5.5 5.5 0 0 0-7.8 7.8L12 22l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z' },
]

onMounted(async () => {
  try { profileImageUrl.value = await memberApi.getProfileImage() } catch {}
})
</script>

<style scoped>
.mypage-main {
  background: var(--bg-page);
}
/* 전역 #main { overflow:hidden } 를 이기기 위해 스크롤은 shell 이 담당 */
.mypage-shell {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  width: 100%;
  max-width: 940px;
  margin: 0 auto;
  padding: 36px 24px 80px;
  box-sizing: border-box;
}

/* ── 상단 프로필 요약 ── */
.mypage-top {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: 22px 24px;
  background: var(--purple-900);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-md);
  color: #fff;
  margin-bottom: var(--space-5);
}
.mypage-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(255, 255, 255, .2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  flex-shrink: 0;
  overflow: hidden;
  text-transform: uppercase;
  border: 2px solid rgba(255, 255, 255, .35);
}
.mypage-avatar-img { width: 100%; height: 100%; object-fit: cover; }
.mypage-top-info { min-width: 0; }
.mypage-greeting { font-size: var(--text-xl); font-weight: 700; letter-spacing: -0.02em; }
.mypage-email {
  font-size: var(--text-sm);
  opacity: .82;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── 탭 내비 ── */
.mypage-tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 24px;
  overflow-x: auto;
  padding-bottom: 2px;
  scrollbar-width: none;
}
.mypage-tabs::-webkit-scrollbar { display: none; }
.mypage-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding: 9px 16px;
  border-radius: var(--radius-full);
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--gray-dark);
  background: var(--bg-surface);
  border: 1px solid var(--gray-border);
  text-decoration: none;
  white-space: nowrap;
  transition: background .14s, color .14s, border-color .14s, box-shadow .14s;
}
.mypage-tab:hover {
  color: var(--purple-900);
  border-color: var(--purple-100);
  background: var(--purple-50);
}
.mypage-tab.active {
  color: #fff;
  background: var(--purple-900);
  border-color: var(--purple-900);
  box-shadow: 0 2px 8px rgba(83, 74, 183, .22);
}
.mypage-tab-ico { flex-shrink: 0; }

.mypage-content { min-height: 200px; }

@media (max-width: 640px) {
  .mypage-shell { padding: 24px 16px 60px; }
  .mypage-top { padding: 18px 18px; }
}
</style>
