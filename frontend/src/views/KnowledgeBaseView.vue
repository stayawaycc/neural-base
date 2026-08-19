<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NInput, NModal, NPopconfirm, NSkeleton } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { useDocumentStore } from '@/stores/document'
import { dialog, message } from '@/utils/feedback'
import FileUploader from '@/components/FileUploader.vue'
import type { DocumentItem, DocumentStatus } from '@/types'

const router = useRouter()
const docStore = useDocumentStore()

const kbModal = ref(false)
const kbForm = reactive({ name: '', description: '', color: '#00f0ff' })
const kbPalette = ['#00f0ff', '#ff6b00', '#ffffff', '#6b7280']

const query = ref('')
const searching = ref(false)
const listLoading = ref(true)

/* 状态收敛:进行中/就绪 → 电光蓝,失败 → 警告橙,排队 → 灰 */
const statusMeta: Record<DocumentStatus, { color: string; bg: string; label: string }> = {
  PENDING: { color: 'var(--nb-text-faint)', bg: 'rgba(255,255,255,0.05)', label: '排队中' },
  PROCESSING: { color: 'var(--nb-accent)', bg: 'var(--nb-accent-soft)', label: '解析中' },
  READY: { color: 'var(--nb-accent)', bg: 'var(--nb-accent-soft)', label: '就绪' },
  FAILED: { color: 'var(--nb-alert)', bg: 'var(--nb-alert-soft)', label: '失败' },
}

/* 文件类型:统一去饱和,仅灰白 */
const fileTypeMeta: Record<string, { color: string; bg: string; icon: string }> = {
  pdf: { color: 'var(--nb-text-secondary)', bg: 'var(--nb-surface)', icon: 'mdi:file-pdf-box' },
  md: { color: 'var(--nb-text-secondary)', bg: 'var(--nb-surface)', icon: 'mdi:language-markdown-outline' },
  markdown: { color: 'var(--nb-text-secondary)', bg: 'var(--nb-surface)', icon: 'mdi:language-markdown-outline' },
  doc: { color: 'var(--nb-text-secondary)', bg: 'var(--nb-surface)', icon: 'mdi:file-word-box' },
  docx: { color: 'var(--nb-text-secondary)', bg: 'var(--nb-surface)', icon: 'mdi:file-word-box' },
  baguwen: { color: 'var(--nb-text-secondary)', bg: 'var(--nb-surface)', icon: 'mdi:notebook-outline' },
}

const hasProcessing = computed(() =>
  docStore.documents.some((d) => d.status === 'PENDING' || d.status === 'PROCESSING'),
)

onMounted(async () => {
  try {
    await docStore.loadKbs()
    await docStore.loadDocuments()
  } finally {
    listLoading.value = false
  }
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
    onPositiveClick: async () => {
      try {
        await docStore.removeKb(id)
        message.success('已删除')
      } catch (e) {
        message.error((e as Error).message)
      }
    },
  })
}

async function onRemoveDoc(id: number) {
  try {
    await docStore.remove(id)
    message.success('已删除')
  } catch (e) {
    message.error((e as Error).message)
  }
}

function openReader(d: DocumentItem) {
  const kbId = d.kbId ?? docStore.currentKbId
  if (d.status !== 'READY' || kbId == null) return
  void router.push({ name: 'kb-reader', params: { kbId: String(kbId), docId: String(d.id) } })
}

function getFileMeta(ft?: string) {
  const t = (ft ?? '').toLowerCase()
  return fileTypeMeta[t] ?? { color: 'var(--nb-text-secondary)', bg: 'var(--nb-surface)', icon: 'mdi:file-document-outline' }
}
</script>

