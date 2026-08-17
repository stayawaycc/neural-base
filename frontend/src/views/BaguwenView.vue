<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NInput, NModal, NPopconfirm, NPagination, NSelect, NSkeleton, NTag } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { useBaguwenStore } from '@/stores/baguwen'
import { listKbs } from '@/api/kb'
import { message } from '@/utils/feedback'
import type { ImportTaskStatus, Kb } from '@/types'

const baguwenStore = useBaguwenStore()
const router = useRouter()

const urlText = ref('')
const importing = ref(false)
const searchKeyword = ref('')
const listLoading = ref(true)

const favoriteModal = ref(false)
const favoriteArticleId = ref<number | null>(null)
const favoriteKbId = ref<number | null>(null)
const kbs = ref<Kb[]>([])
const favoriting = ref(false)

const taskStatusMeta: Record<ImportTaskStatus, { type: 'default' | 'info' | 'success' | 'error'; label: string }> = {
  PENDING: { type: 'default', label: '排队中' },
  PROCESSING: { type: 'info', label: '爬取中' },
  SUCCESS: { type: 'success', label: '成功' },
  FAILED: { type: 'error', label: '失败' },
}

onMounted(async () => {
  try {
    await baguwenStore.loadArticles()
    await baguwenStore.loadTags()
    await baguwenStore.refreshTasks() // 内部:有处理中任务则启动轮询
  } finally {
    listLoading.value = false // 请求失败也解除骨架,避免永驻
  }
})

onBeforeUnmount(() => baguwenStore.stopPolling())

