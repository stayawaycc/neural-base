import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import * as newsApi from '@/api/news'
import { usePolling } from '@/utils/polling'
import type { NewsItem, NewsTask } from '@/types'

export const useNewsStore = defineStore('news', () => {
  const items = ref<NewsItem[]>([])
  const task = ref<NewsTask | null>(null)
  const filterCategory = ref<string>('')
  const unreadOnly = ref(false)

  // 固定五类 chips(规格:全部/大模型/框架/工具/论文/其他)
  const categories = ['大模型', '框架', '工具', '论文', '其他'] as const

  // 未读筛选走后端过滤(切换时重新拉取,避免本地与后端 LIMIT 200 不一致)
  watch(unreadOnly, () => void load())
  const filtered = computed(() =>
    items.value.filter(
      (n) =>
        (!filterCategory.value || n.category === filterCategory.value) &&
        (!unreadOnly.value || !n.isRead),
    ),
  )

  const { start: startPolling, stop: stopPolling } = usePolling(async () => {
    await refreshTask()
    if (task.value && task.value.status !== 'RUNNING') stopPolling()
  })

  async function load() {
    items.value = await newsApi.listNews({ unreadOnly: unreadOnly.value || undefined })
  }

  async function refreshTask() {
    task.value = await newsApi.latestTask()
    if (task.value && task.value.status === 'RUNNING') startPolling()
  }

  async function triggerCrawl() {
    await newsApi.triggerCrawl()
    await refreshTask()
  }

  async function markRead(id: number, isRead: boolean) {
    await newsApi.markRead(id, isRead)
    const target = items.value.find((n) => n.id === id)
    if (target) target.isRead = isRead
  }

  async function markAllRead() {
    await newsApi.markAllRead()
    items.value.forEach((n) => (n.isRead = true))
  }

  async function favorite(id: number, kbId: number) {
    await newsApi.favoriteNews(id, kbId)
  }

  return {
    items, task, filterCategory, unreadOnly, categories, filtered,
    load, refreshTask, triggerCrawl, markRead, markAllRead, favorite,
    startPolling, stopPolling,
  }
})
