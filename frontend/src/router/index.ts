import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
    { path: '/kb', name: 'kb', component: () => import('@/views/KnowledgeBaseView.vue') },
    { path: '/chat', name: 'chat', component: () => import('@/views/ChatView.vue') },
    { path: '/baguwen', name: 'baguwen', component: () => import('@/views/BaguwenView.vue') },
    { path: '/baguwen/:id', name: 'baguwen-detail', component: () => import('@/views/BaguwenDetailView.vue') },
    { path: '/news', name: 'news', component: () => import('@/views/DailyNewsView.vue') },
  ],
})

export default router
