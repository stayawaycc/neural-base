<script setup lang="ts">
import { Icon } from '@iconify/vue'
import GlassCard from '@/components/GlassCard.vue'

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
    <GlassCard :accent="accent" hoverable class="h-full">
      <div class="mb-3 flex h-10 w-10 items-center justify-center rounded-xl border border-gray-200 bg-gray-50 dark:border-white/10 dark:bg-white/[0.03]">
        <Icon :icon="icon" class="h-5 w-5" :style="{ color: `var(--nb-accent)` }" />
      </div>
      <div class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ title }}</div>
      <div class="mt-1 text-xs leading-relaxed text-gray-500">{{ desc }}</div>
    </GlassCard>
  </RouterLink>
</template>
