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

// 图标容器:暗色 HUD 数据帧 + 霓虹强调
const iconContainerStyle = {
  background: 'var(--nb-surface)',
  border: '1px solid color-mix(in srgb, var(--nb-accent) 35%, transparent)',
  boxShadow: '0 0 10px -4px var(--nb-accent)',
  clipPath: 'polygon(6px 0, 100% 0, 100% calc(100% - 6px), calc(100% - 6px) 100%, 0 100%, 0 6px)',
  borderRadius: '0',
}
</script>

<template>
  <RouterLink :to="to" class="module-card group block">
    <GlassCard :accent="accent" hoverable class="btn-press h-full">
      <div class="flex items-start justify-between">
        <div
          class="mb-4 flex h-11 w-11 items-center justify-center"
          :style="iconContainerStyle"
        >
          <Icon :icon="icon" class="h-5 w-5" style="color: var(--nb-accent)" />
        </div>
        <Icon
          icon="mdi:arrow-top-right"
          class="h-4 w-4 opacity-0 transition-all duration-200 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:opacity-100"
          style="color: var(--nb-accent)"
        />
      </div>
      <div class="cy-mono text-[13px] font-semibold tracking-[0.08em]" style="color: var(--nb-text-primary)">
        {{ title }}
      </div>
      <div class="mt-1.5 text-xs leading-relaxed" style="color: var(--nb-text-secondary)">{{ desc }}</div>
      <div class="mt-3 flex items-center gap-1.5 text-[11px]" style="color: var(--nb-accent)">
        <span class="cy-led" />
        <span class="cy-mono">ENTER &gt;&gt;</span>
      </div>
    </GlassCard>
  </RouterLink>
</template>
