import { ref } from 'vue'
import { defineStore } from 'pinia'
import { streamChat } from '@/utils/sse'
import * as conversationApi from '@/api/conversation'
import type { ConversationView, RagSource } from '@/types'

export interface ChatUiMessage {
  role: 'USER' | 'ASSISTANT'
  content: string
  sources?: RagSource[]
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

  async function openConversation(id: number) {
    currentId.value = id
    const history = await conversationApi.messages(id)
    messages.value = history.map((m) => ({
      role: m.role,
      content: m.content,
      sources: m.sources,
    }))
    streamError.value = null
  }

  function newConversation() {
    stopStream()
    currentId.value = null
    messages.value = []
    streamError.value = null
  }

  async function send(question: string, kbId?: number) {
    if (streaming.value || !question.trim()) return
    streamError.value = null
    messages.value.push({ role: 'USER', content: question })
    messages.value.push({ role: 'ASSISTANT', content: '', streaming: true })
    streaming.value = true
    abortController = new AbortController()
    const assistant = lastAssistant()
    try {
      await streamChat(
        { conversationId: currentId.value ?? undefined, kbId, question },
        {
          onMeta: (meta) => {
            currentId.value = meta.conversationId
            void loadConversations()
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
      if ((err as Error).name !== 'AbortError') {
        streamError.value = (err as Error).message
      }
    } finally {
      assistant.streaming = false
      streaming.value = false
      abortController = null
    }
  }

  function stopStream() {
    abortController?.abort()
  }

  async function removeConversation(id: number) {
    await conversationApi.remove(id)
    if (currentId.value === id) newConversation()
    await loadConversations()
  }

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
  }
})
