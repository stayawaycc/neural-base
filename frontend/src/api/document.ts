import http from './request'
import type { DocumentContent, DocumentItem, SearchResult } from '@/types'

export const uploadDocument = (
  file: File,
  kbId?: number,
  onProgress?: (pct: number) => void,
) => {
  const form = new FormData()
  form.append('file', file)
  if (kbId != null) form.append('kbId', String(kbId))
  return http.post<DocumentItem>('/document', form, {
    onUploadProgress: (e) => {
      if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100))
    },
  })
}

export const searchDocuments = (query: string, kbId?: number, topK = 5) =>
  http.post<SearchResult[]>('/document/search', { query, kbId, topK })

export const listDocuments = (kbId?: number) =>
  http.get<DocumentItem[]>('/document', kbId != null ? { kbId } : undefined)

export const deleteDocument = (id: number) => http.del<null>(`/document/${id}`)

export const getDocumentContent = (id: number) =>
  http.get<DocumentContent>(`/document/${id}/content`)

// iframe 与下载链接用的同源相对路径(cookie 自动携带;dev 走 vite 代理)
export const documentFileUrl = (id: number) => `/api/document/${id}/file`
