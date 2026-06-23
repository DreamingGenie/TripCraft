import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/', component: () => import('@/views/LandingView.vue') },
  { path: '/about', component: () => import('@/views/AboutView.vue') },
  { path: '/auth', component: () => import('@/views/AuthView.vue') },
  { path: '/auth/kakao/callback', component: () => import('@/views/KakaoCallbackView.vue') },
  { path: '/explore', component: () => import('@/views/ExploreView.vue') },
  {
    path: '/plan/:tripId(\\d+)?',
    component: () => import('@/views/PlanView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/schedule',
    component: () => import('@/views/ScheduleView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/calendar',
    component: () => import('@/views/CalendarView.vue'),
    meta: { requiresAuth: true },
  },
  { path: '/community', component: () => import('@/views/CommunityView.vue') },
  { path: '/community/:id(\\d+)', component: () => import('@/views/CommunityPostView.vue') },
  {
    path: '/admin',
    component: () => import('@/views/AdminView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/mypage',
    component: () => import('@/components/MyPageLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/mypage/profile' },
      { path: 'profile',   component: () => import('@/views/MyProfileView.vue') },
      { path: 'map',       component: () => import('@/views/MyMapView.vue') },
      { path: 'posts',     component: () => import('@/views/MyPostsView.vue') },
      { path: 'bookmarks', component: () => import('@/views/MyBookmarksView.vue') },
      { path: 'likes',     component: () => import('@/views/MyLikesView.vue') },
    ],
  },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('@/views/NotFoundView.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  if (to.meta.requiresAuth) {
    const auth = useAuthStore()
    if (!auth.isLoggedIn) {
      await auth.fetchMe()
      if (!auth.isLoggedIn) return { path: '/auth' }
    }
    if (to.meta.requiresAdmin && auth.user?.role !== 'ADMIN') {
      return { path: '/explore' }
    }
  }
})

export default router
