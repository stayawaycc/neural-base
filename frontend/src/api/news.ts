import http from './request'
import type { NewsPage, NewsSort, NewsSource, NewsTask } from '@/types'

export const listNews = (params?: {
  category?: string
  unreadOnly?: boolean
  sourceId?: number
  sort?: NewsSort
  page?: number
  size?: number
}) => http.get<NewsPage>('/news', params)

export const triggerCrawl = () => http.post<null>('/news/crawl')

export const latestTask = () => http.get<NewsTask | null>('/news/task/latest')

export const cleanupNonAi = () => http.post<number>('/news/cleanup')

export const markRead = (id: number, isRead: boolean) =>
  http.put<null>(`/news/${id}/read`, { isRead })

export const markAllRead = () => http.put<null>('/news/read-all')

export const favoriteNews = (id: number, kbId: number) =>
  http.post<null>(`/news/${id}/favorite`, { kbId })

export const listSources = () => http.get<NewsSource[]>('/news/source')

export const createSource = (payload: { name: string; url: string; selector?: string; enabled?: boolean }) =>
  http.post<NewsSource>('/news/source', payload)

export const updateSource = (id: number, payload: { name?: string; url?: string; selector?: string; enabled?: boolean }) =>
  http.put<NewsSource>(`/news/source/${id}`, payload)

export const deleteSource = (id: number) => http.del<null>(`/news/source/${id}`)
