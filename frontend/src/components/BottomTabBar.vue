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

// 详情子路径也保持对应 tab 高亮
function isActive(path: string) {
  return route.path === path || route.path.startsWith(path + '/')
}
</script>

<template>
  <nav
    class="fixed inset-x-0 bottom-0 z-40 border-t backdrop-blur-xl md:hidden"
    style="background: var(--nb-header-bg); border-color: var(--nb-border)"
  >
    <div class="grid h-16 grid-cols-5">
      <RouterLink
        v-for="t in tabs"
        :key="t.name"
        :to="t.path"
        class="tab-link flex flex-col items-center justify-center gap-1 text-[11px]"
        :class="{ 'tab-link--active': isActive(t.path) }"
      >
        <span class="tab-icon flex h-7 w-12 items-center justify-center rounded-full transition-all duration-200">
          <Icon :icon="t.icon" class="h-5 w-5" />
        </span>
        {{ t.label }}
      </RouterLink>
    </div>
  </nav>
</template>

<style scoped>
.tab-link {
  color: var(--nb-text-faint);
  transition: color 0.2s ease;
}
.tab-link .tab-icon {
  color: var(--nb-text-faint);
}
/* 激活态:品牌渐变胶囊包裹图标(移动端唯一视觉焦点) */
.tab-link.router-link-active,
.tab-link--active {
  color: var(--nb-accent-cyan);
  font-weight: 500;
}
.tab-link.router-link-active .tab-icon,
.tab-link--active .tab-icon {
  color: #fff;
  background: var(--nb-grad-brand);
  box-shadow: 0 4px 12px -4px color-mix(in srgb, var(--nb-accent-cyan) 60%, transparent);
}
@media (prefers-reduced-motion: reduce) {
  .tab-icon {
    transition: none;
  }
}
</style>
