<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NSelect, NTag } from 'naive-ui'
import { useDocumentStore } from '@/stores/document'
import type { DocumentStatus } from '@/types'

const route = useRoute()
const router = useRouter()
const docStore = useDocumentStore()

const docId = computed(() => (route.params.docId ? Number(route.params.docId) : null))
const currentDoc = computed(() => docStore.documents.find((d) => d.id === docId.value) ?? null)
const kbOptions = computed(() => docStore.kbs.map((k) => ({ label: k.name, value: k.id })))

const statusMeta: Record<DocumentStatus, { type: 'default' | 'info' | 'success' | 'error'; label: string }> = {
  PENDING: { type: 'default', label: '排队中' },
  PROCESSING: { type: 'info', label: '处理中' },
  READY: { type: 'success', label: '就绪' },
  FAILED: { type: 'error', label: '失败' },
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
})

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
        <RouterLink :to="{ name: 'kb' }" class="text-xs text-gray-500 hover:text-cyan-300">← 返回知识库</RouterLink>
        <span class="truncate text-base text-gray-100">{{ currentDoc?.title ?? '文档阅读' }}</span>
        <n-tag v-if="currentDoc" size="small" :bordered="false">
          {{ currentDoc.fileType.toUpperCase() }}<template v-if="currentDoc.chunkCount"> · {{ currentDoc.chunkCount }} 块</template>
        </n-tag>
      </header>
      <!-- 内容区:Task 8 填充 -->
      <div class="flex-1 overflow-y-auto">
        <p class="py-20 text-center text-sm text-gray-600">内容渲染将在下一步接入</p>
      </div>
    </main>
  </div>
</template>
