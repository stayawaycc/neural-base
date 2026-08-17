import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useBaguwenStore } from '@/stores/baguwen'
import type { ImportTask } from '@/types'

vi.mock('@/api/baguwen', () => ({
  importUrls: vi.fn(),
  listTasks: vi.fn(),
  retryTask: vi.fn(),
  deleteTask: vi.fn(),
  clearTasks: vi.fn(),
  listArticles: vi.fn(),
  listTags: vi.fn(),
  deleteArticle: vi.fn(),
  favoriteArticle: vi.fn(),
}))

import {
  clearTasks,
  deleteTask,
  importUrls,
  listTasks,
  retryTask,
  listArticles,
  listTags,
  deleteArticle,
  favoriteArticle,
} from '@/api/baguwen'

const article = { id: 1, title: 'JVM 面试题', url: 'https://x', tags: ['Java'], createdAt: '' }
const task: ImportTask = {
  id: 1,
  url: 'https://x',
  status: 'SUCCESS',
  attempt: 0,
  createdAt: '',
  updatedAt: '',
}

const pageOf = (records: ImportTask[], total = records.length, size = 10) =>
  ({ records, total, size, current: 1 })

describe('baguwen store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  it('导入后刷新任务页并轮询,全部落定后刷新文章与标签', async () => {
    vi.mocked(importUrls).mockResolvedValue([task])
    const proc: ImportTask = { ...task, status: 'PROCESSING' }
    vi.mocked(listTasks)
      .mockResolvedValueOnce(pageOf([proc])) // 导入后首屏
      .mockResolvedValueOnce(pageOf([], 1, 1)) // 轮询:多状态 total=1 继续
      .mockResolvedValueOnce(pageOf([proc])) // 轮询:刷新任务页
      .mockResolvedValueOnce(pageOf([], 0, 1)) // 轮询:total=0 停
      .mockResolvedValueOnce(pageOf([task])) // 停止前最后一次任务页刷新
    vi.mocked(listArticles).mockResolvedValue({ records: [article], total: 1, size: 20, current: 1 })
    vi.mocked(listTags).mockResolvedValue([{ tag: 'Java', count: 1 }])
    const store = useBaguwenStore()

    await store.importUrls(['https://x'])
    expect(store.tasks).toHaveLength(1)
    expect(store.tasks[0].status).toBe('PROCESSING')

    await vi.advanceTimersByTimeAsync(2000)
    expect(store.tasks[0].status).toBe('PROCESSING')
    await vi.advanceTimersByTimeAsync(2000)
    expect(store.articles).toHaveLength(1)
    expect(store.tags).toHaveLength(1)
    expect(store.tasks[0].status).toBe('SUCCESS')
    expect(listTasks).toHaveBeenCalledWith({ status: 'PENDING,PROCESSING', page: 1, size: 1 })
    store.stopPolling()
  })

  it('重试调 API 并重启轮询,删除文章后刷新列表与标签', async () => {
    vi.mocked(retryTask).mockResolvedValue(task)
    vi.mocked(listTasks).mockResolvedValue(pageOf([task]))
    vi.mocked(listArticles).mockResolvedValue({ records: [], total: 0, size: 20, current: 1 })
    vi.mocked(listTags).mockResolvedValue([])
    vi.mocked(deleteArticle).mockResolvedValue(null)
    const store = useBaguwenStore()

    await store.retry(1)
    expect(retryTask).toHaveBeenCalledWith(1)

    await store.removeArticle(1)
    expect(deleteArticle).toHaveBeenCalledWith(1)
  })

  it('删除任务与批量清理失败任务后刷新任务页', async () => {
    vi.mocked(deleteTask).mockResolvedValue(null)
    vi.mocked(clearTasks).mockResolvedValue(2)
    vi.mocked(listTasks).mockResolvedValue(pageOf([task]))
    const store = useBaguwenStore()

    await store.removeTask(1)
    expect(deleteTask).toHaveBeenCalledWith(1)

    const deleted = await store.clearFailedTasks()
    expect(deleted).toBe(2)
    expect(clearTasks).toHaveBeenCalledWith('FAILED')
  })

  it('refreshTasks 发现有处理中任务时启动轮询并记录全局计数', async () => {
    const proc: ImportTask = { ...task, status: 'PROCESSING' }
    vi.mocked(listTasks)
      .mockResolvedValueOnce(pageOf([proc])) // loadTasksPage
      .mockResolvedValueOnce(pageOf([proc], 3, 1)) // 全局 pending 查询(total=3,大于当前页可见数)
    const store = useBaguwenStore()
    await store.refreshTasks()
    expect(listTasks).toHaveBeenCalledWith({ status: 'PENDING,PROCESSING', page: 1, size: 1 })
    expect(store.pendingCount).toBe(3)
    store.stopPolling()
  })

  it('任务分页状态随翻页加载', async () => {
    vi.mocked(listTasks).mockResolvedValue(pageOf([task], 15))
    const store = useBaguwenStore()
    await store.loadTasksPage()
    expect(store.taskTotal).toBe(15)
    store.taskFilter.page = 2
    await store.loadTasksPage()
    expect(listTasks).toHaveBeenLastCalledWith({ page: 2, size: 10 })
  })

  it('收藏转发 kbId 并返回 embedded 数', async () => {
    vi.mocked(favoriteArticle).mockResolvedValue({ embedded: 10 })
    const store = useBaguwenStore()
    const result = await store.favorite(1, 2)
    expect(favoriteArticle).toHaveBeenCalledWith(1, 2)
    expect(result.embedded).toBe(10)
  })
})
