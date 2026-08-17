import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useNewsStore } from '@/stores/news'

vi.mock('@/api/news', () => ({
  listNews: vi.fn(),
  triggerCrawl: vi.fn(),
  latestTask: vi.fn(),
  markRead: vi.fn(),
  markAllRead: vi.fn(),
  favoriteNews: vi.fn(),
  listSources: vi.fn(),
  createSource: vi.fn(),
  updateSource: vi.fn(),
  deleteSource: vi.fn(),
  cleanupNonAi: vi.fn(),
}))

import { cleanupNonAi, listNews, latestTask, markRead, triggerCrawl, listSources, createSource } from '@/api/news'

const item = {
  id: 1, title: 'cool', url: 'https://x', category: '工具',
  summary: 's', isRead: false, createdAt: '',
}

const pageOf = (items: typeof item[] = [item], total = items.length) =>
  ({ items, total, page: 1, size: 20 })

describe('news store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  it('load 填充列表与总数', async () => {
    vi.mocked(listNews).mockResolvedValue(pageOf([item]))
    const store = useNewsStore()
    await store.load()
    expect(store.items).toHaveLength(1)
    expect(store.total).toBe(1)
    expect(store.categories).toContain('工具')
  })

  it('triggerCrawl 后轮询任务直到非 RUNNING 自动停止', async () => {
    vi.mocked(listNews).mockResolvedValue(pageOf([item]))
    vi.mocked(triggerCrawl).mockResolvedValue(null)
    vi.mocked(latestTask)
      .mockResolvedValueOnce({ id: 1, type: 'DAILY_NEWS', status: 'RUNNING', progress: 10, createdAt: '' })
      .mockResolvedValue({ id: 1, type: 'DAILY_NEWS', status: 'SUCCESS', progress: 100, createdAt: '' })
    const store = useNewsStore()
    await store.triggerCrawl()
    expect(triggerCrawl).toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(2000)
    expect(store.task?.status).toBe('SUCCESS')
    await vi.advanceTimersByTimeAsync(2000)
    expect(latestTask).toHaveBeenCalledTimes(2) // 自动停止
    store.stopPolling()
  })

  it('markRead 更新本地并调接口', async () => {
    vi.mocked(listNews).mockResolvedValue(pageOf([item]))
    vi.mocked(markRead).mockResolvedValue(null)
    const store = useNewsStore()
    await store.load()
    await store.markRead(1, true)
    expect(markRead).toHaveBeenCalledWith(1, true)
    expect(store.items[0].isRead).toBe(true)
  })

  it('sort 与 sourceFilter 变化触发带参 load', async () => {
    vi.mocked(listNews).mockResolvedValue(pageOf([item]))
    const store = useNewsStore()
    await store.load()
    store.sort = 'source'
    await vi.advanceTimersByTimeAsync(0)
    expect(listNews).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'source' }))
    store.sourceFilter = 1
    await vi.advanceTimersByTimeAsync(0)
    expect(listNews).toHaveBeenLastCalledWith(expect.objectContaining({ sourceId: 1 }))
  })

  it('category 变化触发带参 load 且回到第 1 页', async () => {
    vi.mocked(listNews).mockResolvedValue(pageOf([item]))
    const store = useNewsStore()
    await store.load()
    store.page = 3
    await vi.advanceTimersByTimeAsync(0)
    expect(listNews).toHaveBeenLastCalledWith(expect.objectContaining({ page: 3 }))
    store.filterCategory = '大模型'
    await vi.advanceTimersByTimeAsync(0)
    expect(store.page).toBe(1)
    expect(listNews).toHaveBeenLastCalledWith(expect.objectContaining({ category: '大模型', page: 1 }))
  })

  it('page 变化触发带参 load', async () => {
    vi.mocked(listNews).mockResolvedValue(pageOf([item], 41))
    const store = useNewsStore()
    await store.load()
    store.page = 2
    await vi.advanceTimersByTimeAsync(0)
    expect(listNews).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2, size: 20 }))
  })

  it('cleanupNonAi 调接口并刷新列表', async () => {
    vi.mocked(listNews).mockResolvedValue(pageOf([item]))
    vi.mocked(cleanupNonAi).mockResolvedValue(5)
    const store = useNewsStore()
    await store.load()
    const deleted = await store.cleanupNonAi()
    expect(deleted).toBe(5)
    expect(cleanupNonAi).toHaveBeenCalled()
    expect(listNews).toHaveBeenCalledTimes(2) // load + 清理后刷新
  })

  it('爬取完成(RUNNING→SUCCESS)自动刷新列表', async () => {
    vi.mocked(listNews).mockResolvedValue(pageOf([item]))
    vi.mocked(triggerCrawl).mockResolvedValue(null)
    vi.mocked(latestTask)
      .mockResolvedValueOnce({ id: 1, type: 'DAILY_NEWS', status: 'RUNNING', progress: 10, createdAt: '' })
      .mockResolvedValue({ id: 1, type: 'DAILY_NEWS', status: 'SUCCESS', progress: 100, createdAt: '' })
    const store = useNewsStore()
    await store.triggerCrawl()
    await vi.advanceTimersByTimeAsync(2000)
    expect(listNews).toHaveBeenCalled() // 完成后自动 load
    store.stopPolling()
  })

  it('来源 CRUD 调用接口', async () => {
    vi.mocked(listSources).mockResolvedValue([
      { id: 1, name: 'InfoQ', url: 'https://x', type: 'html', enabled: true },
    ])
    vi.mocked(createSource).mockResolvedValue({
      id: 9, name: '新源', url: 'https://y', type: 'html', selector: 'ul a', enabled: true,
    })
    const store = useNewsStore()
    await store.loadSources()
    expect(store.sources).toHaveLength(1)
    await store.createSource({ name: '新源', url: 'https://y', selector: 'ul a' })
    expect(createSource).toHaveBeenCalled()
  })
})
