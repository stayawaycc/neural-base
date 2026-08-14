import http from './request'
import type { NewsItem, NewsTask } from '@/types'

export const listNews = (params?: { category?: string; unreadOnly?: boolean }) =>
  http.get<NewsItem[]>('/news', params)

export const triggerCrawl = () => http.post<null>('/news/crawl')

export const latestTask = () => http.get<NewsTask | null>('/news/task/latest')

export const markRead = (id: number, isRead: boolean) =>
  http.put<null>(`/news/${id}/read`, { isRead })

export const markAllRead = () => http.put<null>('/news/read-all')

export const favoriteNews = (id: number, kbId: number) =>
  http.post<null>(`/news/${id}/favorite`, { kbId })
