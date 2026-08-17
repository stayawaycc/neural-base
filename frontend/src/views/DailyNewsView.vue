<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { NButton, NInput, NModal, NPagination, NPopconfirm, NSelect, NSwitch, NTag, type SelectOption } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { useNewsStore } from '@/stores/news'
import { listKbs } from '@/api/kb'
import { message } from '@/utils/feedback'
import type { Kb } from '@/types'

const newsStore = useNewsStore()
const crawling = ref(false)
const favoriteModal = ref(false)
const favoriteNewsId = ref<number | null>(null)
const favoriteKbId = ref<number | null>(null)
const kbs = ref<Kb[]>([])

const sortOptions: SelectOption[] = [
  { label: '最新', value: 'created_desc' },
  { label: '最早', value: 'created_asc' },
  { label: '按来源', value: 'source' },
  { label: '按分类', value: 'category' },
]

const BUILTIN_SOURCE_NAMES = ['GitHub Trending', 'Hacker News', 'Papers with Code', '机器之心', 'InfoQ', 'GitHub Star AI']

const sourceModal = ref(false)
const sourceForm = ref({ name: '', url: '', selector: '' })
const savingSource = ref(false)

function fmtTime(iso: string): string {
  const d = new Date(iso)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

async function onCreateSource() {
  if (!sourceForm.value.name.trim() || !sourceForm.value.url.trim()) {
    message.warning('请填写名称与 URL')
    return
  }
  savingSource.value = true
  try {
    await newsStore.createSource({
      name: sourceForm.value.name.trim(),
      url: sourceForm.value.url.trim(),
      selector: sourceForm.value.selector.trim() || undefined,
    })
    sourceForm.value = { name: '', url: '', selector: '' }
    message.success('来源已添加,下次爬取生效')
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    savingSource.value = false
  }
}

const categoryMeta: Record<string, string> = {
  大模型: 'text-violet-600 dark:text-violet-300',
  框架: 'text-cyan-600 dark:text-cyan-300',
  工具: 'text-emerald-600 dark:text-emerald-300',
  论文: 'text-amber-600 dark:text-amber-300',
  其他: 'text-gray-500',
}

onMounted(async () => {
  await newsStore.load()
  await newsStore.loadSources()
  await newsStore.refreshTask()
})

onBeforeUnmount(() => newsStore.stopPolling())

async function onCrawl() {
  crawling.value = true
  try {
    await newsStore.triggerCrawl()
    message.success('爬取任务已启动')
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    crawling.value = false
  }
}

function openAndMarkRead(n: { id: number; url?: string }) {
  if (n.url) window.open(n.url, '_blank', 'noopener')
  void newsStore.markRead(n.id, true)
}

async function openFavorite(id: number) {
  favoriteNewsId.value = id
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
  if (!favoriteNewsId.value || !favoriteKbId.value) {
    message.warning('请选择知识库')
    return
  }
  try {
    await newsStore.favorite(favoriteNewsId.value, favoriteKbId.value)
    favoriteModal.value = false
    message.success('已收藏进知识库')
  } catch (e) {
    message.error((e as Error).message)
  }
}

const cleaningNonAi = ref(false)

async function onCleanupNonAi() {
  cleaningNonAi.value = true
  try {
    const deleted = await newsStore.cleanupNonAi()
    message.success(`已清理 ${deleted} 条非 AI 历史数据`)
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    cleaningNonAi.value = false
  }
}
</script>

<template>
  <div class="space-y-4">
    <!-- 页面头部:标题 + 任务状态 + 立即爬取 -->
    <div class="flex flex-wrap items-center gap-3">
      <span class="nb-brand-badge h-9 w-9">
        <Icon icon="mdi:newspaper-variant-outline" class="h-5 w-5 text-white" />
      </span>
      <div>
        <h1 class="text-xl font-semibold tracking-tight text-gray-900 dark:text-gray-100">AI 日报</h1>
        <p class="text-xs text-gray-500 dark:text-gray-400">前沿资讯定时爬取,AI 单篇摘要</p>
      </div>
      <div class="ml-auto flex items-center gap-2">
        <div v-if="newsStore.task?.status === 'RUNNING'" class="flex items-center gap-2">
          <div class="nb-progress w-20">
            <div class="nb-progress__bar" :style="{ width: `${newsStore.task.progress ?? 0}%` }" />
          </div>
        </div>
        <n-tag
          v-if="newsStore.task"
          size="small"
          :bordered="false"
          :type="newsStore.task.status === 'SUCCESS' ? 'success' : newsStore.task.status === 'FAILED' ? 'error' : 'info'"
        >
          {{ newsStore.task.status === 'RUNNING' ? `爬取中 ${newsStore.task.progress}%` : newsStore.task.status }}
        </n-tag>
        <n-button
          round
          type="primary"
          size="small"
          :loading="crawling || newsStore.task?.status === 'RUNNING'"
          @click="onCrawl"
        >
          <template #icon><Icon icon="mdi:cloud-download-outline" class="h-4 w-4" /></template>
          立即爬取
        </n-button>
      </div>
    </div>

    <!-- 工具栏:排序 + 来源筛选 + 分类筛选 + 已读 -->
    <div class="flex flex-wrap items-center gap-2">
      <n-select v-model:value="newsStore.sort" :options="sortOptions" size="small" class="w-28" />
      <div class="flex flex-wrap gap-1.5">
        <button
          class="nb-chip"
          :class="newsStore.sourceFilter === null ? 'nb-chip--active' : ''"
          @click="newsStore.sourceFilter = null"
        >
          全部来源
        </button>
        <button
          v-for="s in newsStore.sources"
          :key="s.id"
          class="nb-chip"
          :class="newsStore.sourceFilter === s.id ? 'nb-chip--active' : ''"
          @click="newsStore.sourceFilter = newsStore.sourceFilter === s.id ? null : s.id"
        >
          {{ s.name }}
        </button>
      </div>
      <div class="flex flex-wrap gap-1.5">
        <button
          class="nb-chip"
          :class="newsStore.filterCategory === '' ? 'nb-chip--active' : ''"
          @click="newsStore.filterCategory = ''"
        >
          全部
        </button>
        <button
          v-for="c in newsStore.categories"
          :key="c"
          class="nb-chip"
          :class="newsStore.filterCategory === c ? 'nb-chip--active' : ''"
          @click="newsStore.filterCategory = newsStore.filterCategory === c ? '' : c"
        >
          {{ c }}
        </button>
      </div>
      <div class="ml-auto flex items-center gap-2">
        <n-button size="small" quaternary @click="sourceModal = true">来源管理</n-button>
        <span class="text-xs text-gray-500">仅未读</span>
        <n-switch v-model:value="newsStore.unreadOnly" size="small" />
        <n-button size="small" quaternary @click="newsStore.markAllRead()">全部已读</n-button>
      </div>
    </div>

    <!-- 卡片时间线 -->
    <div class="space-y-2">
      <div
        v-for="n in newsStore.filtered"
        :key="n.id"
        class="glass-card !p-4 transition-all hover:-translate-y-0.5 hover:shadow-[var(--nb-card-shadow-hover)]"
        :class="!n.isRead ? 'glass-card--selected' : ''"
      >
        <div class="flex items-start justify-between gap-2">
          <button
            class="text-left text-sm font-medium text-gray-900 hover:text-cyan-600 dark:text-gray-100 dark:hover:text-cyan-300"
            @click="openAndMarkRead(n)"
          >
            {{ n.title }}
          </button>
          <div class="flex shrink-0 items-center gap-2">
            <span
              v-if="n.category"
              class="rounded-full px-2 py-0.5 text-[11px]"
              :class="[
                categoryMeta[n.category] ?? categoryMeta['其他'],
                { 'bg-violet-50 dark:bg-violet-400/10': n.category === '大模型' },
                { 'bg-cyan-50 dark:bg-cyan-400/10': n.category === '框架' },
                { 'bg-emerald-50 dark:bg-emerald-400/10': n.category === '工具' },
                { 'bg-amber-50 dark:bg-amber-400/10': n.category === '论文' },
              ]"
            >
              {{ n.category }}
            </span>
            <span
              class="btn-press cursor-pointer rounded p-1 text-gray-400 hover:text-amber-400 dark:text-gray-600"
              title="收藏到知识库"
              @click="openFavorite(n.id)"
            >
              <Icon icon="mdi:bookmark-plus-outline" class="h-4 w-4" />
            </span>
          </div>
        </div>
        <p v-if="n.summary" class="mt-1.5 text-xs leading-relaxed text-gray-500 dark:text-gray-400">{{ n.summary }}</p>
        <p v-else class="mt-1.5 text-xs text-gray-400 dark:text-gray-600">暂无摘要</p>
        <div class="mt-2 flex items-center gap-3 text-[11px] text-gray-400 dark:text-gray-600">
          <span v-if="n.sourceName" class="flex items-center gap-1.5">
            <span class="h-1.5 w-1.5 rounded-full bg-gray-300 dark:bg-gray-600" />
            {{ n.sourceName }}
          </span>
          <span class="flex items-center gap-1">
            <Icon icon="mdi:clock-outline" class="h-3 w-3" />
            {{ fmtTime(n.createdAt) }}
          </span>
          <span v-if="!n.isRead" class="ml-auto flex items-center gap-1.5 text-cyan-600 dark:text-cyan-400">
            <span class="h-1.5 w-1.5 rounded-full bg-cyan-500" />
            未读
          </span>
        </div>
      </div>
      <div v-if="!newsStore.filtered.length" class="py-16 text-center text-sm text-gray-400 dark:text-gray-600">
        还没有日报,点击右上角「立即爬取」
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="newsStore.total > newsStore.size" class="flex justify-center pt-2">
      <n-pagination
        :page="newsStore.page"
        :page-size="newsStore.size"
        :item-count="newsStore.total"
        @update:page="newsStore.page = $event"
      />
    </div>

    <!-- 收藏弹窗 -->
    <n-modal
      v-model:show="favoriteModal"
      preset="dialog"
      title="收藏到知识库"
      positive-text="收藏"
      negative-text="取消"
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

    <!-- 来源管理弹窗 -->
    <n-modal
      v-model:show="sourceModal"
      preset="dialog"
      title="来源管理"
      positive-text="关闭"
      @positive-click="sourceModal = false"
    >
      <div class="space-y-3 py-2">
        <div class="space-y-1.5">
          <div
            v-for="s in newsStore.sources"
            :key="s.id"
            class="flex items-center justify-between gap-2 rounded-lg border border-gray-100 px-3 py-2 dark:border-white/5"
          >
            <div class="min-w-0">
              <div class="truncate text-sm text-gray-800 dark:text-gray-200">{{ s.name }}</div>
              <div class="truncate text-xs text-gray-400 dark:text-gray-600">{{ s.url }}</div>
            </div>
            <div class="flex shrink-0 items-center gap-2">
              <n-switch
                size="small"
                :value="s.enabled"
                @update:value="(v: boolean) => newsStore.updateSource(s.id, { enabled: v })"
              />
              <button
                v-if="!BUILTIN_SOURCE_NAMES.includes(s.name)"
                class="btn-press text-gray-400 hover:text-red-400 dark:text-gray-600"
                @click="newsStore.removeSource(s.id)"
              >
                <Icon icon="mdi:trash-can-outline" class="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
        <div class="border-t border-gray-100 pt-3 dark:border-white/5">
          <div class="mb-2 text-xs text-gray-500">
            新增静态来源(仅支持无 JS 渲染的列表页,需填写链接选择器)
          </div>
          <div class="space-y-2">
            <n-input v-model:value="sourceForm.name" size="small" placeholder="名称" />
            <n-input v-model:value="sourceForm.url" size="small" placeholder="URL" />
            <n-input v-model:value="sourceForm.selector" size="small" placeholder="链接选择器,如 ul.news-list a" />
            <n-button size="small" type="primary" :loading="savingSource" @click="onCreateSource">
              添加来源
            </n-button>
          </div>
        </div>
        <div class="border-t border-gray-100 pt-3 dark:border-white/5">
          <n-popconfirm
            @positive-click="onCleanupNonAi"
            :positive-button-props="{ type: 'warning' }"
          >
            <template #trigger>
              <n-button size="small" quaternary :loading="cleaningNonAi">清理非 AI 历史数据</n-button>
            </template>
            将删除 GitHub Trending / Hacker News / InfoQ 三个源中标题不含 AI 关键词的历史条目,确认继续?
          </n-popconfirm>
        </div>
      </div>
    </n-modal>
  </div>
</template>
