import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useDocumentStore } from '@/stores/document'

vi.mock('@/api/kb', () => ({
  listKbs: vi.fn(),
  createKb: vi.fn(),
  deleteKb: vi.fn(),
}))

vi.mock('@/api/document', () => ({
  listDocuments: vi.fn(),
  uploadDocument: vi.fn(),
  searchDocuments: vi.fn(),
  deleteDocument: vi.fn(),
}))

import { listKbs, createKb, deleteKb } from '@/api/kb'
import { listDocuments, uploadDocument, searchDocuments, deleteDocument } from '@/api/document'

const kb = { id: 1, name: '我的知识库', createdAt: '' }
const readyDoc = {
  id: 11,
  title: 'a.md',
  fileType: 'md',
  status: 'READY' as const,
  createdAt: '',
}

describe('document store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  it('加载 KB 与文档,selectKb 切换并清空搜索结果', async () => {
    vi.mocked(listKbs).mockResolvedValue([kb])
    vi.mocked(listDocuments).mockResolvedValue([readyDoc])
    const store = useDocumentStore()

    await store.loadKbs()
    await store.loadDocuments()
    expect(store.kbs).toHaveLength(1)
    expect(store.documents).toHaveLength(1)

    store.searchResults = [{ docId: 11, title: 'a.md', content: 'x', score: 0.9 }]
    store.selectKb(1)
    expect(store.currentKbId).toBe(1)
    expect(store.searchResults).toBeNull()
  })

  it('上传后立即加载并启动轮询,全部就绪后自动停止', async () => {
    vi.mocked(listKbs).mockResolvedValue([kb])
    vi.mocked(uploadDocument).mockResolvedValue(readyDoc)
    vi.mocked(listDocuments)
      .mockResolvedValueOnce([{ ...readyDoc, status: 'PROCESSING' as const }])
      .mockResolvedValue([readyDoc])
    const store = useDocumentStore()

    await store.upload(new File(['x'], 'a.md'))
    expect(uploadDocument).toHaveBeenCalled()
    expect(listDocuments).toHaveBeenCalledTimes(1) // 上传成功后刷新一次(PROCESSING)

    await vi.advanceTimersByTimeAsync(2000)
    expect(listDocuments).toHaveBeenCalledTimes(2) // 轮询一次发现 READY,自动停止
    expect(store.documents.every((d) => d.status === 'READY')).toBe(true)

    await vi.advanceTimersByTimeAsync(2000)
    expect(listDocuments).toHaveBeenCalledTimes(2) // 自动停止生效,不再轮询
    store.stopPolling()
  })

  it('删除文档后刷新列表', async () => {
    vi.mocked(deleteDocument).mockResolvedValue(null)
    vi.mocked(listDocuments).mockResolvedValue([])
    const store = useDocumentStore()
    await store.remove(11)
    expect(deleteDocument).toHaveBeenCalledWith(11)
    expect(store.documents).toHaveLength(0)
  })

  it('搜索写入 searchResults,建库与删库调 API', async () => {
    vi.mocked(searchDocuments).mockResolvedValue([{ docId: 11, title: 'a.md', content: 'x', score: 0.9 }])
    vi.mocked(createKb).mockResolvedValue(kb)
    vi.mocked(deleteKb).mockResolvedValue(null)
    const store = useDocumentStore()

    await store.search('问题')
    expect(store.searchResults).toHaveLength(1)

    await store.createKb({ name: '新库' })
    expect(createKb).toHaveBeenCalledWith({ name: '新库' })
    await store.removeKb(1)
    expect(deleteKb).toHaveBeenCalledWith(1)
  })

  it('删除当前选中的知识库后回退到全部文档', async () => {
    vi.mocked(listKbs).mockResolvedValue([kb])
    vi.mocked(deleteKb).mockResolvedValue(null)
    vi.mocked(listDocuments).mockResolvedValue([])
    const store = useDocumentStore()

    store.selectKb(1)
    expect(store.currentKbId).toBe(1)

    await store.removeKb(1)
    expect(deleteKb).toHaveBeenCalledWith(1)
    expect(store.currentKbId).toBeNull()
  })
})
