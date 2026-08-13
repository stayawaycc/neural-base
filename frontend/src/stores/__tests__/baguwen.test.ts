import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useBaguwenStore } from '@/stores/baguwen'

vi.mock('@/api/baguwen', () => ({
  importUrls: vi.fn(),
  listTasks: vi.fn(),
  retryTask: vi.fn(),
  listArticles: vi.fn(),
  listTags: vi.fn(),
  deleteArticle: vi.fn(),
  favoriteArticle: vi.fn(),
}))

import {
  importUrls,
  listTasks,
  retryTask,
  listArticles,
  listTags,
  deleteArticle,
  favoriteArticle,
} from '@/api/baguwen'

const article = { id: 1, title: 'JVM 面试题', url: 'https://x', tags: ['Java'], createdAt: '' }
const task = {
  id: 1,
  url: 'https://x',
  status: 'SUCCESS' as const,
  attempt: 0,
  createdAt: '',
  updatedAt: '',
}

describe('baguwen store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  it('导入后刷新任务并轮询,全部落定后刷新文章与标签', async () => {
    vi.mocked(importUrls).mockResolvedValue([task])
    vi.mocked(listTasks)
      .mockResolvedValueOnce([{ ...task, status: 'PROCESSING' as const }])
      .mockResolvedValue([task])
    vi.mocked(listArticles).mockResolvedValue({ records: [article], total: 1, size: 20, current: 1 })
    vi.mocked(listTags).mockResolvedValue([{ tag: 'Java', count: 1 }])
    const store = useBaguwenStore()

    await store.importUrls(['https://x'])
    expect(store.tasks).toHaveLength(1)
    expect(store.tasks[0].status).toBe('PROCESSING')

    await vi.advanceTimersByTimeAsync(2000)
    expect(store.articles).toHaveLength(1)
    expect(store.tags).toHaveLength(1)
    store.stopPolling()
  })

  it('重试调 API 并重启轮询,删除文章后刷新列表与标签', async () => {
    vi.mocked(retryTask).mockResolvedValue(task)
    vi.mocked(listTasks).mockResolvedValue([task])
    vi.mocked(listArticles).mockResolvedValue({ records: [], total: 0, size: 20, current: 1 })
    vi.mocked(listTags).mockResolvedValue([])
    vi.mocked(deleteArticle).mockResolvedValue(null)
    const store = useBaguwenStore()

    await store.retry(1)
    expect(retryTask).toHaveBeenCalledWith(1)

    await store.removeArticle(1)
    expect(deleteArticle).toHaveBeenCalledWith(1)
  })

  it('收藏转发 kbId 并返回 embedded 数', async () => {
    vi.mocked(favoriteArticle).mockResolvedValue({ embedded: 10 })
    const store = useBaguwenStore()
    const result = await store.favorite(1, 2)
    expect(favoriteArticle).toHaveBeenCalledWith(1, 2)
    expect(result.embedded).toBe(10)
  })
})
