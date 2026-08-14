import { ref } from 'vue'
import { defineStore } from 'pinia'
import { streamChat } from '@/utils/sse'
import * as conversationApi from '@/api/conversation'
import type { ConversationView, MessageFeedback, RagSource } from '@/types'

/** 流式期间本地新消息的占位 id:尚无服务端 id,不支持反馈 */
const NEW_MESSAGE_ID = 0

export interface ChatUiMessage {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  sources?: RagSource[]
  feedback?: MessageFeedback
  streaming?: boolean
}

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<ConversationView[]>([])
  const currentId = ref<number | null>(null)
  const messages = ref<ChatUiMessage[]>([])
  const streaming = ref(false)
  const streamError = ref<string | null>(null)

  let abortController: AbortController | null = null

  async function loadConversations() {
    conversations.value = await conversationApi.list()
  }

  // 加载历史消息;先停掉进行中的流避免串台
  async function openConversation(id: number) {
    stopStream()
    currentId.value = id
    const history = await conversationApi.messages(id)
    messages.value = history.map((m) => ({
      id: m.id,
      role: m.role,
      content: m.content,
      sources: m.sources,
      feedback: m.feedback,
    }))
    streamError.value = null
  }

  // 新建会话前停掉进行中的流
  function newConversation() {
    stopStream()
    currentId.value = null
    messages.value = []
    streamError.value = null
  }

  // 发送:本地先加 USER+空 ASSISTANT,流式回调更新;AbortError 不算错误
  async function send(question: string, kbId?: number) {
    if (streaming.value || !question.trim()) return
    streamError.value = null
    messages.value.push({ id: NEW_MESSAGE_ID, role: 'USER', content: question })
    messages.value.push({ id: NEW_MESSAGE_ID, role: 'ASSISTANT', content: '', streaming: true })
    streaming.value = true
    abortController = new AbortController()
    const assistant = lastAssistant()
    let aborted = false
    try {
      await streamChat(
        { conversationId: currentId.value ?? undefined, kbId, question },
        {
          onMeta: (meta) => {
            currentId.value = meta.conversationId
            void loadConversations().catch(() => {})
          },
          onSources: (sources) => {
            assistant.sources = sources
          },
          onDelta: (text) => {
            assistant.content += text
          },
          onError: (msg) => {
            streamError.value = msg
          },
        },
        abortController.signal,
      )
    } catch (err) {
      if ((err as Error).name === 'AbortError') {
        aborted = true
      } else {
        streamError.value = (err as Error).message
      }
    } finally {
      assistant.streaming = false
      streaming.value = false
      abortController = null
      // 成功流式结束后,本地占位消息回填服务端真实 id,否则 👍👎 永远不可用
      if (!aborted && !streamError.value && currentId.value != null &&
          messages.value.some((m) => m.id === NEW_MESSAGE_ID)) {
        await openConversation(currentId.value)
      }
    }
  }

  // 中止当前流(AbortController)
  function stopStream() {
    abortController?.abort()
  }

  async function removeConversation(id: number) {
    await conversationApi.remove(id)
    if (currentId.value === id) newConversation()
    await loadConversations()
  }

  async function renameConversation(id: number, title: string) {
    const updated = await conversationApi.renameConversation(id, title)
    const target = conversations.value.find((c) => c.id === id)
    if (target) target.title = updated.title
  }

  async function toggleFavorite(id: number) {
    const next = await conversationApi.toggleFavorite(id)
    const target = conversations.value.find((c) => c.id === id)
    if (target) target.favorite = next
  }

  async function setFeedback(index: number, feedback: MessageFeedback) {
    const msg = messages.value[index]
    if (!msg || msg.id === NEW_MESSAGE_ID) return // 流式中的本地消息尚无服务端 id,不支持反馈
    await conversationApi.setMessageFeedback(msg.id, feedback)
    msg.feedback = feedback
  }

  // 取末尾助手消息(流式回调的目标对象)
  function lastAssistant(): ChatUiMessage {
    const last = messages.value[messages.value.length - 1]
    if (last.role !== 'ASSISTANT') throw new Error('最后一条消息不是助手消息')
    return last
  }

  return {
    conversations,
    currentId,
    messages,
    streaming,
    streamError,
    loadConversations,
    openConversation,
    newConversation,
    send,
    stopStream,
    removeConversation,
    renameConversation,
    toggleFavorite,
    setFeedback,
  }
})
