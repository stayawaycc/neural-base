import http from './request'
import type { Kb } from '@/types'

export const listKbs = () => http.get<Kb[]>('/kb')
export const createKb = (payload: { name: string; description?: string; color?: string }) =>
  http.post<Kb>('/kb', payload)
export const updateKb = (id: number, payload: { name: string; description?: string; color?: string }) =>
  http.put<Kb>(`/kb/${id}`, payload)
export const deleteKb = (id: number) => http.del<null>(`/kb/${id}`)
