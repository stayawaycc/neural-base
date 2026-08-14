<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NDropdown } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const themeStore = useThemeStore()

const navs = [
  { name: 'dashboard', label: '首页', path: '/' },
  { name: 'kb', label: '知识库', path: '/kb' },
  { name: 'chat', label: 'AI 对话', path: '/chat' },
  { name: 'baguwen', label: '八股文', path: '/baguwen' },
  { name: 'news', label: 'AI 日报', path: '/news' },
]

// 详情子路径(如 /baguwen/1、/kb/1/reader/3)也保持对应导航高亮
function isActive(path: string) {
  return route.path === path || route.path.startsWith(path + '/')
}

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
    style="background: var(--nb-header-bg); border-color: var(--nb-border)"
  >
    <div class="mx-auto flex h-full max-w-7xl items-center gap-2 px-4">
      <RouterLink to="/" class="flex shrink-0 items-center gap-2.5">
        <span class="nb-brand-badge h-8 w-8">
          <Icon icon="mdi:brain" class="h-5 w-5 text-white" />
        </span>
        <span class="text-[15px] font-semibold tracking-tight text-gray-900 dark:text-gray-100">NeuralBase</span>
      </RouterLink>
      <nav class="ml-6 hidden items-center gap-1 md:flex">
        <RouterLink
          v-for="n in navs"
          :key="n.name"
          :to="n.path"
          class="nav-link rounded-lg px-3 py-1.5 text-sm transition-colors"
          :class="{ 'nav-link--active': isActive(n.path) }"
        >
          {{ n.label }}
        </RouterLink>
      </nav>
      <div class="ml-auto flex items-center">
        <button
          class="theme-toggle btn-press mr-2 flex h-9 w-9 items-center justify-center rounded-lg text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-700 dark:text-gray-400 dark:hover:bg-white/5 dark:hover:text-gray-200"
          title="切换主题"
          @click="themeStore.toggleTheme()"
        >
          <Icon v-if="themeStore.isDark" icon="mdi:white-balance-sunny" class="h-5 w-5" />
          <Icon v-else icon="mdi:weather-night" class="h-5 w-5" />
        </button>
        <n-dropdown :options="userOptions" trigger="click" @select="onUserSelect">
          <button
            class="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/5"
          >
            <span
              class="flex h-6 w-6 items-center justify-center rounded-full bg-gradient-to-br from-cyan-400 to-purple-400 text-xs font-semibold text-white"
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
  color: var(--nb-text-secondary);
  position: relative;
}
.nav-link:hover {
  color: var(--nb-text-primary);
  background: var(--nb-nav-active-bg);
}
.nav-link.router-link-active,
.nav-link--active {
  color: var(--nb-text-primary);
  font-weight: 500;
}
/* 激活导航:品牌渐变下划线 */
.nav-link.router-link-active::after,
.nav-link--active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 1px;
  transform: translateX(-50%);
  width: 14px;
  height: 2px;
  border-radius: 2px;
  background: var(--nb-grad-brand);
}
</style>
