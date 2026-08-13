import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DocumentReaderView from '@/views/DocumentReaderView.vue'

const mockReplace = vi.fn()
const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { kbId: '1', docId: '11' } }),
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
  RouterLink: { template: '<a><slot /></a>' },
}))

vi.mock('@/api/kb', () => ({ listKbs: vi.fn(), createKb: vi.fn(), deleteKb: vi.fn() }))
vi.mock('@/api/document', () => ({
  listDocuments: vi.fn(),
  uploadDocument: vi.fn(),
  searchDocuments: vi.fn(),
  deleteDocument: vi.fn(),
  getDocumentContent: vi.fn(),
  documentFileUrl: (id: number) => `/api/document/${id}/file`,
}))

import { listKbs } from '@/api/kb'
import { listDocuments, getDocumentContent } from '@/api/document'

const kb = { id: 1, name: '我的知识库', createdAt: '' }
const readyDoc = { id: 11, kbId: 1, title: 'a.md', fileType: 'md', status: 'READY' as const, createdAt: '' }
const pendingDoc = { id: 12, kbId: 1, title: 'b.md', fileType: 'md', status: 'PENDING' as const, createdAt: '' }

function mountView() {
  return mount(DocumentReaderView, {
    global: {
      stubs: {
        'n-select': true,
        'n-tag': { template: '<span><slot /></span>' },
        'n-drawer': { template: '<div><slot /></div>' },
        'n-drawer-content': { template: '<div><slot /></div>' },
        'n-button': true,
      },
    },
  })
}

describe('DocumentReaderView 外壳', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(listKbs).mockResolvedValue([kb])
    vi.mocked(listDocuments).mockResolvedValue([readyDoc, pendingDoc])
    vi.mocked(getDocumentContent).mockResolvedValue({
      id: 11, title: 'a.md', fileType: 'md', content: '# 甲',
    })
  })

  it('挂载后加载 KB 与当前库文档,树里非 READY 显示状态徽标', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(listKbs).toHaveBeenCalled()
    expect(listDocuments).toHaveBeenCalled()
    expect(wrapper.text()).toContain('a.md')
    expect(wrapper.text()).toContain('排队中') // PENDING 徽标
  })

  it('点击 READY 文档以 router.replace 切换 docId,非 READY 不响应', async () => {
    const wrapper = mountView()
    await flushPromises()
    const ready = wrapper.findAll('.doc-item').find((n) => n.text().includes('a.md'))!
    await ready.trigger('click')
    expect(mockReplace).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'kb-reader', params: { kbId: '1', docId: '11' } }),
    )
    const pending = wrapper.findAll('.doc-item').find((n) => n.text().includes('b.md'))!
    await pending.trigger('click')
    expect(mockReplace).toHaveBeenCalledTimes(1)
  })

  it('无文档时显示空态引导', async () => {
    vi.mocked(listDocuments).mockResolvedValue([])
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('还没有文档')
  })
})
