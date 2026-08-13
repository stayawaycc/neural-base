<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NSkeleton } from 'naive-ui'
import * as conversationApi from '@/api/conversation'
import { listDocuments } from '@/api/document'
import ModuleCard from '@/components/ModuleCard.vue'
import type { ConversationView, DocumentItem } from '@/types'

const conversations = ref<ConversationView[]>([])
const documents = ref<DocumentItem[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    conversations.value = (await conversationApi.list()).slice(0, 5)
  } catch {
    conversations.value = []
  }
  try {
    documents.value = (await listDocuments()).slice(0, 5)
  } catch {
    documents.value = []
  }
  loading.value = false
})
</script>

<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-lg font-semibold text-gray-900 dark:text-gray-100">知识中枢</h1>
      <p class="mt-1 text-xs text-gray-500">上传文档构建知识库,与 AI 对话,自动整理八股文</p>
    </div>

    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <ModuleCard
        title="知识库"
        desc="多格式上传,向量检索,语义搜索"
        icon="mdi:bookshelf"
        accent="cyan"
        to="/kb"
      />
      <ModuleCard
        title="AI 对话"
        desc="流式输出,普通 / 知识库双模式"
        icon="mdi:message-text-outline"
        accent="violet"
        to="/chat"
      />
      <ModuleCard
        title="八股文库"
        desc="URL 导入,AI 结构化问答对"
        icon="mdi:file-document-multiple-outline"
        accent="amber"
        to="/baguwen"
      />
      <ModuleCard
        title="AI 日报"
        desc="定时爬取前沿资讯,自动汇总"
        icon="mdi:newspaper-variant-outline"
        accent="emerald"
        to="/news"
      />
    </div>

    <div class="grid grid-cols-1 gap-3 lg:grid-cols-2">
      <div class="glass-card">
        <div class="mb-3 flex items-center justify-between">
          <span class="text-sm text-gray-700 dark:text-gray-300">最近会话</span>
          <RouterLink to="/chat" class="text-xs text-cyan-600 hover:underline dark:text-cyan-400">全部 →</RouterLink>
        </div>
        <div v-if="loading" class="space-y-3">
          <n-skeleton text :repeat="3" />
        </div>
        <div v-else class="fade-in space-y-1.5">
          <div v-for="c in conversations" :key="c.id" class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-white/[0.02]">
            <div class="truncate text-sm text-gray-800 dark:text-gray-200">{{ c.title }}</div>
            <div v-if="c.lastMessageContent" class="mt-0.5 truncate text-xs text-gray-500">
              {{ c.lastMessageContent }}
            </div>
          </div>
          <div v-if="!conversations.length" class="py-6 text-center text-xs text-gray-400 dark:text-gray-600">
            暂无会话
          </div>
        </div>
      </div>

      <div class="glass-card">
        <div class="mb-3 flex items-center justify-between">
          <span class="text-sm text-gray-700 dark:text-gray-300">最近文档</span>
          <RouterLink to="/kb" class="text-xs text-cyan-600 hover:underline dark:text-cyan-400">全部 →</RouterLink>
        </div>
        <div v-if="loading" class="space-y-3">
          <n-skeleton text :repeat="3" />
        </div>
        <div v-else class="fade-in space-y-1.5">
          <div v-for="d in documents" :key="d.id" class="flex items-center justify-between gap-2 rounded-lg bg-gray-50 px-3 py-2 dark:bg-white/[0.02]">
            <span class="truncate text-sm text-gray-800 dark:text-gray-200">{{ d.title }}</span>
            <span class="shrink-0 text-xs text-gray-400 dark:text-gray-600">{{ d.status }}</span>
          </div>
          <div v-if="!documents.length" class="py-6 text-center text-xs text-gray-400 dark:text-gray-600">
            暂无文档
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
