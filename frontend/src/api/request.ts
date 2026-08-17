import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import router from '@/router'
import { useUserStore } from '@/stores/user'
import type { Result } from '@/types'

// 统一拦截器:解 Result 壳 + 401 跳登录(spec 第八节)
const client = axios.create({ baseURL: '/api', timeout: 30_000 })

client.interceptors.response.use(
  (resp) => {
    const body = resp.data as Result<unknown>
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body.data as never
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body as never
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message
    if (status === 401) {
      useUserStore().user = null
      if (router.currentRoute.value.name !== 'login') {
        void router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
      }
    }
    return Promise.reject(new Error(message || error.message || '网络错误'))
  },
)

// 各 api 模块统一入口:返回值为解壳后的 data
export const http = {
  get: <T>(url: string, params?: Record<string, unknown>) =>
    client.get(url, { params }) as Promise<T>,
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    client.post(url, data, config) as Promise<T>,
  put: <T>(url: string, data?: unknown) => client.put(url, data) as Promise<T>,
  del: <T>(url: string, params?: Record<string, unknown>) =>
    client.delete(url, { params }) as Promise<T>,
}

export default http