async function onImport() {
  const urls = urlText.value
    .split('\n')
    .map((u) => u.trim())
    .filter((u) => u && /^https?:\/\//.test(u))
  if (!urls.length) {
    message.warning('请输入至少一个 http(s) 链接,每行一个')
    return
  }
  importing.value = true
  try {
    await baguwenStore.importUrls(urls)
    urlText.value = ''
    message.success(`已提交 ${urls.length} 个导入任务`)
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    importing.value = false
  }
}

function onSearch() {
  baguwenStore.filter.keyword = searchKeyword.value.trim()
  baguwenStore.filter.page = 1
  void baguwenStore.loadArticles()
}

function onTagFilter(tag: string) {
  baguwenStore.filter.tag = baguwenStore.filter.tag === tag ? '' : tag
  baguwenStore.filter.page = 1
  void baguwenStore.loadArticles()
}

function onPageChange(page: number) {
  baguwenStore.filter.page = page
  void baguwenStore.loadArticles()
}

function onTaskPageChange(page: number) {
  baguwenStore.taskFilter.page = page
  void baguwenStore.loadTasksPage()
}

const clearingFailed = ref(false)

async function onClearFailed() {
  clearingFailed.value = true
  try {
    const deleted = await baguwenStore.clearFailedTasks()
    message.success(deleted > 0 ? `已清理 ${deleted} 个失败任务` : '没有失败任务')
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    clearingFailed.value = false
  }
}

async function openFavorite(id: number) {
  favoriteArticleId.value = id
  favoriteKbId.value = null
  favoriteModal.value = true
  if (!kbs.value.length) {
    try {
      kbs.value = await listKbs()
    } catch {
      kbs.value = []
    }
  }
}

async function onFavoriteConfirm() {
  if (!favoriteArticleId.value || !favoriteKbId.value) {
    message.warning('请选择知识库')
    return
  }
  favoriting.value = true
  try {
    const result = await baguwenStore.favorite(favoriteArticleId.value, favoriteKbId.value)
    favoriteModal.value = false
    message.success(`已收藏,写入 ${result.embedded} 个向量片段`)
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    favoriting.value = false
  }
}
</script>

<template>
  <div class="space-y-4">
    <!-- 页面头部 -->
    <div class="flex items-center gap-3">
      <span class="nb-brand-badge h-9 w-9">
        <Icon icon="mdi:file-document-multiple-outline" class="h-5 w-5 text-white" />
      </span>
      <div>
        <h1 class="text-xl font-semibold tracking-tight text-gray-900 dark:text-gray-100">八股文库</h1>
        <p class="text-xs text-gray-500 dark:text-gray-400">URL 导入,AI 自动提取问答对</p>
      </div>
      <div class="ml-auto">
        <span class="rounded-full border border-gray-200 px-2.5 py-1 text-xs text-gray-500 dark:border-white/10 dark:text-gray-400">
          共 {{ baguwenStore.total }} 篇
        </span>
      </div>
    </div>

    <!-- 导入区 -->
    <div class="glass-card space-y-3 !p-5">
      <div class="flex items-center gap-3">
        <span
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl"
          :style="{
            background: 'color-mix(in srgb, var(--nb-accent-amber) 14%, transparent)',
            boxShadow: '0 0 0 1px color-mix(in srgb, var(--nb-accent-amber) 22%, transparent)',
          }"
        >
          <Icon icon="mdi:link-variant" class="h-5 w-5" :style="{ color: 'var(--nb-accent-amber)' }" />
        </span>
        <div>
          <div class="text-sm font-medium text-gray-800 dark:text-gray-200">导入文章</div>
          <div class="text-xs text-gray-500 dark:text-gray-400">每行一个 URL,后台异步爬取 + AI 结构化</div>
        </div>
      </div>
      <n-input
        v-model:value="urlText"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 5 }"
        placeholder="https://example.com/article&#10;https://example.com/another"
      />
      <div class="flex justify-end">
        <n-button round type="primary" :loading="importing" @click="onImport">
          <template #icon><Icon icon="mdi:download-box-outline" class="h-4 w-4" /></template>
          开始导入
        </n-button>
      </div>
    </div>

    <!-- 任务进度 -->
    <div v-if="baguwenStore.tasks.length" class="glass-card !p-4">
      <div class="mb-2 flex items-center gap-2">
        <span
          class="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg"
          :style="{
            background: 'color-mix(in srgb, var(--nb-accent-amber) 14%, transparent)',
            boxShadow: '0 0 0 1px color-mix(in srgb, var(--nb-accent-amber) 22%, transparent)',
          }"
        >
          <Icon icon="mdi:progress-clock" class="h-4 w-4" :style="{ color: 'var(--nb-accent-amber)' }" />
        </span>
        <span class="text-xs font-medium text-gray-700 dark:text-gray-300">导入任务</span>
        <span v-if="baguwenStore.pendingCount" class="text-xs text-gray-400 dark:text-gray-500">处理中 {{ baguwenStore.pendingCount }} 个</span>
        <span v-else class="text-xs text-emerald-600 dark:text-emerald-400">全部完成</span>
        <div class="ml-auto">
          <n-popconfirm @positive-click="onClearFailed">
            <template #trigger>
              <n-button size="tiny" quaternary :loading="clearingFailed">清理失败任务</n-button>
            </template>
            删除全部失败任务(不影响已导入的文章)?
          </n-popconfirm>
        </div>
      </div>
      <div class="space-y-0.5">
        <div
          v-for="t in baguwenStore.tasks"
          :key="t.id"
          class="group flex items-center gap-2.5 rounded-lg px-2 py-1.5 transition-colors hover:bg-gray-50 dark:hover:bg-white/[0.02]"
        >
          <span class="task-dot" :class="`task-dot--${t.status.toLowerCase()}`" />
          <div class="min-w-0 flex-1">
            <div class="truncate text-xs text-gray-600 dark:text-gray-400">{{ t.url }}</div>
            <div v-if="t.status === 'FAILED' && t.errorMsg" class="truncate text-[11px] text-red-400 dark:text-red-400/80">
              {{ t.errorMsg }}
            </div>
          </div>
          <div class="flex shrink-0 items-center gap-1">
            <n-tag size="tiny" :type="taskStatusMeta[t.status].type" :bordered="false">
              {{ taskStatusMeta[t.status].label }}
            </n-tag>
            <n-button v-if="t.status === 'FAILED'" size="tiny" quaternary @click="baguwenStore.retry(t.id)">
              重试
            </n-button>
            <n-popconfirm
              v-if="t.status === 'FAILED' || t.status === 'SUCCESS'"
              @positive-click="baguwenStore.removeTask(t.id)"
            >
              <template #trigger>
                <span
                  class="cursor-pointer rounded p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-red-400 dark:text-gray-600 dark:hover:bg-white/5"
                  title="删除任务"
                >
                  <Icon icon="mdi:trash-can-outline" class="h-3.5 w-3.5" />
                </span>
              </template>
              删除该导入任务?(不影响已导入的文章)
            </n-popconfirm>
          </div>
        </div>
      </div>
      <div v-if="baguwenStore.taskTotal > baguwenStore.taskFilter.size" class="flex justify-center pt-2">
        <n-pagination
          :page="baguwenStore.taskFilter.page"
          :page-size="baguwenStore.taskFilter.size"
          :item-count="baguwenStore.taskTotal"
          @update:page="onTaskPageChange"
        />
      </div>
    </div>

    <!-- 筛选 + 列表 -->
    <div class="flex flex-wrap items-center gap-2">
      <n-input
        v-model:value="searchKeyword"
        placeholder="搜索标题/正文/问答…"
        class="max-w-xs"
        clearable
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <n-button quaternary @click="onSearch">搜索</n-button>
      <div class="flex flex-wrap gap-1.5">
        <button
          v-for="t in baguwenStore.tags"
          :key="t.tag"
          class="nb-chip"
          :class="baguwenStore.filter.tag === t.tag ? 'nb-chip--active' : ''"
          @click="onTagFilter(t.tag)"
        >
          {{ t.tag }} {{ t.count }}
        </button>
      </div>
    </div>

    <div v-if="listLoading" class="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
      <div v-for="i in 3" :key="i" class="glass-card space-y-3 !p-4">
        <n-skeleton text :repeat="2" />
        <n-skeleton text style="width: 40%" />
      </div>
    </div>
    <div v-else class="fade-in grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
      <div
        v-for="a in baguwenStore.articles"
        :key="a.id"
        class="glass-card glass-card--hover btn-press flex cursor-pointer flex-col gap-2"
        @click="router.push({ name: 'baguwen-detail', params: { id: a.id } })"
      >
        <div class="flex items-start justify-between gap-2">
          <span class="line-clamp-2 text-sm font-medium text-gray-800 dark:text-gray-200">{{ a.title }}</span>
          <div class="flex shrink-0 gap-1" @click.stop>
            <span
              class="cursor-pointer rounded p-1 text-gray-500 transition-colors hover:bg-gray-100 hover:text-cyan-600 dark:hover:bg-white/5 dark:hover:text-cyan-400"
              title="收藏到知识库"
              @click="openFavorite(a.id)"
            >
              <Icon icon="mdi:bookmark-plus-outline" class="h-4 w-4" />
            </span>
            <n-popconfirm @positive-click="baguwenStore.removeArticle(a.id)">
              <template #trigger>
                <span class="cursor-pointer rounded p-1 text-gray-500 transition-colors hover:bg-gray-100 hover:text-red-400 dark:hover:bg-white/5">
                  <Icon icon="mdi:trash-can-outline" class="h-4 w-4" />
                </span>
              </template>
              删除该文章及其问答对?
            </n-popconfirm>
          </div>
        </div>
        <div class="flex flex-wrap gap-1">
          <n-tag v-for="t in a.tags" :key="t" size="tiny" :bordered="false">
            {{ t }}
          </n-tag>
        </div>
        <div class="mt-auto flex items-center gap-1.5 text-xs text-gray-400 dark:text-gray-600">
          <Icon icon="mdi:calendar-blank-outline" class="h-3.5 w-3.5" />
          {{ new Date(a.createdAt).toLocaleDateString() }}
        </div>
      </div>
    </div>

    <div v-if="!baguwenStore.articles.length" class="py-12 text-center text-sm text-gray-400 dark:text-gray-600">
      暂无文章,在上方输入 URL 开始导入
    </div>

    <div v-if="baguwenStore.total > baguwenStore.filter.size" class="flex justify-center">
      <n-pagination
        :page="baguwenStore.filter.page"
        :page-size="baguwenStore.filter.size"
        :item-count="baguwenStore.total"
        @update:page="onPageChange"
      />
    </div>
  </div>

  <!-- 收藏弹窗 -->
  <n-modal
    v-model:show="favoriteModal"
    preset="dialog"
    title="收藏到知识库"
    positive-text="收藏"
    negative-text="取消"
    :positive-button-props="{ loading: favoriting }"
    @positive-click="onFavoriteConfirm"
  >
    <div class="py-2">
      <n-select
        v-model:value="favoriteKbId"
        placeholder="选择知识库"
        :options="kbs.map((k) => ({ label: k.name, value: k.id }))"
      />
    </div>
  </n-modal>
</template>

<style scoped>
/* 任务状态色点:PENDING 灰 / PROCESSING 青色呼吸 / SUCCESS 绿 / FAILED 红 */
.task-dot {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  border-radius: 9999px;
  background: var(--nb-text-faint);
}
.task-dot--processing {
  background: var(--nb-accent-cyan);
  animation: nb-task-pulse 1.2s ease-in-out infinite;
}
.task-dot--success {
  background: var(--nb-accent-emerald);
}
.task-dot--failed {
  background: #ef4444;
}
@keyframes nb-task-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.35;
  }
}
@media (prefers-reduced-motion: reduce) {
  .task-dot--processing {
    animation: none;
  }
}
</style>
