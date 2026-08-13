<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NInput, NModal, NPopconfirm, NSkeleton, NTag } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { useDocumentStore } from '@/stores/document'
import { dialog, message } from '@/utils/feedback'
import FileUploader from '@/components/FileUploader.vue'
import type { DocumentItem, DocumentStatus } from '@/types'

const router = useRouter()
const docStore = useDocumentStore()

const kbModal = ref(false)
const kbForm = reactive({ name: '', description: '', color: '#22d3ee' })
const kbPalette = ['#22d3ee', '#a78bfa', '#fbbf24', '#34d399', '#60a5fa', '#f472b6', '#fb923c', '#6b7280']

const query = ref('')
const searching = ref(false)
const listLoading = ref(true)

const statusMeta: Record<DocumentStatus, { type: 'default' | 'info' | 'success' | 'error'; label: string }> = {
  PENDING: { type: 'default', label: '排队中' },
  PROCESSING: { type: 'info', label: '解析中' },
  READY: { type: 'success', label: '就绪' },
  FAILED: { type: 'error', label: '失败' },
}

const hasProcessing = computed(() =>
  docStore.documents.some((d) => d.status === 'PENDING' || d.status === 'PROCESSING'),
)

onMounted(async () => {
  await docStore.loadKbs()
  await docStore.loadDocuments()
  listLoading.value = false
  if (hasProcessing.value) docStore.startPolling()
})

onBeforeUnmount(() => docStore.stopPolling())

async function onCreateKb() {
  if (!kbForm.name.trim()) {
    message.warning('请输入知识库名称')
    return
  }
  await docStore.createKb({ ...kbForm })
  kbModal.value = false
  kbForm.name = ''
  kbForm.description = ''
  message.success('创建成功')
}

async function onSearch() {
  if (!query.value.trim()) return
  searching.value = true
  try {
    await docStore.search(query.value)
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    searching.value = false
  }
}