<template>
  <div class="flex h-[calc(100vh-9.5rem)] flex-col gap-5">
    <!-- 页面头部 -->
    <div class="flex items-center gap-3">
      <span class="nb-brand-badge h-10 w-10">
        <Icon icon="mdi:bookshelf" class="h-5 w-5" />
      </span>
      <div>
        <h1 class="cy-mono text-xl font-semibold tracking-[0.08em]" style="color: var(--nb-text-primary)">知识库 [KB]</h1>
        <p class="mt-0.5 text-xs" style="color: var(--nb-text-secondary)">多格式上传,自动切片入库,语义检索</p>
      </div>
      <div class="ml-auto hidden items-center gap-3 sm:flex">
        <span class="kb-stat">
          <Icon icon="mdi:database-outline" class="h-3.5 w-3.5" />
          <span class="cy-mono text-[10px]">DB // {{ docStore.kbs.length }}</span>
        </span>
        <span class="kb-stat">
          <Icon icon="mdi:file-document-outline" class="h-3.5 w-3.5" />
          <span class="cy-mono text-[10px]">FILES // {{ docStore.documents.length }}</span>
        </span>
      </div>
    </div>

    <div class="flex min-h-0 flex-1 gap-5">
    <!-- 知识库侧栏 -->
    <aside class="hidden w-60 shrink-0 flex-col md:flex">
      <div class="mb-3 flex items-center justify-between">
        <span class="cy-mono text-[11px] tracking-wider" style="color: var(--nb-text-faint)">// 知识库</span>
        <button class="kb-new-btn" @click="kbModal = true">
          <Icon icon="mdi:plus" class="h-3.5 w-3.5" />
          新建
        </button>
      </div>
      <div class="flex-1 space-y-1 overflow-y-auto pr-1">
        <!-- 全部文档 -->
        <button
          class="kb-item"
          :class="docStore.currentKbId === null ? 'kb-item--active' : ''"
          @click="docStore.selectKb(null)"
        >
          <span
            v-if="docStore.currentKbId === null"
            class="kb-item__bar"
            style="background: var(--nb-accent)"
          />
          <Icon icon="mdi:folder-outline" class="h-4 w-4 shrink-0" />
          <span class="truncate text-sm">全部文档</span>
          <span class="ml-auto text-xs text-gray-400 dark:text-gray-500">{{ docStore.documents.length }}</span>
        </button>
        <!-- 各知识库 -->
        <button
          v-for="k in docStore.kbs"
          :key="k.id"
          class="kb-item"
          :class="docStore.currentKbId === k.id ? 'kb-item--active' : ''"
          @click="docStore.selectKb(k.id)"
        >
          <span
            v-if="docStore.currentKbId === k.id"
            class="kb-item__bar"
            :style="{ background: k.color || 'var(--nb-accent-cyan)' }"
          />
          <span v-if="k.color" class="nb-dot h-2.5 w-2.5" :style="{ background: k.color, boxShadow: `0 0 6px ${k.color}` }" />
          <span v-else class="nb-dot h-2.5 w-2.5" />
          <span class="truncate text-sm">{{ k.name }}</span>
          <span
            class="ml-auto shrink-0 cursor-pointer rounded-none p-0.5 text-gray-400 transition-all hover:text-[var(--nb-alert)] group-hover:opacity-100 dark:text-gray-600"
            style="opacity: 0.55"
            title="删除知识库"
            @click.stop="confirmDeleteKb(k.id, k.name)"
          >
            <Icon icon="mdi:trash-can-outline" class="h-3.5 w-3.5" />
          </span>
        </button>
        <div v-if="!docStore.kbs.length" class="py-8 text-center text-xs text-gray-400 dark:text-gray-600">
          暂无知识库,点击「新建」创建
        </div>
      </div>
    </aside>

    <!-- 主区 -->
    <div class="flex min-w-0 flex-1 flex-col gap-4 overflow-y-auto">
      <FileUploader />

      <!-- 搜索栏 -->
      <div class="kb-search">
        <Icon icon="mdi:magnify" class="h-4 w-4 shrink-0" style="color: var(--nb-text-faint)" />
        <input
          v-model="query"
          class="kb-search__input"
          placeholder="语义搜索:输入自然语言问题…"
          @keyup.enter="onSearch"
        />
        <button class="kb-search__btn" :disabled="searching || !query.trim()" @click="onSearch">
          <Icon v-if="searching" icon="mdi:loading" class="h-4 w-4 animate-spin" />
          <Icon v-else icon="mdi:magnify" class="h-4 w-4" />
          <span class="cy-mono">搜索</span>
        </button>
      </div>

      <!-- 搜索结果 -->
      <div v-if="docStore.searchResults" class="space-y-2">
        <div class="cy-mono flex items-center justify-between text-[11px]" style="color: var(--nb-text-secondary)">
          <span>// FOUND {{ docStore.searchResults.length }} ITEMS</span>
          <button class="hover:underline" style="color: var(--nb-accent)" @click="docStore.searchResults = null">清除</button>
        </div>
        <div
          v-for="r in docStore.searchResults"
          :key="`${r.docId}-${r.score}`"
          class="doc-card doc-card--search"
        >
          <div class="flex items-center justify-between">
            <div class="flex min-w-0 items-center gap-2">
              <span class="truncate text-sm font-medium" style="color: var(--nb-text-primary)">{{ r.title ?? `文档#${r.docId}` }}</span>
              <span v-if="r.kbName" class="cy-mono shrink-0 border border-[var(--nb-border)] px-1.5 py-0.5 text-[10px]" style="color: var(--nb-text-secondary)">{{ r.kbName }}</span>
            </div>
            <span class="cy-mono shrink-0 border border-[var(--nb-border)] px-2 py-0.5 text-[10px]" style="color: var(--nb-accent)">
              0x{{ r.score.toFixed(3) }}
            </span>
          </div>
          <p class="mt-1.5 line-clamp-2 text-xs leading-relaxed" style="color: var(--nb-text-secondary)">{{ r.content }}</p>
        </div>
      </div>

      <!-- 文档列表 -->
      <template v-else>
        <div v-if="hasProcessing" class="cy-mono flex items-center gap-2 text-[11px]" style="color: var(--nb-text-secondary)">
          <span class="cy-led" style="color: var(--nb-accent)" />
          // PROCESSING — 每 2 秒自动刷新
        </div>
        <div v-if="listLoading" class="space-y-2">
          <div v-for="i in 3" :key="i" class="doc-card">
            <div class="h-9 w-9" style="background: var(--nb-surface); border: 1px solid var(--nb-border)" />
            <n-skeleton text style="width: 50%" />
          </div>
          <n-skeleton text style="width: 56px; height: 20px" />
        </div>
        <div v-else class="fade-in space-y-2">
          <div
            v-for="d in docStore.documents"
            :key="d.id"
            class="doc-card group"
            :class="d.status === 'READY' && (d.kbId ?? docStore.currentKbId) != null ? 'cursor-pointer' : ''"
            @click="openReader(d)"
          >
            <!-- 文件类型图标 -->
            <div
              class="doc-card__icon"
              :style="{ background: getFileMeta(d.fileType).bg, color: getFileMeta(d.fileType).color }"
            >
              <Icon :icon="getFileMeta(d.fileType).icon" class="h-4 w-4" />
            </div>
            <!-- 文件信息 -->
            <div class="min-w-0 flex-1">
              <div
                class="truncate text-sm font-medium"
                style="color: var(--nb-text-primary)"
              >
                {{ d.title }}
              </div>
              <div v-if="d.status === 'FAILED' && d.error" class="mt-0.5 truncate text-xs" style="color: var(--nb-alert)">
                {{ d.error }}
              </div>
            </div>
            <!-- 状态 + 操作 -->
            <div class="flex shrink-0 items-center gap-2">
              <span
                class="doc-card__badge"
                :style="{ color: statusMeta[d.status].color, borderColor: statusMeta[d.status].color }"
              >
                {{ statusMeta[d.status].label }}
                <template v-if="d.chunkCount"> · {{ d.chunkCount }}</template>
              </span>
              <n-popconfirm @positive-click="onRemoveDoc(d.id)">
                <template #trigger>
                  <span class="cursor-pointer rounded-none p-1 text-gray-400 transition-all hover:text-[var(--nb-alert)] group-hover:opacity-100 dark:text-gray-600" style="opacity: 0.55" title="删除文档">
                    <Icon icon="mdi:trash-can-outline" class="h-3.5 w-3.5" />
                  </span>
                </template>
                删除该文档?
              </n-popconfirm>
            </div>
          </div>
          <div v-if="!docStore.documents.length" class="flex flex-col items-center gap-3 py-16 text-center">
            <div
              class="flex h-14 w-14 items-center justify-center"
              style="background: var(--nb-card-bg); border: 1px solid var(--nb-border); clip-path: polygon(10px 0, 100% 0, 100% calc(100% - 10px), calc(100% - 10px) 100%, 0 100%, 0 10px)"
            >
              <Icon icon="mdi:cloud-upload-outline" class="h-7 w-7" style="color: var(--nb-text-faint)" />
            </div>
            <div>
              <p class="text-sm font-medium" style="color: var(--nb-text-secondary)">还没有文档</p>
              <p class="cy-mono mt-0.5 text-[11px]" style="color: var(--nb-text-faint)">// UPLOAD TO INIT KB</p>
            </div>
          </div>
        </div>
      </template>
    </div>
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
            class="h-6 w-6 rounded-full border-2 transition-transform hover:scale-110"
            :style="{ background: c, borderColor: kbForm.color === c ? '#fff' : 'transparent', boxShadow: kbForm.color === c ? `0 0 0 2px ${c}` : 'none' }"
            @click="kbForm.color = c"
          />
        </div>
      </div>
    </div>
  </n-modal>
