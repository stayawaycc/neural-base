import http from './request'
import type { ConversationView, MessageView } from '@/types'

export const create = (title?: string, kbId?: number) =>
  http.post<ConversationView>('/conversation', { title, kbId })

export const list = () => http.get<ConversationView[]>('/conversation')

export const messages = (id: number) => http.get<MessageView[]>(`/conversation/${id}/messages`)

export const remove = (id: number) => http.del<null>(`/conversation/${id}`)
