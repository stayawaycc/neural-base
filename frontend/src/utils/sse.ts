import type { ChatRequest, RagSource } from '@/types'

export interface SseEvent {
  event: string
  data: string
}

/** SSE 文本流增量解析器(spec 6.1 事件协议;POST + Bearer/cookie 故不用 EventSource) */
export class SseParser {
  private buffer = ''
  private eventName = 'message'
  private dataLines: string[] = []

  push(chunk: string): SseEvent[] {
    const events: SseEvent[] = []
    this.buffer += chunk
    let idx: number
    while ((idx = this.buffer.indexOf('\n')) >= 0) {
      const line = this.buffer.slice(0, idx).replace(/\r$/, '')
      this.buffer = this.buffer.slice(idx + 1)
      if (line === '') {
        if (this.dataLines.length > 0) {
          events.push({ event: this.eventName, data: this.dataLines.join('\n') })
          this.dataLines = []
          this.eventName = 'message'
        }
      } else if (line.startsWith(':')) {
        // keep-alive 注释行,忽略
      } else if (line.startsWith('event:')) {
        this.eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        this.dataLines.push(line.slice(5).trimStart())
      }
    }
    return events
  }

  /** 流结束时把残存 data 行(无空行结尾)作为最后一个事件 */
  flush(): SseEvent[] {
    const tail = this.push('\n\n')
    this.buffer = ''
    this.dataLines = []
    this.eventName = 'message'
    return tail
  }
}

export interface StreamHandlers {
  onMeta?: (meta: { conversationId: number }) => void
  onSources?: (sources: RagSource[]) => void
  onDelta?: (text: string) => void
  onDone?: () => void
  onError?: (message: string) => void
}

function dispatch(ev: SseEvent, h: StreamHandlers) {
  switch (ev.event) {
    case 'meta':
      try {
        h.onMeta?.(JSON.parse(ev.data))
      } catch {
        /* 坏数据忽略 */
      }
      break
    case 'sources':
      try {
        h.onSources?.(JSON.parse(ev.data))
      } catch {
        /* 坏数据忽略 */
      }
      break
    case 'delta':
      h.onDelta?.(ev.data)
      break
    case 'done':
      h.onDone?.()
      break
    case 'error':
      h.onError?.('生成出错,请重试')
      break
  }
}

/** POST /api/chat/stream(fetch + ReadableStream;同源自动携带 cookie) */
export async function streamChat(
  payload: ChatRequest,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const resp = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    signal,
  })
  if (!resp.ok) {
    let message = `请求失败(${resp.status})`
    try {
      const body = await resp.json()
      message = body?.message || message
    } catch {
      /* 非 JSON 响应体,保留默认 message */
    }
    throw new Error(message)
  }
  if (!resp.body) throw new Error('响应无内容')

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  const parser = new SseParser()
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    for (const ev of parser.push(decoder.decode(value, { stream: true }))) dispatch(ev, handlers)
  }
  for (const ev of parser.flush()) dispatch(ev, handlers)
}
