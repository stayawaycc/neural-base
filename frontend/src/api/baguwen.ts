import http from './request'
import type {
  BaguwenArticleView,
  BaguwenDetail,
  FavoriteResult,
  ImportTask,
  ImportTaskStatus,
  Page,
  TagCount,
} from '@/types'

export const importUrls = (urls: string[]) => http.post<ImportTask[]>('/baguwen/import', { urls })

export const listTasks = (status?: ImportTaskStatus) =>
  http.get<ImportTask[]>('/baguwen/tasks', status ? { status } : undefined)

export const retryTask = (id: number) => http.post<ImportTask>(`/baguwen/tasks/${id}/retry`)

export const deleteTask = (id: number) => http.del<null>(`/baguwen/tasks/${id}`)

export const listArticles = (params: { tag?: string; keyword?: string; page: number; size: number }) =>
  http.get<Page<BaguwenArticleView>>('/baguwen', {
    tag: params.tag,
    keyword: params.keyword,
    page: params.page,
    size: params.size,
  })

export const articleDetail = (id: number) => http.get<BaguwenDetail>(`/baguwen/${id}`)

export const listTags = () => http.get<TagCount[]>('/baguwen/tags')

export const deleteArticle = (id: number) => http.del<null>(`/baguwen/${id}`)

export const favoriteArticle = (id: number, kbId: number) =>
  http.post<FavoriteResult>(`/baguwen/${id}/favorite`, { kbId })
