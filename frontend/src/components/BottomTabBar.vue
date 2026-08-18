<script setup lang="ts">
import { useRoute } from 'vue-router'
import { Icon } from '@iconify/vue'

const route = useRoute()

const tabs = [
  { name: 'dashboard', label: '首页', icon: 'mdi:view-dashboard-outline', path: '/' },
  { name: 'kb', label: '知识库', icon: 'mdi:bookshelf', path: '/kb' },
  { name: 'chat', label: '对话', icon: 'mdi:message-text-outline', path: '/chat' },
  { name: 'baguwen', label: '八股文', icon: 'mdi:file-document-multiple-outline', path: '/baguwen' },
  { name: 'news', label: '日报', icon: 'mdi:newspaper-variant-outline', path: '/news' },
]

function isActive(path: string) {
  return route.path === path || route.path.startsWith(path + '/')
}
</script>

<template>
  <nav
    class="tab-bar fixed inset-x-0 bottom-0 z-40 md:hidden"
    style="background: var(--nb-header-bg); border-color: var(--nb-border)"
  >
    <div class="grid h-14 grid-cols-5">
      <RouterLink
        v-for="t in tabs"
        :key="t.name"
        :to="t.path"
        class="tab-link relative flex flex-col items-center justify-center gap-0.5 text-[10px]"
        :class="{ 'tab-link--active': isActive(t.path) }"
      >
        <!-- 顶部霓虹指示灯 -->
        <span class="tab-led" />
        <span class="tab-icon flex h-6 w-9 items-center justify-center transition-all duration-200">
          <Icon :icon="t.icon" class="h-[18px] w-[18px]" />
        </span>
        <span class="cy-mono text-[9px]">{{ t.label }}</span>
      </RouterLink>
    </div>
  </nav>
</template>

<style scoped>
.tab-bar {
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-top: 1px solid var(--nb-border);
}
.tab-link {
  color: var(--nb-text-faint);
  transition: color 0.15s ease;
}
.tab-link .tab-icon {
  color: var(--nb-text-faint);
  border-radius: 0;
  border: 1px solid transparent;
}
.tab-link--active {
  color: var(--nb-accent);
  font-weight: 500;
}
.tab-link--active .tab-icon {
  color: #050508;
  background: var(--nb-accent);
  border-color: var(--nb-accent);
  clip-path: polygon(5px 0, 100% 0, 100% calc(100% - 5px), calc(100% - 5px) 100%, 0 100%, 0 5px);
  box-shadow: 0 0 12px -2px var(--nb-accent);
}
/* 顶部指示灯 */
.tab-led {
  position: absolute;
  top: 4px;
  width: 4px;
  height: 4px;
  background: transparent;
  transition: all 0.2s ease;
}
.tab-link--active .tab-led {
  background: var(--nb-accent);
  box-shadow: 0 0 6px var(--nb-accent);
  animation: tab-led-pulse 1.8s ease-in-out infinite;
}
@keyframes tab-led-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}
@media (prefers-reduced-motion: reduce) {
  .tab-icon {
    transition: none;
  }
  .tab-link--active .tab-led {
    animation: none;
  }
}
</style>
