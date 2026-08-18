<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NDrawer, NDrawerContent, NSelect, NTag } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { useDocumentStore } from '@/stores/document'
import { getDocumentContent, documentFileUrl } from '@/api/document'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { buildOutline, computeActiveId } from '@/utils/outline'
import { message } from '@/utils/feedback'
import type { DocumentContent, DocumentStatus } from '@/types'

const route = useRoute()
const router = useRouter()
const docStore = useDocumentStore()

const docId = computed(() => (route.params.docId ? Number(route.params.docId) : null))
const currentDoc = computed(() => docStore.documents.find((d) => d.id === docId.value) ?? null)
const kbOptions = computed(() => docStore.kbs.map((k) => ({ label: k.name, value: k.id })))

const content = ref<DocumentContent | null>(null)
const loading = ref(false)
const loadError = ref('')

type RenderKind = 'markdown' | 'pdf' | 'text' | 'baguwen'
function renderKind(ft: string): RenderKind {
  const t = (ft ?? '').toLowerCase()
  if (t === 'md' || t === 'markdown') return 'markdown'
  if (t === 'pdf') return 'pdf'
  if (t === 'baguwen') return 'baguwen'
  return 'text'
}
const kind = computed<RenderKind | null>(() => (content.value ? renderKind(content.value.fileType) : null))
const hasOriginal = computed(
  () => !!currentDoc.value && (currentDoc.value.fileType ?? '').toLowerCase() !== 'baguwen',
)

// 大纲(仅 md):目录抽屉 + 滚动高亮
const showOutline = ref(false)
const activeId = ref<string | null>(null)
const readerPane = ref<HTMLElement>()
const outlineItems = computed(() =>
  kind.value === 'markdown' && content.value ? buildOutline(content.value.content) : [],
)
let rafPending = false

function headingsInPane(): HTMLElement[] {
  const pane = readerPane.value
  return pane ? Array.from(pane.querySelectorAll<HTMLElement>('h1,h2,h3')) : []
}

function updateActive() {
  rafPending = false
  const pane = readerPane.value
  if (!pane) return
  const paneRect = pane.getBoundingClientRect()
  const headings = headingsInPane().map((h) => ({
    id: h.id,
    top: h.getBoundingClientRect().top - paneRect.top,
  }))
  activeId.value = computeActiveId(headings, 120)
}

function onPaneScroll() {
  if (rafPending) return
  rafPending = true
  requestAnimationFrame(updateActive)
}

function jumpTo(id: string) {
  // 在 pane 内按 id 找标题(挂载测试中组件树不 attach 到 document,不能用 getElementById)
  headingsInPane().find((h) => h.id === id)?.scrollIntoView({ behavior: 'smooth' })
  showOutline.value = false
}

watch([content, kind], updateActive, { flush: 'post' }) // DOM 更新后再读标题位置

const statusMeta: Record<DocumentStatus, { type: 'default' | 'info' | 'success' | 'error'; label: string }> = {
  PENDING: { type: 'default', label: '排队中' },
  PROCESSING: { type: 'info', label: '处理中' },
  READY: { type: 'success', label: '就绪' },
  FAILED: { type: 'error', label: '失败' },
}

async function loadDocument() {
  if (docId.value == null) {
    content.value = null
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    content.value = await getDocumentContent(docId.value)
  } catch (e) {
    const msg = (e as Error).message
    loadError.value = msg
    content.value = null
    message.error(msg)
    if (msg.includes('不存在')) {
      await docStore.loadDocuments() // 刷新树,移除已删除的文档(规格 5.3)
      void router.replace({ name: 'kb-reader', params: { kbId: String(route.params.kbId) } })
    }
  } finally {
    loading.value = false
  }
}

// 校验当前 docId 属于当前知识库;不属于(跨库直链/已删除)则清空选择(规格 5.3)
function validateDoc() {
  if (docId.value != null && !docStore.documents.some((d) => d.id === docId.value)) {
    void router.replace({ name: 'kb-reader', params: { kbId: String(route.params.kbId) } })
  }
}

