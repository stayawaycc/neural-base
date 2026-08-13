import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useChatStore } from '@/stores/chat'

vi.mock('@/api/conversation', () => ({
  list: vi.fn(),
  messages: vi.fn(),
  remove: vi.fn(),
}))

import { list, messages, remove } from '@/api/conversation'

const streamChatMock = vi.hoisted(() => vi.fn())
vi.mock('@/utils/sse', () => ({ streamChat: streamChatMock }))

describe('chat store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('send 流式追加:meta 回填会话、delta 累积、结束 streaming=false', async () => {
    streamChatMock.mockImplementation(async (_payload, handlers) => {
      handlers.onMeta?.({ conversationId: 9 })
      handlers.onDelta?.('你')
      handlers.onDelta?.('好')
      handlers.onDone?.()
    })
    vi.mocked(list).mockResolvedValue([])
    const store = useChatStore()

    await store.send('问题', undefined)

    expect(store.currentId).toBe(9)
    expect(store.messages.map((m) => m.role)).toEqual(['USER', 'ASSISTANT'])
    expect(store.messages[1].content).toBe('你好')
    expect(store.streaming).toBe(false)
    expect(store.messages[1].streaming).toBe(false)
    expect(store.messages[0].content).toBe('问题')
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

  it('加载历史消息与删除会话', async () => {
    vi.mocked(list).mockResolvedValue([
      { id: 1, title: '旧会话', createdAt: '', updatedAt: '' },
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
})
