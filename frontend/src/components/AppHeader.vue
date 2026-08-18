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
  { name: 'dashboard', label: '首页', prefix: '//', path: '/' },
  { name: 'kb', label: '知识库', prefix: '[KB]', path: '/kb' },
  { name: 'chat', label: 'AI 对话', prefix: '[SYNC]', path: '/chat' },
  { name: 'baguwen', label: '八股文', prefix: '[EXAM]', path: '/baguwen' },
  { name: 'news', label: 'AI 日报', prefix: '[FEED]', path: '/news' },
]

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
    class="app-header fixed inset-x-0 top-0 z-40"
    style="background: var(--nb-header-bg); border-color: var(--nb-border)"
  >
    <div class="mx-auto flex h-14 max-w-[1200px] items-center gap-2 px-6">
      <RouterLink to="/" class="flex shrink-0 items-center gap-2.5">
        <span class="nb-brand-badge h-8 w-8">
          <Icon icon="mdi:brain" class="h-4 w-4" />
        </span>
        <span class="text-[15px] font-semibold tracking-[0.08em] text-gray-900 dark:text-gray-100">
          NeuralBase
        </span>
      </RouterLink>
      <nav class="ml-8 hidden items-center gap-0.5 md:flex">
        <RouterLink
          v-for="n in navs"
          :key="n.name"
          :to="n.path"
          class="nav-link relative rounded-none px-3 py-1.5 text-[12px] font-medium transition-colors"
          :class="{ 'nav-link--active': isActive(n.path) }"
        >
          <span class="nav-link__prefix">{{ n.prefix }}</span>
          {{ n.label }}
        </RouterLink>
      </nav>
      <div class="ml-auto flex items-center gap-1">
        <button
          class="theme-toggle btn-press flex h-8 w-8 items-center justify-center rounded-none text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-700 dark:text-gray-400 dark:hover:bg-white/5 dark:hover:text-gray-200"
          title="切换主题"
          @click="themeStore.toggleTheme()"
        >
          <Icon v-if="themeStore.isDark" icon="mdi:white-balance-sunny" class="h-4 w-4" />
          <Icon v-else icon="mdi:weather-night" class="h-4 w-4" />
        </button>
        <n-dropdown :options="userOptions" trigger="click" @select="onUserSelect">
          <button
            class="flex items-center gap-2 rounded-none px-2 py-1.5 text-[13px] text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/5"
          >
            <span
              class="flex h-6 w-6 items-center justify-center rounded-none text-[11px] font-semibold text-white"
              style="background: var(--nb-accent)"
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
.app-header {
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-bottom: 1px solid var(--nb-border);
}
.nav-link {
  font-family: var(--nb-font-mono);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--nb-text-secondary);
}
.nav-link__prefix {
  opacity: 0;
  transition: opacity 0.15s ease;
}
.nav-link:hover {
  color: var(--nb-text-primary);
  background: var(--nb-nav-active-bg);
}
.nav-link--active {
  color: var(--nb-accent);
  text-shadow: 0 0 8px color-mix(in srgb, var(--nb-accent) 50%, transparent);
}
.nav-link--active .nav-link__prefix {
  opacity: 1;
}
.nav-link--active::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: 0;
  width: 100%;
  height: 2px;
  background: var(--nb-accent);
  box-shadow: 0 0 8px var(--nb-accent);
}
</style>
