<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NSkeleton } from 'naive-ui'
import { gsap } from 'gsap'
import { Icon } from '@iconify/vue'
import * as conversationApi from '@/api/conversation'
import { listDocuments } from '@/api/document'
import ModuleCard from '@/components/ModuleCard.vue'
import type { ConversationView, DocumentItem } from '@/types'

const conversations = ref<ConversationView[]>([])
const documents = ref<DocumentItem[]>([])
const loading = ref(true)

onMounted(async () => {
  // 模块卡 stagger 入场(尊重 reduced-motion)
  if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    gsap.from('.module-card', {
      y: 18,
      opacity: 0,
      stagger: 0.07,
      duration: 0.45,
      ease: 'power2.out',
      delay: 0.05,
    })
  }
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
  <div class="space-y-8">
    <!-- 欢迎区 -->
    <div>
      <div class="flex items-center gap-3">
        <span class="nb-dot h-2.5 w-2.5" />
        <h1 class="text-2xl font-semibold tracking-tight text-gray-900 dark:text-gray-100">知识中枢</h1>
      </div>
      <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">上传文档构建知识库,与 AI 对话,自动整理八股文</p>
    </div>

    <!-- 四大模块 -->
    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
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

    <!-- 最近动态 -->
    <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <div class="glass-card glass-card--padded">
        <div class="mb-4 flex items-center justify-between">
          <span class="flex items-center gap-2 text-sm font-medium text-gray-700 dark:text-gray-300">
            <Icon icon="mdi:message-text-outline" class="h-4 w-4 text-cyan-600 dark:text-cyan-400" />
            最近会话
          </span>
          <RouterLink to="/chat" class="text-xs text-cyan-600 hover:underline dark:text-cyan-400">全部 →</RouterLink>
        </div>
        <div v-if="loading" class="space-y-3">
          <n-skeleton text :repeat="3" />
        </div>
        <div v-else class="fade-in space-y-1.5">
          <RouterLink
            v-for="c in conversations"
            :key="c.id"
            :to="{ path: '/chat', query: { conv: c.id } }"
            class="nb-list-item block px-3 py-2.5"
          >
            <div class="truncate text-sm text-gray-800 dark:text-gray-200">{{ c.title }}</div>
            <div v-if="c.lastMessageContent" class="mt-0.5 truncate text-xs text-gray-500 dark:text-gray-400">
              {{ c.lastMessageContent }}
            </div>
          </RouterLink>
          <div v-if="!conversations.length" class="flex flex-col items-center gap-2 py-8 text-center">
            <Icon icon="mdi:message-text-outline" class="h-5 w-5 text-gray-300 dark:text-gray-600" />
            <span class="text-xs text-gray-400 dark:text-gray-600">暂无会话,去 AI 对话开启第一段对话</span>
          </div>
        </div>
      </div>

      <div class="glass-card glass-card--padded">
        <div class="mb-4 flex items-center justify-between">
          <span class="flex items-center gap-2 text-sm font-medium text-gray-700 dark:text-gray-300">
            <Icon icon="mdi:file-document-outline" class="h-4 w-4 text-violet-600 dark:text-violet-400" />
            最近文档
          </span>
          <RouterLink to="/kb" class="text-xs text-cyan-600 hover:underline dark:text-cyan-400">全部 →</RouterLink>
        </div>
        <div v-if="loading" class="space-y-3">
          <n-skeleton text :repeat="3" />
        </div>
        <div v-else class="fade-in space-y-1.5">
          <RouterLink
            v-for="d in documents"
            :key="d.id"
            :to="d.status === 'READY' && d.kbId != null
              ? { name: 'kb-reader', params: { kbId: String(d.kbId), docId: String(d.id) } }
              : { path: '/kb' }"
            class="nb-list-item flex items-center justify-between gap-2 px-3 py-2.5"
          >
            <span class="flex min-w-0 items-center gap-2">
              <Icon
                :icon="d.status === 'READY' ? 'mdi:file-document-outline' : 'mdi:clock-outline'"
                class="h-4 w-4 shrink-0 text-gray-400 dark:text-gray-500"
              />
              <span class="truncate text-sm text-gray-800 dark:text-gray-200">{{ d.title }}</span>
            </span>
            <span class="shrink-0 text-xs text-gray-400 dark:text-gray-500">{{ d.status }}</span>
          </RouterLink>
          <div v-if="!documents.length" class="flex flex-col items-center gap-2 py-8 text-center">
            <Icon icon="mdi:file-document-outline" class="h-5 w-5 text-gray-300 dark:text-gray-600" />
            <span class="text-xs text-gray-400 dark:text-gray-600">暂无文档,去知识库上传第一份资料</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
