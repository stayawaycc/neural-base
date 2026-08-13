import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DocumentReaderView from '@/views/DocumentReaderView.vue'

const mockReplace = vi.fn()
const mockPush = vi.fn()
// 可变路由参数:各用例按需改 docId(vi.mock 工厂被提升,变量须走 vi.hoisted)
const routeParams = vi.hoisted(() => ({ kbId: '1', docId: '11' as string | undefined }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: routeParams }),
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
        // NDrawer 内容走 teleport(wrapper 之外),测试里内联渲染才能被 findAll 找到
        teleport: true,
        'n-select': true,
        'n-tag': { template: '<span><slot /></span>' },
        'n-button': true,
      },
    },
  })
}

describe('DocumentReaderView 外壳', () => {
  beforeEach(() => {
    routeParams.kbId = '1'
    routeParams.docId = '11'
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

  it('md 内容用 MarkdownRenderer 渲染并开锚点', async () => {
    const wrapper = mountView()
    await flushPromises()
    const md = wrapper.findComponent({ name: 'MarkdownRenderer' })
    expect(md.exists()).toBe(true)
    expect(md.props('anchorHeadings')).toBe(true)
  })

  it('BAGUWEN 收藏文档按纯文本渲染,显示原文外链,无下载按钮', async () => {
    routeParams.docId = '21'
    vi.mocked(getDocumentContent).mockResolvedValue({
      id: 21, title: '面试题', fileType: 'BAGUWEN', content: 'Q: 什么是JVM?\nA: 运行时。',
    })
    vi.mocked(listDocuments).mockResolvedValue([
      { id: 21, kbId: 1, title: '面试题', fileType: 'BAGUWEN', status: 'READY' as const, sourceUrl: 'https://example.com/a', createdAt: '' },
    ])
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('pre').text()).toContain('Q: 什么是JVM?')
    expect(wrapper.find('a[href="https://example.com/a"]').exists()).toBe(true)
    expect(wrapper.find('a.download-link').exists()).toBe(false)
  })

  it('pdf 内容用 iframe 指向原文件接口,并显示下载按钮', async () => {
    routeParams.docId = '31'
    vi.mocked(getDocumentContent).mockResolvedValue({
      id: 31, title: 'p.pdf', fileType: 'pdf', content: 'x',
    })
    vi.mocked(listDocuments).mockResolvedValue([
      { id: 31, kbId: 1, title: 'p.pdf', fileType: 'pdf', status: 'READY' as const, createdAt: '' },
    ])
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('iframe').attributes('src')).toBe('/api/document/31/file')
    expect(wrapper.find('a.download-link').exists()).toBe(true)
  })

  it('内容接口报错时提示并清空 docId', async () => {
    vi.mocked(getDocumentContent).mockRejectedValue(new Error('文档不存在: 11'))
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('文档不存在: 11')
    expect(mockReplace).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'kb-reader', params: { kbId: '1' } }),
    )
  })

  it('文档不存在时刷新树(重新拉取文档列表)', async () => {
    vi.mocked(getDocumentContent).mockRejectedValue(new Error('文档不存在: 11'))
    const wrapper = mountView()
    await flushPromises()
    // 挂载时拉一次 + 报错路径刷新一次
    expect(listDocuments).toHaveBeenCalledTimes(2)
  })

  it('跨库直链访问时清空不属于当前库的 docId', async () => {
    routeParams.kbId = '2'
    vi.mocked(listDocuments).mockResolvedValue([
      { id: 99, kbId: 2, title: 'other.md', fileType: 'md', status: 'READY' as const, createdAt: '' },
    ])
    const wrapper = mountView()
    await flushPromises()
    expect(mockReplace).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'kb-reader', params: { kbId: '2' } }),
    )
  })

  it('md 文档显示目录按钮,打开抽屉列出大纲并可点击跳转', async () => {
    const scrollIntoView = vi.fn()
    Element.prototype.scrollIntoView = scrollIntoView
    const wrapper = mountView()
    await flushPromises()
    const btn = wrapper.find('button.outline-trigger')
    expect(btn.exists()).toBe(true)
    await btn.trigger('click')
    await flushPromises()
    const items = wrapper.findAll('button.outline-item')
    expect(items.map((n) => n.text())).toEqual(['甲'])
    await items[0].trigger('click')
    expect(scrollIntoView).toHaveBeenCalled()
  })

  it('非 md 文档不显示目录按钮', async () => {
    routeParams.docId = '41'
    vi.mocked(getDocumentContent).mockResolvedValue({
      id: 41, title: 'n.txt', fileType: 'txt', content: '纯文本',
    })
    vi.mocked(listDocuments).mockResolvedValue([
      { id: 41, kbId: 1, title: 'n.txt', fileType: 'txt', status: 'READY' as const, createdAt: '' },
    ])
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('button.outline-trigger').exists()).toBe(false)
  })
})
