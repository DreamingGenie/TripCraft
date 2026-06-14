import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/', component: () => import('@/views/LandingView.vue') },
  { path: '/about', component: () => import('@/views/AboutView.vue') },
  { path: '/auth', component: () => import('@/views/AuthView.vue') },
  { path: '/explore', component: () => import('@/views/ExploreView.vue') },
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
    path: '/mypage/bookmarks',
    component: () => import('@/views/MyBookmarksView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/mypage/likes',
    component: () => import('@/views/MyLikesView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/mypage/posts',
    component: () => import('@/views/MyPostsView.vue'),
    meta: { requiresAuth: true },
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
