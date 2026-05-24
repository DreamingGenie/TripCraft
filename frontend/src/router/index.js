import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/', redirect: '/explore' },
  { path: '/auth', component: () => import('@/views/AuthView.vue') },
  { path: '/explore', component: () => import('@/views/ExploreView.vue') },
  {
    path: '/schedule',
    component: () => import('@/views/ScheduleView.vue'),
    meta: { requiresAuth: true },
  },
  { path: '/community', component: () => import('@/views/CommunityView.vue') },
  {
    path: '/admin',
    component: () => import('@/views/AdminView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
  },
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
