import { reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import * as baguwenApi from '@/api/baguwen'
import { usePolling } from '@/utils/polling'
import type { BaguwenArticleView, ImportTask, TagCount } from '@/types'

export const useBaguwenStore = defineStore('baguwen', () => {
  const articles = ref<BaguwenArticleView[]>([])
  const total = ref(0)
  const tags = ref<TagCount[]>([])
  const tasks = ref<ImportTask[]>([])
  const taskFilter = reactive({ page: 1, size: 10 })
  const taskTotal = ref(0)
  const filter = reactive({ tag: '', keyword: '', page: 1, size: 20 })

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

  async function loadTasksPage() {
    const page = await baguwenApi.listTasks({ page: taskFilter.page, size: taskFilter.size })
    tasks.value = page.records
    taskTotal.value = page.total
  }

  async function importUrls(urls: string[]) {
    await baguwenApi.importUrls(urls)
    taskFilter.page = 1
    await loadTasksPage()
    startPolling()
  }

  async function refreshTasks() {
    await loadTasksPage()
  }

  async function retry(id: number) {
    await baguwenApi.retryTask(id)
    taskFilter.page = 1
    await loadTasksPage()
    startPolling()
  }

  async function removeTask(id: number) {
    await baguwenApi.deleteTask(id)
    await loadTasksPage()
  }

  /** 批量清理失败任务,返回删除条数并刷新当前任务页。 */
  async function clearFailedTasks() {
    const deleted = await baguwenApi.clearTasks('FAILED')
    await loadTasksPage()
    return deleted
  }

  async function removeArticle(id: number) {
    await baguwenApi.deleteArticle(id)
    await loadArticles()
    await loadTags()
  }

  async function favorite(id: number, kbId: number) {
    return baguwenApi.favoriteArticle(id, kbId)
  }

  // 轮询:多状态 total 判停(分页后当前页任务不足以判断全局是否还有处理中任务)
  const { start: startPolling, stop: stopPolling } = usePolling(async () => {
    const pending = await baguwenApi.listTasks({ status: 'PENDING,PROCESSING', page: 1, size: 1 })
    await loadTasksPage()
    if (!pending.total) {
      stopPolling()
      await loadArticles()
      await loadTags()
    }
  })

  return {
    articles,
    total,
    tags,
    tasks,
    taskFilter,
    taskTotal,
    filter,
    loadTags,
    loadArticles,
    loadTasksPage,
    importUrls,
    refreshTasks,
    retry,
    removeTask,
    clearFailedTasks,
    removeArticle,
    favorite,
    startPolling,
    stopPolling,
  }
})
