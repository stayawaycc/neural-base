// 后端 DTO 的 TS 镜像(spec 第六节 API 契约)
export interface Result<T> {
  code: number
  message: string
  data: T
}

export interface User {
  id: number
  username: string
  role: string
  createdAt: string
}

export interface Kb {
  id: number
  name: string
  description?: string
  color?: string
  createdAt: string
}

export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'

export interface DocumentItem {
  id: number
  kbId?: number
  title: string
  fileType: string
  sourceUrl?: string
  status: DocumentStatus
  chunkCount?: number
  error?: string
  createdAt: string
}

export interface DocumentContent {
  id: number
  title: string
  fileType: string
  content: string
}

export interface SearchResult {
  docId: number
  title: string | null
  content: string
  score: number
}

export interface ChatRequest {
  conversationId?: number
  kbId?: number
  question: string
}

export interface RagSource {
  docId: number
  title: string
  content: string
  score: number
}

export interface ConversationView {
  id: number
  title: string
  kbId?: number
  createdAt: string
  updatedAt: string
  lastMessageContent?: string
}

export interface MessageView {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  sources?: RagSource[]
  createdAt: string
}

export interface Page<T> {
  records: T[]
  total: number
  size: number
  current: number
}

export interface BaguwenArticleView {
  id: number
  title: string
  url: string
  tags: string[]
  createdAt: string
}

export interface QaPairView {
  id: number
  question: string
  answer: string
  difficulty: string
}

export interface BaguwenDetail {
  id: number
  title: string
  url: string
  content: string
  tags: string[]
  createdAt: string
  qaPairs: QaPairView[]
}

export type ImportTaskStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED'

export interface ImportTask {
  id: number
  url: string
  status: ImportTaskStatus
  errorMsg?: string
  articleId?: number
  attempt: number
  createdAt: string
  updatedAt: string
}

export interface TagCount {
  tag: string
  count: number
}

export interface FavoriteResult {
  embedded: number
}
