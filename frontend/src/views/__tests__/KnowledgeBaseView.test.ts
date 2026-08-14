import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import KnowledgeBaseView from '@/views/KnowledgeBaseView.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
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
vi.mock('@/components/FileUploader.vue', () => ({
  default: { template: '<div class="uploader-stub" />' },
}))

import { listKbs } from '@/api/kb'
import { listDocuments } from '@/api/document'
import type { DocumentItem } from '@/types'

function mountView() {
  return mount(KnowledgeBaseView, {
    global: {
      stubs: {
        'n-select': true,
        'n-tag': { template: '<span><slot /></span>' },
        'n-button': true,
        'n-input': true,
        'n-modal': true,
        'n-popconfirm': true,
        Icon: { template: '<i />' },
        teleport: true,
      },
    },
  })
}

describe('KnowledgeBaseView 骨架屏', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('文档列表加载中显示骨架,数据到达后消失并渲染列表', async () => {
    vi.mocked(listKbs).mockResolvedValue([{ id: 1, name: 'kb', createdAt: '' }])
    let resolveDocs!: (v: DocumentItem[]) => void
    vi.mocked(listDocuments).mockImplementation(
      () => new Promise<DocumentItem[]>((r) => (resolveDocs = r)),
    )
    const wrapper = mountView()
    await flushPromises()
    // listKbs 已返回、listDocuments 仍挂起 → 骨架可见
    expect(wrapper.find('.n-skeleton').exists()).toBe(true)

    resolveDocs([{ id: 1, kbId: 1, title: 'a.md', fileType: 'md', status: 'READY', createdAt: '' }])
    await flushPromises()
    expect(wrapper.find('.n-skeleton').exists()).toBe(false)
    expect(wrapper.text()).toContain('a.md')
  })
})
