import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
    { path: '/kb', name: 'kb', component: () => import('@/views/KnowledgeBaseView.vue') },
    { path: '/kb/:kbId/reader/:docId?', name: 'kb-reader', component: () => import('@/views/DocumentReaderView.vue') },
    { path: '/chat', name: 'chat', component: () => import('@/views/ChatView.vue') },
    { path: '/baguwen', name: 'baguwen', component: () => import('@/views/BaguwenView.vue') },
    { path: '/baguwen/:id', name: 'baguwen-detail', component: () => import('@/views/BaguwenDetailView.vue') },
    { path: '/news', name: 'news', component: () => import('@/views/DailyNewsView.vue') },
    { path: '/news', name: 'news', component: () => import('@/views/DailyNewsView.vue') },
  ],
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  if (!userStore.initialized) await userStore.fetchMe()
  if (to.name !== 'login' && !userStore.user) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && userStore.user) return { name: 'dashboard' }
})

export default router
