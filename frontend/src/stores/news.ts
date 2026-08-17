import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import * as newsApi from '@/api/news'
import { usePolling } from '@/utils/polling'
import type { NewsItem, NewsSort, NewsSource, NewsTask } from '@/types'

export const useNewsStore = defineStore('news', () => {
  const items = ref<NewsItem[]>([])
  const task = ref<NewsTask | null>(null)
  const filterCategory = ref<string>('')
  const unreadOnly = ref(false)
  const sort = ref<NewsSort>('created_desc')
  const sourceFilter = ref<number | null>(null)
  const sources = ref<NewsSource[]>([])
  const page = ref(1)
  const size = 20
  const total = ref(0)

  // 固定五类 chips(规格:全部/大模型/框架/工具/论文/其他)
  const categories = ['大模型', '框架', '工具', '论文', '其他'] as const

  // 服务端分页:筛选/排序变化回第 1 页;分页变化直接重拉;本地不再过滤
  watch([unreadOnly, sort, sourceFilter, filterCategory], () => {
    if (page.value !== 1) {
      page.value = 1 // 触发 page watch → load
    } else {
      void load()
    }
  })
  watch(page, () => void load())

  const { start: startPolling, stop: stopPolling } = usePolling(async () => {
    await refreshTask()
    if (task.value && task.value.status !== 'RUNNING') stopPolling()
  })

  async function load() {
    const r = await newsApi.listNews({
      category: filterCategory.value || undefined,
      unreadOnly: unreadOnly.value || undefined,
      sourceId: sourceFilter.value ?? undefined,
      sort: sort.value,
      page: page.value,
      size,
    })
    items.value = r.items
    total.value = r.total
  }

  async function loadSources() {
    sources.value = await newsApi.listSources()
  }

  async function createSource(payload: { name: string; url: string; selector?: string }) {
    await newsApi.createSource(payload)
    await loadSources()
  }

  async function updateSource(id: number, payload: { name?: string; url?: string; selector?: string; enabled?: boolean }) {
    await newsApi.updateSource(id, payload)
    await loadSources()
  }

  async function removeSource(id: number) {
    await newsApi.deleteSource(id)
    await loadSources()
  }

  async function refreshTask() {
    const prev = task.value?.status
    task.value = await newsApi.latestTask()
    if (task.value && task.value.status === 'RUNNING') {
      startPolling()
    } else if (prev === 'RUNNING') {
      await load() // 爬取完成自动刷新列表,否则修复后仍需手动刷新才能看到新数据
    }
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

  /** 清理三个通用内置源的历史非 AI 条目,返回删除条数并刷新列表。 */
  async function cleanupNonAi() {
    const deleted = await newsApi.cleanupNonAi()
    await load()
    return deleted
  }

  return {
    items, task, filterCategory, unreadOnly, sort, sourceFilter, sources, categories,
    page, size, total,
    load, loadSources, createSource, updateSource, removeSource,
    refreshTask, triggerCrawl, markRead, markAllRead, favorite, cleanupNonAi,
    startPolling, stopPolling,
  }
})
