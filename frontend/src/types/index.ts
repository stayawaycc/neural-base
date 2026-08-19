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
  kbName?: string | null
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
  favorite: boolean
  createdAt: string
  updatedAt: string
  lastMessageContent?: string
}

export type MessageFeedback = 'LIKE' | 'DISLIKE' | null

export interface MessageView {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  sources?: RagSource[]
  feedback?: MessageFeedback
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

export interface NewsItem {
  id: number
  sourceId?: number
  sourceName?: string
  url?: string
  title: string
  category?: string
  summary?: string
  isRead: boolean
  createdAt: string
}

export interface NewsPage {
  items: NewsItem[]
  total: number
  page: number
  size: number
}

export interface NewsTask {
  id: number
  type: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
  progress: number
  error?: string
  createdAt: string
}

export type NewsSort = 'created_desc' | 'created_asc' | 'source' | 'category'

export interface NewsSource {
  id: number
  name: string
  url: string
  type: string
  selector?: string
  enabled: boolean
}
