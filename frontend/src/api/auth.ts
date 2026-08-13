import http from './request'
import type { User } from '@/types'

// login 成功后 token 由后端 Set-Cookie(HttpOnly)携带,body token 前端忽略
export const login = (username: string, password: string) =>
  http.post<string>('/auth/login', { username, password })

export const logout = () => http.post<null>('/auth/logout')

export const me = () => http.get<User>('/auth/me')
