<script setup lang="ts">
import { Icon } from '@iconify/vue'
</script>

<template>
  <div class="glass-card flex min-h-[40vh] flex-col items-center justify-center gap-4 text-center">
    <div class="flex h-14 w-14 items-center justify-center rounded-2xl border border-emerald-500/20 bg-emerald-50 dark:border-emerald-400/20 dark:bg-emerald-500/10">
      <Icon icon="mdi:newspaper-variant-outline" class="h-7 w-7 text-emerald-500 dark:text-emerald-400" />
    </div>
    <div>
      <h1 class="text-base font-medium text-gray-900 dark:text-gray-100">AI 技术日报</h1>
      <p class="mt-2 max-w-sm text-xs leading-relaxed text-gray-500">
        每日定时爬取 GitHub Trending、Hacker News、机器之心等前沿资讯,由 DeepSeek 自动分类汇总。
      </p>
      <p class="mt-3 text-xs text-gray-400 dark:text-gray-600">后端开发中(Phase 5),就绪后此处将展示时间线与一键收藏。</p>
    </div>
  </div>
</template>
