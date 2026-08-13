<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { NDropdown } from 'naive-ui'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()

const navs = [
  { name: 'dashboard', label: '首页', path: '/' },
  { name: 'kb', label: '知识库', path: '/kb' },
  { name: 'chat', label: 'AI 对话', path: '/chat' },
  { name: 'baguwen', label: '八股文', path: '/baguwen' },
  { name: 'news', label: 'AI 日报', path: '/news' },
]

const initial = computed(() => (userStore.user?.username ?? '?').charAt(0).toUpperCase())

const userOptions = [{ label: '退出登录', key: 'logout' }]

async function onUserSelect(key: string) {
  if (key === 'logout') {
    await userStore.logout()
    void router.push('/login')
  }
}
</script>

<template>
  <header
    class="fixed inset-x-0 top-0 z-40 h-16 border-b backdrop-blur-xl"
    style="background: rgba(10, 10, 15, 0.72); border-color: rgba(255, 255, 255, 0.05)"
  >
    <div class="mx-auto flex h-full max-w-7xl items-center gap-2 px-4">
      <RouterLink to="/" class="flex shrink-0 items-center gap-2">
        <span class="h-2.5 w-2.5 rounded-full bg-gradient-to-r from-cyan-400 to-purple-400" />
        <span class="text-sm font-semibold tracking-wide text-gray-100">NeuralBase</span>
      </RouterLink>
      <nav class="ml-6 hidden items-center gap-1 md:flex">
        <RouterLink
          v-for="n in navs"
          :key="n.name"
          :to="n.path"
          class="nav-link rounded-lg px-3 py-1.5 text-sm transition-colors"
        >
          {{ n.label }}
        </RouterLink>
      </nav>
      <div class="ml-auto">
        <n-dropdown :options="userOptions" trigger="click" @select="onUserSelect">
          <button
            class="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-gray-300 transition-colors hover:bg-white/5"
          >
            <span
              class="flex h-6 w-6 items-center justify-center rounded-full bg-gradient-to-br from-cyan-400 to-purple-400 text-xs font-semibold text-black"
            >
              {{ initial }}
            </span>
            <span class="hidden sm:inline">{{ userStore.user?.username }}</span>
          </button>
        </n-dropdown>
      </div>
    </div>
  </header>
</template>

<style scoped>
.nav-link {
  color: #9ca3af;
}
.nav-link:hover {
  color: #e5e7eb;
}
.nav-link.router-link-active {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.05);
}
</style>
