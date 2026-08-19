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
  const startDate = ref<string>('')
  const endDate = ref<string>('')
  const sources = ref<NewsSource[]>([])
  const page = ref(1)
  const size = 20
  const total = ref(0)
  const selection = ref<number[]>([])

  // 固定五类 chips(规格:全部/大模型/框架/工具/论文/其他)
  const categories = ['大模型', '框架', '工具', '论文', '其他'] as const

  // 服务端分页:筛选/排序变化回第 1 页;分页变化直接重拉;本地不再过滤
  watch([unreadOnly, sort, sourceFilter, filterCategory, startDate, endDate], () => {
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
      startDate: startDate.value || undefined,
      endDate: endDate.value || undefined,
      page: page.value,
      size,
    })
    items.value = r.items
    total.value = r.total
    // 删除后当前页可能为空,自动回退上一页
    if (items.value.length === 0 && page.value > 1) {
      page.value -= 1
      await load()
    }
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
      // 爬取完成:静默刷新列表展示最新数据;失败不阻塞轮询停止,保持旧数据
      try {
        await load()
      } catch {
        /* 列表刷新失败保持旧数据 */
      }
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

  function toggleSelect(id: number) {
    const i = selection.value.indexOf(id)
    if (i >= 0) selection.value.splice(i, 1)
    else selection.value.push(id)
  }

  function clearSelection() {
    selection.value = []
  }

  async function batchDelete() {
    if (selection.value.length === 0) return 0
    const deleted = await newsApi.batchDeleteNews([...selection.value])
    selection.value = []
    await load()
    return deleted
  }

  return {
    items, task, filterCategory, unreadOnly, sort, sourceFilter, sources, categories,
    page, size, total, startDate, endDate, selection,
    load, loadSources, createSource, updateSource, removeSource,
    refreshTask, triggerCrawl, markRead, markAllRead, favorite, cleanupNonAi,
    toggleSelect, clearSelection, batchDelete,
    startPolling, stopPolling,
  }
})
