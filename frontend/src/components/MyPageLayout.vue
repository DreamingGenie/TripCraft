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
          <span class="mypage-tab-ico">{{ t.icon }}</span>{{ t.label }}
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

const tabs = [
  { to: '/mypage/profile',   label: '내 정보',   icon: '👤' },
  { to: '/mypage/map',       label: '방문 지도', icon: '🗺' },
  { to: '/mypage/posts',     label: '내가 쓴 글', icon: '✍' },
  { to: '/mypage/bookmarks', label: '북마크',    icon: '🔖' },
  { to: '/mypage/likes',     label: '좋아요',    icon: '♥' },
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
  gap: 16px;
  padding: 22px 24px;
  background: linear-gradient(135deg, var(--purple-900) 0%, #6b62cf 100%);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-md);
  color: #fff;
  margin-bottom: 20px;
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
.mypage-greeting { font-size: 18px; font-weight: 700; letter-spacing: -0.02em; }
.mypage-email {
  font-size: 13px;
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
  font-size: 13px;
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
.mypage-tab-ico { font-size: 13px; line-height: 1; }

.mypage-content { min-height: 200px; }

@media (max-width: 640px) {
  .mypage-shell { padding: 24px 16px 60px; }
  .mypage-top { padding: 18px 18px; }
}
</style>