</template>

<style scoped>
/* ---- 侧栏知识库列表 ---- */
.kb-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border-radius: 0;
  text-align: left;
  color: var(--nb-text-secondary);
  background: transparent;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.2s var(--nb-ease-spring);
}
.kb-item:hover {
  background: var(--nb-nav-active-bg);
  color: var(--nb-text-primary);
  transform: translateX(2px);
}
.kb-item--active {
  background: var(--nb-card-bg);
  color: var(--nb-text-primary);
  border-color: var(--nb-border-strong);
  box-shadow: 0 0 12px -6px var(--nb-accent);
  font-weight: 500;
}
.kb-item__bar {
  position: absolute;
  left: 0;
  top: 50%;
  height: 20px;
  width: 2px;
  border-radius: 0;
  transform: translateY(-50%);
  box-shadow: 0 0 6px currentColor;
}
.kb-new-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 5px 12px;
  border-radius: 0;
  font-family: var(--nb-font-mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--nb-accent);
  background: transparent;
  border: 1px solid color-mix(in srgb, var(--nb-accent) 50%, transparent);
  clip-path: polygon(5px 0, 100% 0, 100% calc(100% - 5px), calc(100% - 5px) 100%, 0 100%, 0 5px);
  cursor: pointer;
  transition: all 0.2s var(--nb-ease-spring);
}
.kb-new-btn:hover {
  background: var(--nb-accent);
  color: #050508;
  box-shadow: var(--nb-glow-accent);
}