function confirmDeleteKb(id: number, name: string) {
  dialog.warning({
    title: '删除知识库',
    content: `确定删除「${name}」?文档列表不会自动删除。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: () => void docStore.removeKb(id),
  })
}

function openReader(d: DocumentItem) {
  // 优先用文档自身的 kbId(「全部文档」模式下也能进阅读器);无归属的文档不可读
  const kbId = d.kbId ?? docStore.currentKbId
  if (d.status !== 'READY' || kbId == null) return
  void router.push({ name: 'kb-reader', params: { kbId: String(kbId), docId: String(d.id) } })
}
</script>

<template>
  <div class="flex h-[calc(100vh-9.5rem)] gap-4">
    <!-- 知识库列表 -->
    <aside class="hidden w-64 shrink-0 flex-col md:flex">
      <div class="mb-3 flex items-center justify-between">
        <span class="text-sm text-gray-600 dark:text-gray-400">知识库</span>
        <n-button size="small" quaternary @click="kbModal = true">
          <template #icon><Icon icon="mdi:plus" class="h-4 w-4" /></template>
          新建
        </n-button>
      </div>
      <div class="flex-1 space-y-1.5 overflow-y-auto pr-1">
        <button
          class="glass-card w-full !p-3 text-left"
          :class="docStore.currentKbId === null ? 'glass-card--selected' : ''"
          @click="docStore.selectKb(null)"
        >
          <span class="text-sm text-gray-800 dark:text-gray-200">全部文档</span>
        </button>
        <button
          v-for="k in docStore.kbs"
          :key="k.id"
          class="glass-card w-full !p-3 text-left"
          :class="docStore.currentKbId === k.id ? 'glass-card--selected' : ''"
          @click="docStore.selectKb(k.id)"
        >
          <div class="flex items-center gap-2">
            <span v-if="k.color" class="h-2.5 w-2.5 rounded-full" :style="{ background: k.color }" />
            <span class="truncate text-sm text-gray-800 dark:text-gray-200">{{ k.name }}</span>
            <span
              class="ml-auto cursor-pointer text-gray-400 hover:text-red-400 dark:text-gray-600"
              title="删除知识库"
              @click.stop="confirmDeleteKb(k.id, k.name)"
            >
              <Icon icon="mdi:trash-can-outline" class="h-4 w-4" />
            </span>
          </div>
        </button>
        <div v-if="!docStore.kbs.length" class="py-8 text-center text-xs text-gray-400 dark:text-gray-600">
          暂无知识库,点击「新建」创建
        </div>
      </div>
    </aside>

    <!-- 主区 -->
    <div class="flex min-w-0 flex-1 flex-col gap-3 overflow-y-auto">
      <FileUploader />

      <div class="flex gap-2">
        <n-input v-model:value="query" placeholder="语义搜索:输入自然语言问题…" @keyup.enter="onSearch" />
        <n-button type="primary" :loading="searching" @click="onSearch">搜索</n-button>
      </div>

      <div v-if="docStore.searchResults" class="space-y-2">
        <div class="text-xs text-gray-500">
          搜索结果 {{ docStore.searchResults.length }} 条
          <n-button text size="tiny" @click="docStore.searchResults = null">清除</n-button>
        </div>
        <div
          v-for="r in docStore.searchResults"
          :key="`${r.docId}-${r.score}`"
          class="glass-card !p-3"
        >
          <div class="flex items-center justify-between">
            <span class="text-sm text-gray-800 dark:text-gray-200">{{ r.title ?? `文档#${r.docId}` }}</span>
            <span class="text-xs text-gray-400 dark:text-gray-600">相似度 {{ r.score.toFixed(3) }}</span>
          </div>
          <p class="mt-1 line-clamp-3 text-xs leading-relaxed text-gray-600 dark:text-gray-400">{{ r.content }}</p>
        </div>
      </div>

      <template v-else>
        <div v-if="hasProcessing" class="text-xs text-gray-500">
          有文档处理中,每 2 秒自动刷新…
        </div>
        <div v-if="listLoading" class="space-y-2">
          <div v-for="i in 3" :key="i" class="glass-card flex items-center gap-3 !p-3">
            <n-skeleton text style="width: 60%" />
            <n-skeleton text style="width: 20%" />
          </div>
        </div>
        <div v-else class="fade-in space-y-2">
          <div v-for="d in docStore.documents" :key="d.id" class="glass-card !p-3">
            <div class="flex items-center justify-between gap-2">
              <span
                class="truncate text-sm text-gray-800 dark:text-gray-200"
                :class="d.status === 'READY' && (d.kbId ?? docStore.currentKbId) != null ? 'cursor-pointer hover:text-cyan-600 dark:hover:text-cyan-300' : ''"
                @click="openReader(d)"
              >{{ d.title }}</span>
              <div class="flex shrink-0 items-center gap-2">
                <n-tag size="small" :type="statusMeta[d.status].type" :bordered="false">
                  {{ statusMeta[d.status].label }}<template v-if="d.chunkCount"> · {{ d.chunkCount }} 块</template>
                </n-tag>
                <n-popconfirm @positive-click="docStore.remove(d.id)">
                  <template #trigger>
                    <span class="cursor-pointer text-gray-600 hover:text-red-400">
                      <Icon icon="mdi:trash-can-outline" class="h-4 w-4" />
                    </span>
                  </template>
                  删除该文档?
                </n-popconfirm>
              </div>
            </div>
            <div v-if="d.status === 'FAILED' && d.error" class="mt-1 text-xs text-red-500 dark:text-red-400">
              {{ d.error }}
            </div>
          </div>
          <div v-if="!docStore.documents.length" class="py-12 text-center text-sm text-gray-400 dark:text-gray-600">
            还没有文档,上传一个开始构建知识库
          </div>
        </div>
      </template>
    </div>
  </div>

  <!-- 新建知识库弹窗 -->
  <n-modal v-model:show="kbModal" preset="dialog" title="新建知识库" positive-text="创建" negative-text="取消" @positive-click="onCreateKb">
    <div class="space-y-4 py-2">
      <n-input v-model:value="kbForm.name" placeholder="名称" />
      <n-input v-model:value="kbForm.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="描述(可选)" />
      <div>
        <div class="mb-2 text-xs text-gray-500">颜色</div>
        <div class="flex gap-2">
          <button
            v-for="c in kbPalette"
            :key="c"
            class="h-6 w-6 rounded-full border-2 transition-transform"
            :style="{ background: c, borderColor: kbForm.color === c ? '#fff' : 'transparent' }"
            @click="kbForm.color = c"
          />
        </div>
      </div>
    </div>
  </n-modal>
</template>
