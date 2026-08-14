<script setup lang="ts">
import { Icon } from '@iconify/vue'
import GlassCard from '@/components/GlassCard.vue'

const props = withDefaults(
  defineProps<{
    title: string
    desc: string
    icon: string
    accent: 'cyan' | 'violet' | 'amber' | 'emerald'
    to: string
  }>(),
  { accent: 'cyan' },
)

// 图标容器:强调色柔光底 + 同色描边,随卡片 accent 联动
function iconStyle(accent: string) {
  return {
    background: `color-mix(in srgb, var(--nb-accent-${accent}) 14%, transparent)`,
    boxShadow: `0 0 0 1px color-mix(in srgb, var(--nb-accent-${accent}) 22%, transparent), 0 6px 16px -6px color-mix(in srgb, var(--nb-accent-${accent}) 45%, transparent)`,
  }
}
</script>

<template>
  <RouterLink :to="to" class="module-card group block">
    <GlassCard :accent="accent" hoverable class="btn-press h-full">
      <div class="flex items-start justify-between">
        <div class="mb-4 flex h-11 w-11 items-center justify-center rounded-xl" :style="iconStyle(accent)">
          <Icon :icon="icon" class="h-5 w-5" :style="{ color: `var(--nb-accent)` }" />
        </div>
        <Icon
          icon="mdi:arrow-top-right"
          class="h-4 w-4 opacity-0 transition-all duration-200 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:opacity-100"
          :style="{ color: `var(--nb-accent)` }"
        />
      </div>
      <div class="text-[15px] font-semibold text-gray-900 dark:text-gray-100">{{ title }}</div>
      <div class="mt-1 text-xs leading-relaxed text-gray-500 dark:text-gray-400">{{ desc }}</div>
    </GlassCard>
  </RouterLink>
</template>
