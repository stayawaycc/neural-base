<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
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

/* ---- 数据解码入场:乱码 → 正常文字 ---- */
const HERO_TITLE = '知识中枢'
const displayTitle = ref(HERO_TITLE)
const SCRAMBLE_CHARS = 'アイウエオカキクケコ0123456789<>/\\|=+*#@$%&'
let decodeTimer: ReturnType<typeof setInterval> | null = null

function runDecode() {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    displayTitle.value = HERO_TITLE
    return
  }
  let frame = 0
  const total = 42
  decodeTimer = setInterval(() => {
    frame += 1
    displayTitle.value = HERO_TITLE.split('')
      .map((ch, i) => {
        const revealAt = Math.floor(total * (i / HERO_TITLE.length)) + 3
        if (frame >= revealAt) return ch
        return SCRAMBLE_CHARS[Math.floor(Math.random() * SCRAMBLE_CHARS.length)]
      })
      .join('')
    if (frame >= total) {
      displayTitle.value = HERO_TITLE
      if (decodeTimer) clearInterval(decodeTimer)
      decodeTimer = null
    }
  }, 45)
}

onMounted(async () => {
  runDecode()
  if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    gsap.from('.module-card', {
      y: 14,
      opacity: 0,
      stagger: 0.06,
      duration: 0.5,
      ease: 'power2.out',
      delay: 0.08,
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

onBeforeUnmount(() => {
  if (decodeTimer) clearInterval(decodeTimer)
})
</script>

<template>
  <div class="space-y-10">
    <!-- 欢迎区:终端启动横幅 -->
    <div>
      <div class="mb-2 flex items-center gap-2 text-[12px]" style="color: var(--nb-text-faint)">
        <Icon icon="mdi:hexagon-multiple-outline" class="h-3.5 w-3.5" />
        <span class="cy-mono">[SYS] NEURAL_BASE CORE v2.0 // SYSTEM ONLINE</span>
      </div>
      <h1
        class="cy-glitch text-[40px] font-bold leading-[1.05] tracking-[0.02em] md:text-[48px]"
        :data-text="HERO_TITLE"
        style="color: var(--nb-text-primary); font-family: var(--nb-font-mono)"
      >
        {{ displayTitle }}
      </h1>
      <p class="mt-3 flex items-center gap-2 text-[15px] leading-relaxed" style="color: var(--nb-text-secondary)">
        <span class="cy-led" style="color: var(--nb-accent)" />
        <span class="cy-mono">MISSION: 构建个人知识库 · 接入 AI 对话 · 自动整理八股文</span>
      </p>
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
        <div class="mb-5 flex items-center justify-between">
          <span class="cy-mono flex items-center gap-2 text-[12px]" style="color: var(--nb-text-secondary)">
            <Icon icon="mdi:message-text-outline" class="h-4 w-4" style="color: var(--nb-accent)" />
            // 最近会话 [LOG]
          </span>
          <RouterLink to="/chat" class="cy-mono text-[11px] font-medium hover:underline" style="color: var(--nb-accent)">全部 →</RouterLink>
        </div>
        <div v-if="loading" class="space-y-3">
          <n-skeleton text :repeat="3" />
        </div>
        <div v-else class="fade-in space-y-1">
          <RouterLink
            v-for="c in conversations"
            :key="c.id"
            :to="{ path: '/chat', query: { conv: c.id } }"
            class="nb-list-item block px-3 py-2.5"
          >
            <div class="truncate text-[14px]" style="color: var(--nb-text-primary)">{{ c.title }}</div>
            <div v-if="c.lastMessageContent" class="mt-0.5 truncate text-[12px]" style="color: var(--nb-text-secondary)">
              {{ c.lastMessageContent }}
            </div>
          </RouterLink>
          <div v-if="!conversations.length" class="flex flex-col items-center gap-2 py-10 text-center">
            <Icon icon="mdi:message-text-outline" class="h-6 w-6" style="color: var(--nb-text-faint)" />
            <span class="cy-mono text-[12px]" style="color: var(--nb-text-faint)">// NO LOGS — 去 AI 对话开启第一段对话</span>
          </div>
        </div>
      </div>

      <div class="glass-card glass-card--padded">
        <div class="mb-5 flex items-center justify-between">
          <span class="cy-mono flex items-center gap-2 text-[12px]" style="color: var(--nb-text-secondary)">
            <Icon icon="mdi:file-document-outline" class="h-4 w-4" style="color: var(--nb-alert)" />
            // 最近文档 [FILES]
          </span>
          <RouterLink to="/kb" class="cy-mono text-[11px] font-medium hover:underline" style="color: var(--nb-accent)">全部 →</RouterLink>
        </div>
        <div v-if="loading" class="space-y-3">
          <n-skeleton text :repeat="3" />
        </div>
        <div v-else class="fade-in space-y-1">
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
                class="h-4 w-4 shrink-0"
                :style="{ color: d.status === 'READY' ? 'var(--nb-accent)' : 'var(--nb-text-faint)' }"
              />
              <span class="truncate text-[14px]" style="color: var(--nb-text-primary)">{{ d.title }}</span>
            </span>
            <span class="cy-mono shrink-0 text-[10px]" :style="{ color: d.status === 'READY' ? 'var(--nb-accent)' : 'var(--nb-alert)' }">
              [{{ d.status }}]
            </span>
          </RouterLink>
          <div v-if="!documents.length" class="flex flex-col items-center gap-2 py-10 text-center">
            <Icon icon="mdi:file-document-outline" class="h-6 w-6" style="color: var(--nb-text-faint)" />
            <span class="cy-mono text-[12px]" style="color: var(--nb-text-faint)">// NO FILES — 去知识库上传第一份资料</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
