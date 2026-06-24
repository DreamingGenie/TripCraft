<template>
  <main id="main" class="mypage-main">
    <!-- 전체폭 스크롤 래퍼: 스크롤바를 뷰포트 끝으로 (커뮤니티 #community-layout 패턴과 통일) -->
    <div class="mypage-shell">
      <!-- 안쪽 폭 제한 래퍼: 960px 중앙정렬로 커뮤니티 목록 폭과 통일 -->
      <div class="mypage-inner">
      <header class="mypage-top">
        <div class="mypage-avatar">
          <img v-if="profileImageUrl" :src="profileImageUrl" alt="" class="mypage-avatar-img" />
          <span v-else>{{ (auth.user?.nickname || '?')[0] }}</span>
        </div>
        <div class="mypage-top-info">
          <p class="mypage-greeting">{{ auth.user?.nickname || '회원' }}님</p>
          <!-- 신원 표시만: 이메일 계정은 이메일, 소셜은 계정유형 뱃지 (편집은 내 정보 탭 전담) -->
          <span class="mypage-account-badge">
            {{ isSocial ? '카카오 로그인' : auth.user?.email }}
          </span>
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
    </div>
  </main>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { memberApi } from '@/api/member'

const auth = useAuthStore()
const profileImageUrl = ref(null)

// 소셜 계정(카카오 등)은 이메일이 없으므로 신원 뱃지 분기 (auth.user.socialProvider)
const isSocial = computed(() => !!auth.user?.socialProvider)

// 라인 SVG path 데이터(단일 아이콘 세트, design_system §2·§6)
const tabs = [
  { to: '/mypage/trips',     label: '내 여행',   icon: 'M3 7l6-3 6 3 6-3v13l-6 3-6-3-6 3V7ZM9 4v13M15 7v13' },
  { to: '/mypage/profile',   label: '내 정보',   icon: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM4.5 20a7.5 7.5 0 0 1 15 0' },
  { to: '/mypage/places',    label: '내 장소',   icon: 'M12 21s-6-5.7-6-10a6 6 0 0 1 12 0c0 4.3-6 10-6 10ZM12 11a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z' },
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
/* 전역 #main { overflow:hidden } 를 이기기 위해 스크롤은 shell 이 담당.
   shell 은 전체폭(스크롤바=뷰포트 끝), 폭 제한은 안쪽 .mypage-inner 가 담당 — 커뮤니티와 동일 패턴 */
.mypage-shell {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background: var(--bg-page);
}
.mypage-inner {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  padding: 36px 24px 80px;
  box-sizing: border-box;
}

/* ── 상단 신원 표시(슬림) ── */
.mypage-top {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 14px 18px;
  background: var(--bg-surface);
  border: 1px solid var(--gray-border);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-sm);
  color: var(--text-primary);
  margin-bottom: var(--space-5);
}
.mypage-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--purple-50);
  color: var(--purple-900);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  flex-shrink: 0;
  overflow: hidden;
  text-transform: uppercase;
  border: 2px solid var(--purple-100);
}
.mypage-avatar-img { width: 100%; height: 100%; object-fit: cover; }
.mypage-top-info { min-width: 0; display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.mypage-greeting { font-size: var(--text-lg); font-weight: 700; letter-spacing: -0.02em; color: var(--text-primary); }
/* 계정유형 뱃지: 이메일 계정=이메일, 소셜=로그인 수단 표시 */
.mypage-account-badge {
  font-size: var(--text-xs);
  color: var(--gray-muted);
  background: var(--bg-page);
  border: 1px solid var(--gray-border);
  border-radius: var(--radius-full);
  padding: 2px 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
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
  .mypage-inner { padding: 24px 16px 60px; }
  .mypage-top { padding: 12px 14px; }
}
</style>
