<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
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

const pendingTaskCount = computed(
  () => baguwenStore.tasks.filter((t) => t.status === 'PENDING' || t.status === 'PROCESSING').length,
)

onMounted(async () => {
  try {
    await baguwenStore.loadArticles()
    await baguwenStore.loadTags()
    await baguwenStore.refreshTasks()
  } finally {
    listLoading.value = false // 请求失败也解除骨架,避免永驻
  }
  if (pendingTaskCount.value) baguwenStore.startPolling()
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
    <!-- 导入区 -->
    <div class="glass-card space-y-2">
      <div class="text-sm text-gray-700 dark:text-gray-300">导入文章(每行一个 URL,后台异步爬取 + AI 结构化)</div>
      <n-input
        v-model:value="urlText"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 5 }"
        placeholder="https://example.com/article&#10;https://example.com/another"
      />
      <div class="flex justify-end">
        <n-button type="primary" :loading="importing" @click="onImport">
          <template #icon><Icon icon="mdi:download-box-outline" class="h-4 w-4" /></template>
          开始导入
        </n-button>
      </div>
    </div>

    <!-- 任务进度 -->
    <div v-if="baguwenStore.tasks.length" class="glass-card space-y-1.5">
      <div class="text-xs text-gray-500">
        导入任务(处理中 {{ pendingTaskCount }} 个,每 2 秒刷新)
      </div>
      <div v-for="t in baguwenStore.tasks" :key="t.id" class="flex items-center justify-between gap-2 py-1">
        <span class="min-w-0 flex-1 truncate text-xs text-gray-600 dark:text-gray-400">{{ t.url }}</span>
        <div class="flex shrink-0 items-center gap-2">
          <n-tag size="small" :type="taskStatusMeta[t.status].type" :bordered="false">
            {{ taskStatusMeta[t.status].label }}
          </n-tag>
          <n-button v-if="t.status === 'FAILED'" size="tiny" quaternary @click="baguwenStore.retry(t.id)">
            重试
          </n-button>
        </div>
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
        <n-tag
          v-for="t in baguwenStore.tags"
          :key="t.tag"
          size="small"
          :bordered="false"
          :type="baguwenStore.filter.tag === t.tag ? 'warning' : 'default'"
          class="cursor-pointer"
          @click="onTagFilter(t.tag)"
        >
          {{ t.tag }} {{ t.count }}
        </n-tag>
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
          <n-tag v-for="t in a.tags" :key="t" size="tiny" :bordered="false" type="warning">
            {{ t }}
          </n-tag>
        </div>
        <div class="mt-auto text-xs text-gray-400 dark:text-gray-600">{{ new Date(a.createdAt).toLocaleDateString() }}</div>
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
