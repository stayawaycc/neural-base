<script setup lang="ts">
import { Icon } from '@iconify/vue'

const tabs = [
  { name: 'dashboard', label: '首页', icon: 'mdi:view-dashboard-outline', path: '/' },
  { name: 'kb', label: '知识库', icon: 'mdi:bookshelf', path: '/kb' },
  { name: 'chat', label: '对话', icon: 'mdi:message-text-outline', path: '/chat' },
  { name: 'baguwen', label: '八股文', icon: 'mdi:file-document-multiple-outline', path: '/baguwen' },
  { name: 'news', label: '日报', icon: 'mdi:newspaper-variant-outline', path: '/news' },
]
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
        class="tab-link flex flex-col items-center justify-center gap-0.5 text-[11px]"
      >
        <Icon :icon="t.icon" class="h-5 w-5" />
        {{ t.label }}
      </RouterLink>
    </div>
  </nav>
</template>

<style scoped>
.tab-link {
  color: var(--nb-text-faint);
}
.tab-link.router-link-active {
  color: #22d3ee;
}
</style>