/* ---- 统计标签 ---- */
.kb-stat {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: 0;
  color: var(--nb-text-secondary);
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
}

/* ---- 搜索栏 ---- */
.kb-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 6px 6px 14px;
  border-radius: 0;
  background: var(--nb-input-bg);
  border: 1px solid var(--nb-border);
  border-left: 2px solid var(--nb-accent);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.kb-search:focus-within {
  border-color: var(--nb-border-strong);
  border-left-color: var(--nb-accent);
  box-shadow: 0 0 14px -6px var(--nb-accent);
  background: var(--nb-card-bg-solid);
}
.kb-search__input {
  flex: 1;
  min-width: 0;
  background: transparent;
  border: none;
  outline: none;
  font-size: 14px;
  color: var(--nb-text-primary);
}
.kb-search__input::placeholder {
  color: var(--nb-text-faint);
}
.kb-search__btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 16px;
  border-radius: 0;
  font-size: 13px;
  font-weight: 500;
  color: #050508;
  background: var(--nb-accent);
  border: none;
  clip-path: polygon(6px 0, 100% 0, 100% calc(100% - 6px), calc(100% - 6px) 100%, 0 100%, 0 6px);
  cursor: pointer;
  transition: all 0.2s var(--nb-ease-spring);
}
.kb-search__btn:hover:not(:disabled) {
  background: var(--nb-accent-hover);
  box-shadow: var(--nb-glow-accent);
  transform: scale(1.02);
}
.kb-search__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ---- 文档卡片(HUD) ---- */
.doc-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 0;
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
  box-shadow: var(--nb-card-shadow);
  transition: all 0.2s var(--nb-ease-spring);
}
.doc-card:hover {
  border-color: var(--nb-border-strong);
  box-shadow: var(--nb-card-shadow-hover);
  transform: translateX(2px);
}
/* 搜索结果卡片变体 */
.doc-card--search {
  flex-direction: column;
  align-items: stretch;
}
.doc-card--search:hover {
  transform: translateX(2px);
}
/* 文件类型图标容器 */
.doc-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 0;
  border: 1px solid var(--nb-border);
  clip-path: polygon(5px 0, 100% 0, 100% calc(100% - 5px), calc(100% - 5px) 100%, 0 100%, 0 5px);
  flex-shrink: 0;
}
/* 状态胶囊:空心 + 等宽 */
.doc-card__badge {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px 10px;
  border-radius: 0;
  border: 1px solid currentColor;
  font-family: var(--nb-font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.06em;
  line-height: 1;
  white-space: nowrap;
}
</style>
