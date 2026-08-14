<script setup lang="ts">
import { Icon } from '@iconify/vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import type { RagSource } from '@/types'

const props = defineProps<{
  role: 'USER' | 'ASSISTANT'
  content: string
  sources?: RagSource[]
  streaming?: boolean
  feedback?: 'LIKE' | 'DISLIKE' | null
}>()

const emit = defineEmits<{ feedback: ['LIKE' | 'DISLIKE' | null] }>()

function onFeedback(type: 'LIKE' | 'DISLIKE') {
  emit('feedback', props.feedback === type ? null : type)
}
</script>

<template>
  <div class="flex" :class="role === 'USER' ? 'justify-end' : 'justify-start'">
    <!-- 用户消息:右侧渐变气泡 -->
    <div v-if="role === 'USER'" class="max-w-[88%] md:max-w-[75%]">
      <div
        class="whitespace-pre-wrap rounded-2xl rounded-br-sm border border-gray-200 bg-gradient-to-br from-cyan-500/10 to-purple-500/10 px-4 py-2.5 text-[15px] leading-relaxed text-gray-900 shadow-sm dark:border-white/10 dark:from-cyan-500/15 dark:to-purple-500/15 dark:text-gray-100"
      >
        {{ content }}
      </div>
    </div>

    <!-- AI 消息:品牌头像 + 内容 -->
    <div v-else class="flex w-full items-start gap-3 md:max-w-[85%]">
      <span class="nb-brand-badge mt-1 h-8 w-8 shrink-0">
        <Icon icon="mdi:brain" class="h-4 w-4 text-white" />
      </span>
      <div class="min-w-0 flex-1 space-y-3">
        <div v-if="sources?.length" class="glass-card space-y-2 !p-3">
          <div class="text-xs text-gray-500">📚 参考来源</div>
          <div
            v-for="(s, i) in sources"
            :key="i"
            class="rounded-lg border border-gray-100 bg-gray-50 px-3 py-2 dark:border-white/5 dark:bg-white/[0.02]"
          >
            <div class="text-xs text-gray-700 dark:text-gray-300">{{ s.title }}</div>
            <div class="mt-1 line-clamp-2 text-xs text-gray-500">{{ s.content }}</div>
          </div>
        </div>
        <div class="text-[15px] leading-relaxed text-gray-900 dark:text-gray-100">
          <MarkdownRenderer :content="content" />
          <span v-if="streaming" class="nb-cursor" />
        </div>
        <div v-if="role === 'ASSISTANT' && !streaming" class="flex items-center gap-2">
          <button
            class="fb-like btn-press rounded p-1 transition-colors"
            :class="feedback === 'LIKE' ? 'fb-active text-cyan-600 dark:text-cyan-300' : 'text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300'"
            title="赞"
            @click="onFeedback('LIKE')"
          >
            <Icon :icon="feedback === 'LIKE' ? 'mdi:thumb-up' : 'mdi:thumb-up-outline'" class="h-4 w-4" />
          </button>
          <button
            class="fb-dislike btn-press rounded p-1 transition-colors"
            :class="feedback === 'DISLIKE' ? 'fb-active text-red-500 dark:text-red-300' : 'text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300'"
            title="踩"
            @click="onFeedback('DISLIKE')"
          >
            <Icon :icon="feedback === 'DISLIKE' ? 'mdi:thumb-down' : 'mdi:thumb-down-outline'" class="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
