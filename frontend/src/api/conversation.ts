import http from './request'
import type { ConversationView, MessageFeedback, MessageView } from '@/types'

export const create = (title?: string, kbId?: number) =>
  http.post<ConversationView>('/conversation', { title, kbId })

export const list = () => http.get<ConversationView[]>('/conversation')

export const messages = (id: number) => http.get<MessageView[]>(`/conversation/${id}/messages`)

export const remove = (id: number) => http.del<null>(`/conversation/${id}`)

export const renameConversation = (id: number, title: string) =>
  http.put<ConversationView>(`/conversation/${id}`, { title })

export const toggleFavorite = (id: number) =>
  http.put<boolean>(`/conversation/${id}/favorite`)

export const setMessageFeedback = (id: number, feedback: MessageFeedback) =>
  http.post<null>(`/message/${id}/feedback`, { feedback })
