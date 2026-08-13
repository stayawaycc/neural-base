<script setup lang="ts">
import { Icon } from '@iconify/vue'

withDefaults(
  defineProps<{
    title: string
    desc: string
    icon: string
    accent: 'cyan' | 'violet' | 'amber' | 'emerald'
    to: string
  }>(),
  { accent: 'cyan' },
)
</script>

<template>
  <RouterLink :to="to" class="block">
    <div class="glass-card glass-card--hover h-full" :class="`glass-card--${accent}`">
      <div class="mb-3 flex h-10 w-10 items-center justify-center rounded-xl border border-white/10 bg-white/[0.03]">
        <Icon :icon="icon" class="h-5 w-5" :style="{ color: `var(--nb-accent)` }" />
      </div>
      <div class="text-sm font-medium text-gray-100">{{ title }}</div>
      <div class="mt-1 text-xs leading-relaxed text-gray-500">{{ desc }}</div>
    </div>
  </RouterLink>
</template>
