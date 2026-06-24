import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

// 라우트는 IA(정보구조) 단위로 구획. 각 그룹의 진입점·권한을 한눈에 보이게 한다.

// ── 공개: 진입·소개·탐색 ──
const publicRoutes = [
  { path: '/', component: () => import('@/views/LandingView.vue') },
  { path: '/about', component: () => import('@/views/AboutView.vue') },
  { path: '/discover', component: () => import('@/views/ExploreView.vue') },
]

// ── 인증 ──
const authRoutes = [
  { path: '/auth', component: () => import('@/views/AuthView.vue') },
  { path: '/auth/kakao/callback', component: () => import('@/views/KakaoCallbackView.vue') },
]

// ── 여행 작업실(/plan): 탐색·정리 모드 통합 화면 ──
const planRoutes = [
  {
    path: '/plan/:tripId(\\d+)?',
    component: () => import('@/views/PlanView.vue'),
    meta: { requiresAuth: true },
  },
]

// ── 커뮤니티(여행 이야기) ──
const communityRoutes = [
  { path: '/community', component: () => import('@/views/CommunityView.vue') },
  // 전용 작성/수정 화면(모달 폐지). :id가 \d+라 /write와 충돌 없음. requiresAuth로 보호.
  { path: '/community/write', component: () => import('@/views/CommunityWriteView.vue'), meta: { requiresAuth: true } },
  { path: '/community/:id(\\d+)/edit', component: () => import('@/views/CommunityWriteView.vue'), meta: { requiresAuth: true } },
  { path: '/community/:id(\\d+)', component: () => import('@/views/CommunityPostView.vue') },
]

// ── 관리자 ──
const adminRoutes = [
  {
    path: '/admin',
    component: () => import('@/views/AdminView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
  },
]

// ── 마이페이지: 공통 셸 + 탭 자식 라우트 ──
const mypageRoutes = [
  {
    path: '/mypage',
    component: () => import('@/components/MyPageLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/mypage/trips' },
      { path: 'trips',     component: () => import('@/views/TripsView.vue') },
      { path: 'profile',   component: () => import('@/views/MyProfileView.vue') },
      { path: 'map',       component: () => import('@/views/MyMapView.vue') },
      { path: 'posts',     component: () => import('@/views/MyPostsView.vue') },
      { path: 'bookmarks', component: () => import('@/views/MyBookmarksView.vue') },
      { path: 'likes',     component: () => import('@/views/MyLikesView.vue') },
    ],
  },
]

// ── 구 경로 리다이렉트(하위호환): 재설계 이전 URL 보존 ──
// /explore→/discover, /schedule·/trips·/calendar→/mypage/trips (단독 일정/캘린더 화면은 폐지)
const legacyRedirects = [
  { path: '/explore',  redirect: '/discover' },
  { path: '/schedule', redirect: '/mypage/trips' },
  { path: '/trips',    redirect: '/mypage/trips' },
  { path: '/calendar', redirect: '/mypage/trips' },
]

const routes = [
  ...publicRoutes,
  ...authRoutes,
  ...planRoutes,
  ...communityRoutes,
  ...adminRoutes,
  ...mypageRoutes,
  ...legacyRedirects,
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('@/views/NotFoundView.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  if (to.meta.requiresAuth) {
    // 공유 링크(?s=token): /plan 비로그인 조회 허용 — PlanView 가 getShared 로 로드
    if (to.path.startsWith('/plan/') && to.query.s) return true
    const auth = useAuthStore()
    if (!auth.isLoggedIn) {
      await auth.fetchMe()
      if (!auth.isLoggedIn) return { path: '/auth' }
    }
    if (to.meta.requiresAdmin && auth.user?.role !== 'ADMIN') {
      return { path: '/discover' }
    }
  }
})

export default router
