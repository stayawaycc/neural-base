import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as newsApi from '@/api/news'
import type { NewsItem, NewsTask } from '@/types'

export const useNewsStore = defineStore('news', () => {
  const items = ref<NewsItem[]>([])
  const task = ref<NewsTask | null>(null)
  const filterCategory = ref<string>('')
  const unreadOnly = ref(false)

  const categories = computed(() =>
    Array.from(new Set(items.value.map((n) => n.category).filter((c): c is string => !!c))),
  )
  const filtered = computed(() =>
    items.value.filter(
      (n) =>
        (!filterCategory.value || n.category === filterCategory.value) &&
        (!unreadOnly.value || !n.isRead),
    ),
  )

  let pollTimer: number | null = null

  async function load() {
    items.value = await newsApi.listNews({ unreadOnly: unreadOnly.value || undefined })
  }

  async function refreshTask() {
    task.value = await newsApi.latestTask()
    if (task.value?.status === 'RUNNING') startPolling()
    else stopPolling()
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

  function startPolling() {
    stopPolling()
    pollTimer = window.setInterval(() => void refreshTask(), 2000)
  }

  function stopPolling() {
    if (pollTimer != null) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  return {
    items, task, filterCategory, unreadOnly, categories, filtered,
    load, refreshTask, triggerCrawl, markRead, markAllRead, favorite,
    startPolling, stopPolling,
  }
})
