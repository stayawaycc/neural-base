import { reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import * as baguwenApi from '@/api/baguwen'
import type { BaguwenArticleView, ImportTask, TagCount } from '@/types'

export const useBaguwenStore = defineStore('baguwen', () => {
  const articles = ref<BaguwenArticleView[]>([])
  const total = ref(0)
  const tags = ref<TagCount[]>([])
  const tasks = ref<ImportTask[]>([])
  const filter = reactive({ tag: '', keyword: '', page: 1, size: 20 })

  let pollTimer: number | null = null

  async function loadTags() {
    tags.value = await baguwenApi.listTags()
  }

  async function loadArticles() {
    const page = await baguwenApi.listArticles({
      tag: filter.tag || undefined,
      keyword: filter.keyword || undefined,
      page: filter.page,
      size: filter.size,
    })
    articles.value = page.records
    total.value = page.total
  }

  async function importUrls(urls: string[]) {
    await baguwenApi.importUrls(urls)
    await refreshTasks()
    startPolling()
  }

  async function refreshTasks() {
    tasks.value = await baguwenApi.listTasks()
  }

  async function retry(id: number) {
    await baguwenApi.retryTask(id)
    await refreshTasks()
    startPolling()
  }

  async function removeArticle(id: number) {
    await baguwenApi.deleteArticle(id)
    await loadArticles()
    await loadTags()
  }

  async function favorite(id: number, kbId: number) {
    return baguwenApi.favoriteArticle(id, kbId)
  }

  function startPolling() {
    stopPolling()
    pollTimer = window.setInterval(() => {
      void (async () => {
        await refreshTasks()
        const pending = tasks.value.some(
          (t) => t.status === 'PENDING' || t.status === 'PROCESSING',
        )
        if (!pending) {
          stopPolling()
          await loadArticles()
          await loadTags()
        }
      })()
    }, 2000)
  }

  function stopPolling() {
    if (pollTimer != null) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  return {
    articles,
    total,
    tags,
    tasks,
    filter,
    loadTags,
    loadArticles,
    importUrls,
    refreshTasks,
    retry,
    removeArticle,
    favorite,
    startPolling,
    stopPolling,
  }
})