function openDoc(id: number) {
  router.replace({ name: 'kb-reader', params: { kbId: String(route.params.kbId), docId: String(id) } })
}

// 文档类型图标(侧栏列表项)
function docIcon(ft?: string): string {
  const t = (ft ?? '').toLowerCase()
  if (t === 'md' || t === 'markdown') return 'mdi:language-markdown-outline'
  if (t === 'pdf') return 'mdi:file-pdf-box'
  if (t === 'doc' || t === 'docx') return 'mdi:file-word-box'
  if (t === 'baguwen') return 'mdi:notebook-outline'
  return 'mdi:file-document-outline'
}

function switchKb(v: number) {
  docStore.selectKb(v)
  void router.replace({ name: 'kb-reader', params: { kbId: String(v) } })
}

onMounted(async () => {
  await docStore.loadKbs()
  await docStore.selectKb(Number(route.params.kbId))
  validateDoc()
  await loadDocument()
})

watch(docId, () => void loadDocument())
watch(
  () => route.params.kbId,
  async (v) => {
    if (v && Number(v) !== docStore.currentKbId) {
      await docStore.selectKb(Number(v))
      validateDoc()
    }
  },
)
</script>

<template>
  <div class="flex h-[calc(100vh-9.5rem)] gap-4">
    <!-- 左:两级树(知识库选择 + 文档列表) -->
    <aside class="hidden w-64 shrink-0 flex-col md:flex">
      <n-select :value="Number(route.params.kbId)" :options="kbOptions" placeholder="选择知识库" class="mb-3" @update:value="switchKb" />
      <div class="mb-2 text-xs text-gray-400 dark:text-gray-500">文档</div>
      <div class="flex-1 space-y-1.5 overflow-y-auto pr-1">
        <button
          v-for="d in docStore.documents"
          :key="d.id"
          class="doc-item btn-press glass-card w-full !p-3 text-left"
          :class="[
            currentDoc?.id === d.id ? 'glass-card--selected' : '',
            d.status !== 'READY' ? 'cursor-not-allowed opacity-50' : 'glass-card--hover',
          ]"
          :disabled="d.status !== 'READY'"
          @click="d.status === 'READY' && openDoc(d.id)"
        >
          <div class="flex items-center justify-between gap-2">
            <span class="flex min-w-0 items-center gap-2">
              <Icon :icon="docIcon(d.fileType)" class="h-4 w-4 shrink-0 text-gray-400 dark:text-gray-500" />
              <span class="truncate text-sm text-gray-800 dark:text-gray-200">{{ d.title }}</span>
            </span>
            <n-tag v-if="d.status !== 'READY'" size="small" :type="statusMeta[d.status].type" :bordered="false">
              {{ statusMeta[d.status].label }}
            </n-tag>
          </div>
        </button>
        <div v-if="!docStore.documents.length" class="py-8 text-center text-xs text-gray-400 dark:text-gray-600">
          这个知识库还没有文档,去「知识库」页上传
        </div>
      </div>
    </aside>

    <!-- 移动端:知识库/文档下拉(桌面端 aside 的等价) -->
    <div class="flex min-w-0 flex-1 flex-col md:hidden">
      <n-select :value="Number(route.params.kbId)" :options="kbOptions" placeholder="选择知识库" class="mb-2" @update:value="switchKb" />
      <n-select
        :value="docId ?? undefined"
        :options="docStore.documents.map((d) => ({ label: d.title, value: d.id, disabled: d.status !== 'READY' }))"
        placeholder="选择文档"
        @update:value="(v: number) => openDoc(v)"
      />
    </div>

    <!-- 右:阅读区 -->
    <main class="flex min-w-0 flex-1 flex-col gap-3">
      <header class="flex items-center gap-3 border-b border-gray-100 pb-3 dark:border-white/5">
        <RouterLink :to="{ name: 'kb' }" class="shrink-0 text-xs text-gray-500 hover:text-[var(--nb-accent)]">← 返回知识库</RouterLink>
        <span class="truncate text-base text-gray-900 dark:text-gray-100">{{ content?.title ?? currentDoc?.title ?? '文档阅读' }}</span>
        <n-tag v-if="currentDoc" size="small" :bordered="false">
          {{ currentDoc.fileType.toUpperCase() }}<template v-if="currentDoc.chunkCount"> · {{ currentDoc.chunkCount }} 块</template>
        </n-tag>
        <div class="ml-auto flex shrink-0 items-center gap-3">
          <a
            v-if="docId != null && hasOriginal"
            class="download-link text-xs text-gray-500 hover:text-[var(--nb-accent)]"
            :href="documentFileUrl(docId)"
            :download="currentDoc?.title"
          >下载原文</a>
          <a
            v-if="kind === 'baguwen' && currentDoc?.sourceUrl"
            class="text-xs text-gray-500 hover:text-[var(--nb-accent)]"
            :href="currentDoc.sourceUrl"
            target="_blank"
            rel="noopener"
          >查看原文</a>
        </div>
      </header>
      <div ref="readerPane" class="relative flex-1 overflow-y-auto" @scroll="onPaneScroll">
        <button
          v-if="kind === 'markdown'"
          class="outline-trigger btn-press absolute right-4 top-4 z-10 flex h-9 items-center gap-1.5 rounded-none border px-3 text-xs backdrop-blur hover:text-[var(--nb-accent)]"
          style="border-color: var(--nb-border); background: var(--nb-card-bg); color: var(--nb-text-secondary)"
          @click="showOutline = true"
        >
          <span class="text-sm leading-none">☰</span> <span class="cy-mono">目录</span>
        </button>
        <div v-if="loading" class="py-20 text-center text-sm text-gray-500">加载中…</div>
        <div v-else-if="loadError" class="py-20 text-center text-sm text-gray-500">
          <p style="color: var(--nb-alert)">{{ loadError }}</p>
          <p v-if="loadError.includes('内容缺失')" class="mt-2 text-gray-400 dark:text-gray-600">请删除该文档后重新上传。</p>
          <p v-else-if="loadError.includes('尚未处理')" class="mt-2 text-gray-400 dark:text-gray-600">处理完成后自动可用,稍后重新选择即可。</p>
        </div>
        <div v-else-if="!content" class="flex h-full flex-col items-center justify-center gap-4 py-20">
          <span class="nb-brand-badge h-12 w-12">
            <Icon icon="mdi:book-open-page-variant-outline" class="h-6 w-6 text-white" />
          </span>
          <div class="text-center">
            <p class="text-sm font-medium text-gray-700 dark:text-gray-300">从左侧选择一篇文档开始阅读</p>
            <p class="mt-1 text-xs text-gray-400 dark:text-gray-600">支持 Markdown / PDF / Word / TXT</p>
          </div>
        </div>
        <template v-else>
          <MarkdownRenderer v-if="kind === 'markdown'" :content="content.content" anchor-headings />
          <iframe v-else-if="kind === 'pdf'" :src="documentFileUrl(content.id)" class="h-full min-h-[60vh] w-full rounded-none border" style="border-color: var(--nb-border)" />
          <pre v-else class="whitespace-pre-wrap py-2 text-sm leading-relaxed" style="color: var(--nb-text-primary)">{{ content.content }}</pre>
        </template>
      </div>
    </main>

    <!-- 目录抽屉(md 文档) -->
    <n-drawer v-model:show="showOutline" :width="280" placement="right">
      <n-drawer-content title="目录" closable>
        <div class="space-y-1">
          <button
            v-for="o in outlineItems"
            :key="o.id"
            class="outline-item btn-press block w-full rounded px-2 py-1.5 text-left text-sm transition-colors"
            :class="[
              o.level === 2 ? 'pl-4' : o.level === 3 ? 'pl-8' : '',
              o.id === activeId
                ? 'text-[var(--nb-accent)]'
                : 'text-gray-600 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200',
            ]"
            :style="o.id === activeId ? { background: 'var(--nb-accent-soft)' } : undefined"
            @click="jumpTo(o.id)"
          >
            {{ o.text }}
          </button>
          <div v-if="!outlineItems.length" class="py-8 text-center text-xs text-gray-400 dark:text-gray-600">本文档没有标题</div>
        </div>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>
