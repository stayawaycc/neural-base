<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NSelect, NTag } from 'naive-ui'
import { useDocumentStore } from '@/stores/document'
import { getDocumentContent, documentFileUrl } from '@/api/document'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
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
const hasOriginal = computed(() => !!currentDoc.value && currentDoc.value.fileType !== 'BAGUWEN')

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
      void router.replace({ name: 'kb-reader', params: { kbId: String(route.params.kbId) } })
    }
  } finally {
    loading.value = false
  }
}

function openDoc(id: number) {
  router.replace({ name: 'kb-reader', params: { kbId: String(route.params.kbId), docId: String(id) } })
}

function switchKb(v: number) {
  docStore.selectKb(v)
  void router.replace({ name: 'kb-reader', params: { kbId: String(v) } })
}

onMounted(async () => {
  await docStore.loadKbs()
  docStore.selectKb(Number(route.params.kbId))
  await loadDocument()
})

watch(docId, () => void loadDocument())
watch(
  () => route.params.kbId,
  (v) => {
    if (v && Number(v) !== docStore.currentKbId) docStore.selectKb(Number(v))
  },
)
</script>

<template>
  <div class="flex h-[calc(100vh-9.5rem)] gap-4">
    <!-- 左:两级树(知识库选择 + 文档列表) -->
    <aside class="hidden w-64 shrink-0 flex-col md:flex">
      <n-select :value="Number(route.params.kbId)" :options="kbOptions" placeholder="选择知识库" class="mb-3" @update:value="switchKb" />
      <div class="flex-1 space-y-1.5 overflow-y-auto pr-1">
        <button
          v-for="d in docStore.documents"
          :key="d.id"
          class="doc-item glass-card w-full !p-3 text-left"
          :class="[
            currentDoc?.id === d.id ? '!border-cyan-400/40' : '',
            d.status !== 'READY' ? 'cursor-not-allowed opacity-50' : 'glass-card--hover',
          ]"
          :disabled="d.status !== 'READY'"
          @click="d.status === 'READY' && openDoc(d.id)"
        >
          <div class="flex items-center justify-between gap-2">
            <span class="truncate text-sm text-gray-200">{{ d.title }}</span>
            <n-tag v-if="d.status !== 'READY'" size="small" :type="statusMeta[d.status].type" :bordered="false">
              {{ statusMeta[d.status].label }}
            </n-tag>
          </div>
        </button>
        <div v-if="!docStore.documents.length" class="py-8 text-center text-xs text-gray-600">
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
      <header class="flex items-center gap-3 border-b border-white/5 pb-3">
        <RouterLink :to="{ name: 'kb' }" class="shrink-0 text-xs text-gray-500 hover:text-cyan-300">← 返回知识库</RouterLink>
        <span class="truncate text-base text-gray-100">{{ content?.title ?? currentDoc?.title ?? '文档阅读' }}</span>
        <n-tag v-if="currentDoc" size="small" :bordered="false">
          {{ currentDoc.fileType.toUpperCase() }}<template v-if="currentDoc.chunkCount"> · {{ currentDoc.chunkCount }} 块</template>
        </n-tag>
        <div class="ml-auto flex shrink-0 items-center gap-3">
          <a
            v-if="docId != null && hasOriginal"
            class="download-link text-xs text-gray-500 hover:text-cyan-300"
            :href="documentFileUrl(docId)"
            :download="currentDoc?.title"
          >下载原文</a>
          <a
            v-if="kind === 'baguwen' && currentDoc?.sourceUrl"
            class="text-xs text-gray-500 hover:text-cyan-300"
            :href="currentDoc.sourceUrl"
            target="_blank"
            rel="noopener"
          >查看原文</a>
        </div>
      </header>
      <div class="relative flex-1 overflow-y-auto">
        <div v-if="loading" class="py-20 text-center text-sm text-gray-500">加载中…</div>
        <div v-else-if="loadError" class="py-20 text-center text-sm text-gray-500">
          <p class="text-red-400">{{ loadError }}</p>
          <p v-if="loadError.includes('内容缺失')" class="mt-2 text-gray-600">请删除该文档后重新上传。</p>
          <p v-else-if="loadError.includes('尚未处理')" class="mt-2 text-gray-600">处理完成后自动可用,稍后重新选择即可。</p>
        </div>
        <div v-else-if="!content" class="py-20 text-center text-sm text-gray-600">从左侧选择一篇文档开始阅读</div>
        <template v-else>
          <MarkdownRenderer v-if="kind === 'markdown'" :content="content.content" anchor-headings />
          <iframe v-else-if="kind === 'pdf'" :src="documentFileUrl(content.id)" class="h-full min-h-[60vh] w-full rounded-xl border border-white/5" />
          <pre v-else class="whitespace-pre-wrap py-2 text-sm leading-relaxed text-gray-300">{{ content.content }}</pre>
        </template>
      </div>
    </main>
  </div>
</template>
