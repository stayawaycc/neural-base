<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { NButton, NModal, NSelect, NSwitch, NTag } from 'naive-ui'
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

const categoryMeta: Record<string, string> = {
  大模型: 'text-violet-600 dark:text-violet-300',
  框架: 'text-cyan-600 dark:text-cyan-300',
  工具: 'text-emerald-600 dark:text-emerald-300',
  论文: 'text-amber-600 dark:text-amber-300',
  其他: 'text-gray-500',
}

onMounted(async () => {
  await newsStore.load()
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

function openUrl(n: { id: number; url?: string }) {
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
</script>

<template>
  <div class="space-y-4">
    <!-- 工具栏:手动爬取 + 任务状态 + 分类筛选 + 未读/全部已读 -->
    <div class="flex flex-wrap items-center gap-2">
      <n-button
        type="primary"
        size="small"
        :loading="crawling || newsStore.task?.status === 'RUNNING'"
        @click="onCrawl"
      >
        <template #icon><Icon icon="mdi:cloud-download-outline" class="h-4 w-4" /></template>
        立即爬取
      </n-button>
      <n-tag
        v-if="newsStore.task"
        size="small"
        :bordered="false"
        :type="newsStore.task.status === 'SUCCESS' ? 'success' : newsStore.task.status === 'FAILED' ? 'error' : 'info'"
      >
        {{ newsStore.task.status === 'RUNNING' ? `爬取中 ${newsStore.task.progress}%` : newsStore.task.status }}
      </n-tag>
      <div class="flex flex-wrap gap-1.5">
        <n-tag
          v-for="c in newsStore.categories"
          :key="c"
          size="small"
          :bordered="false"
          :type="newsStore.filterCategory === c ? 'warning' : 'default'"
          class="cursor-pointer"
          @click="newsStore.filterCategory = newsStore.filterCategory === c ? '' : c"
        >
          {{ c }}
        </n-tag>
      </div>
      <div class="ml-auto flex items-center gap-2">
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
        class="glass-card !p-4 transition-colors"
        :class="!n.isRead ? 'glass-card--selected' : ''"
      >
        <div class="flex items-start justify-between gap-2">
          <button
            class="text-left text-sm font-medium text-gray-900 hover:text-cyan-600 dark:text-gray-100 dark:hover:text-cyan-300"
            @click="openUrl(n)"
          >
            {{ n.title }}
          </button>
          <div class="flex shrink-0 items-center gap-2">
            <span
              v-if="n.category"
              class="text-xs"
              :class="categoryMeta[n.category] ?? categoryMeta['其他']"
            >
              {{ n.category }}
            </span>
            <span v-if="!n.isRead" class="h-2 w-2 rounded-full bg-cyan-400" title="未读" />
            <span
              class="btn-press cursor-pointer rounded p-1 text-gray-400 hover:text-amber-400 dark:text-gray-600"
              title="收藏到知识库"
              @click="openFavorite(n.id)"
            >
              <Icon icon="mdi:bookmark-plus-outline" class="h-4 w-4" />
            </span>
          </div>
        </div>
        <p v-if="n.summary" class="mt-1 text-xs leading-relaxed text-gray-500">{{ n.summary }}</p>
        <p v-else class="mt-1 text-xs text-gray-400 dark:text-gray-600">暂无摘要</p>
      </div>
      <div v-if="!newsStore.filtered.length" class="py-16 text-center text-sm text-gray-400 dark:text-gray-600">
        还没有日报,点击右上角「立即爬取」
      </div>
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
  </div>
</template>
