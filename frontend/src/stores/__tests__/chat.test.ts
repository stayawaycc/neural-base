import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useChatStore } from '@/stores/chat'

vi.mock('@/api/conversation', () => ({
  list: vi.fn(),
  messages: vi.fn(),
  remove: vi.fn(),
  renameConversation: vi.fn(),
  toggleFavorite: vi.fn(),
  setMessageFeedback: vi.fn(),
}))

import { list, messages, remove, renameConversation, toggleFavorite, setMessageFeedback } from '@/api/conversation'

const streamChatMock = vi.hoisted(() => vi.fn())
vi.mock('@/utils/sse', () => ({ streamChat: streamChatMock }))

describe('chat store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('send 流式追加:meta 回填会话、delta 累积、结束回填真实 id', async () => {
    streamChatMock.mockImplementation(async (_payload, handlers) => {
      handlers.onMeta?.({ conversationId: 9 })
      handlers.onDelta?.('你')
      handlers.onDelta?.('好')
      handlers.onDone?.()
    })
    vi.mocked(list).mockResolvedValue([])
    vi.mocked(messages).mockResolvedValue([
      { id: 91, role: 'USER', content: '问题', createdAt: '' },
      { id: 92, role: 'ASSISTANT', content: '你好', createdAt: '' },
    ])
    const store = useChatStore()

    await store.send('问题', undefined)

    expect(store.currentId).toBe(9)
    expect(messages).toHaveBeenCalledWith(9) // 流式结束回填服务端真实 id
    expect(store.messages.map((m) => m.role)).toEqual(['USER', 'ASSISTANT'])
    expect(store.messages[1].content).toBe('你好')
    expect(store.messages[0].id).toBe(91)
    expect(store.streaming).toBe(false)
  })

  it('中止不视为错误且保留已生成内容', async () => {
    streamChatMock.mockImplementation((_payload, _handlers, signal) =>
      new Promise((_resolve, reject) => {
        signal?.addEventListener('abort', () =>
          reject(Object.assign(new Error('aborted'), { name: 'AbortError' })),
        )
      }),
    )
    const store = useChatStore()
    const sendPromise = store.send('hi')
    store.messages[1].content = '已生成的部分'
    store.stopStream()
    await sendPromise

    expect(store.streaming).toBe(false)
    expect(store.streamError).toBeNull()
    expect(store.messages[1].content).toBe('已生成的部分')
  })

  it('流出错写入 streamError,onError 事件同样处理', async () => {
    streamChatMock.mockImplementation(async (_payload, handlers) => {
      handlers.onDelta?.('部分')
      handlers.onError?.('生成出错,请重试')
    })
    const store = useChatStore()
    await store.send('hi')
    expect(store.streamError).toBe('生成出错,请重试')
    expect(store.messages[1].content).toBe('部分')
  })

  it('非 Abort 异常写入 streamError', async () => {
    streamChatMock.mockRejectedValue(new Error('网络错误'))
    const store = useChatStore()
    await store.send('hi')
    expect(store.streamError).toBe('网络错误')
    expect(store.streaming).toBe(false)
    expect(store.messages).toHaveLength(2)
  })

  it('加载历史消息与删除会话', async () => {
    vi.mocked(list).mockResolvedValue([
      { id: 1, title: '旧会话', favorite: false, createdAt: '', updatedAt: '' },
    ])
    vi.mocked(messages).mockResolvedValue([
      { id: 1, role: 'USER', content: '你好', createdAt: '' },
      { id: 2, role: 'ASSISTANT', content: '你好呀', createdAt: '' },
    ])
    vi.mocked(remove).mockResolvedValue(null)
    const store = useChatStore()

    await store.loadConversations()
    expect(store.conversations).toHaveLength(1)

    await store.openConversation(1)
    expect(store.messages).toHaveLength(2)
    expect(store.currentId).toBe(1)

    await store.removeConversation(1)
    expect(remove).toHaveBeenCalledWith(1)
    expect(store.currentId).toBeNull()
  })

  it('renameConversation 更新列表标题', async () => {
    vi.mocked(renameConversation).mockResolvedValue({
      id: 1, title: '新名', favorite: false, createdAt: '', updatedAt: '',
    })
    const store = useChatStore()
    store.conversations = [{ id: 1, title: '旧名', favorite: false, createdAt: '', updatedAt: '' }]
    await store.renameConversation(1, '新名')
    expect(renameConversation).toHaveBeenCalledWith(1, '新名')
    expect(store.conversations[0].title).toBe('新名')
  })

  it('toggleFavorite 切换并更新列表', async () => {
    vi.mocked(toggleFavorite).mockResolvedValue(true)
    const store = useChatStore()
    store.conversations = [{ id: 1, title: 'a', favorite: false, createdAt: '', updatedAt: '' }]
    await store.toggleFavorite(1)
    expect(store.conversations[0].favorite).toBe(true)
  })

  it('setFeedback 写库并更新本地消息', async () => {
    vi.mocked(setMessageFeedback).mockResolvedValue(null)
    const store = useChatStore()
    store.messages = [{ id: 9, role: 'ASSISTANT', content: 'hi' }]
    await store.setFeedback(0, 'LIKE')
    expect(setMessageFeedback).toHaveBeenCalledWith(9, 'LIKE')
    expect(store.messages[0].feedback).toBe('LIKE')
  })
})
