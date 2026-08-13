import { describe, expect, it, vi } from 'vitest'
import { SseParser, streamChat } from '@/utils/sse'
import type { RagSource } from '@/types'

describe('SseParser', () => {
  it('解析完整事件序列 meta/sources/delta/done', () => {
    const parser = new SseParser()
    const raw = [
      'event: meta\ndata: {"conversationId":1}\n\n',
      'event: sources\ndata: [{"docId":2,"title":"t","content":"c","score":0.9}]\n\n',
      'event: delta\ndata: 你好\n\n',
      'event: done\ndata: \n\n',
    ].join('')
    const events = parser.push(raw)
    expect(events.map((e) => e.event)).toEqual(['meta', 'sources', 'delta', 'done'])
    expect(events[2].data).toBe('你好')
    expect(JSON.parse(events[0].data)).toEqual({ conversationId: 1 })
  })

  it('跨块断行与一行多事件拼接正确', () => {
    const parser = new SseParser()
    const first = parser.push('event: delta\ndata: hel')
    expect(first).toEqual([])
    const second = parser.push('lo\n\nevent: done\ndata: \n\n')
    expect(second.map((e) => e.event)).toEqual(['delta', 'done'])
    expect(second[0].data).toBe('hello')
  })

  it('忽略注释行,flush 处理无空行结尾', () => {
    const parser = new SseParser()
    expect(parser.push(': keep-alive\n')).toEqual([])
    expect(parser.push('data: tail')).toEqual([])
    expect(parser.flush()).toEqual([{ event: 'message', data: 'tail' }])
  })
})

describe('streamChat', () => {
  function mockFetchWithChunks(chunks: string[]) {
    const encoder = new TextEncoder()
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        for (const c of chunks) controller.enqueue(encoder.encode(c))
        controller.close()
      },
    })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(stream, { status: 200 })))
  }

  it('按事件调用对应 handler,delta 按序累积', async () => {
    mockFetchWithChunks(['event: meta\ndata: {"conversationId":7}\n\n', 'event: delta\ndata: 你\n', '\nevent: delta\ndata: 好\n\n'])
    const deltas: string[] = []
    let meta: unknown = null
    await streamChat(
      { question: 'hi' },
      {
        onMeta: (m) => (meta = m),
        onDelta: (t) => deltas.push(t),
      },
    )
    expect(meta).toEqual({ conversationId: 7 })
    expect(deltas.join('')).toBe('你好')
    expect(fetch).toHaveBeenCalledWith(
      '/api/chat/stream',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ question: 'hi' }) }),
    )
  })

  it('error 事件走 onError', async () => {
    mockFetchWithChunks(['event: error\ndata: stream error\n\n'])
    const onError = vi.fn()
    await streamChat({ question: 'hi' }, { onError })
    expect(onError).toHaveBeenCalledWith('生成出错,请重试')
  })

  it('非 2xx 抛出 Result.message', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 401, message: '未认证', data: null }), { status: 401 }),
      ),
    )
    await expect(streamChat({ question: 'hi' }, {})).rejects.toThrow('未认证')
  })

  it('sources 事件透传解析后的数组', async () => {
    mockFetchWithChunks([
      'event: sources\ndata: [{"docId":2,"title":"t","content":"c","score":0.9}]\n\n',
    ])
    const onSources = vi.fn()
    await streamChat({ question: 'hi' }, { onSources })
    expect(onSources).toHaveBeenCalledWith([
      { docId: 2, title: 't', content: 'c', score: 0.9 } as RagSource,
    ])
  })
})
