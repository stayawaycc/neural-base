<script setup lang="ts">
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import type { RagSource } from '@/types'

defineProps<{
  role: 'USER' | 'ASSISTANT'
  content: string
  sources?: RagSource[]
  streaming?: boolean
}>()
</script>

<template>
  <div class="flex" :class="role === 'USER' ? 'justify-end' : 'justify-start'">
    <div class="max-w-[88%] md:max-w-[75%]">
      <div
        v-if="role === 'USER'"
        class="whitespace-pre-wrap rounded-2xl rounded-br-sm border border-white/10 bg-gradient-to-br from-cyan-500/15 to-purple-500/15 px-4 py-2.5 text-[15px] leading-relaxed text-gray-100"
      >
        {{ content }}
      </div>
      <div v-else class="space-y-3">
        <div v-if="sources?.length" class="glass-card space-y-2 !p-3">
          <div class="text-xs text-gray-500">📚 参考来源</div>
          <div
            v-for="(s, i) in sources"
            :key="i"
            class="rounded-lg border border-white/5 bg-white/[0.02] px-3 py-2"
          >
            <div class="text-xs text-gray-300">{{ s.title }}</div>
            <div class="mt-1 line-clamp-2 text-xs text-gray-500">{{ s.content }}</div>
          </div>
        </div>
        <div class="text-[15px] leading-relaxed text-gray-100">
          <MarkdownRenderer :content="content" />
          <span v-if="streaming" class="nb-cursor" />
        </div>
      </div>
    </div>
  </div>
</template>
