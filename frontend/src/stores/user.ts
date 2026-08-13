import { ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import type { User } from '@/types'

// 登录态由 HttpOnly cookie 承载,前端不存 token(spec 二、认证方案)
export const useUserStore = defineStore('user', () => {
  const user = ref<User | null>(null)
  const initialized = ref(false)

  async function login(username: string, password: string) {
    await authApi.login(username, password) // 后端 Set-Cookie
    await fetchMe()
  }

  async function fetchMe() {
    try {
      user.value = await authApi.me()
    } catch {
      user.value = null
    } finally {
      initialized.value = true
    }
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // 即使后端失败也清空本地登录态
    }
    user.value = null
  }

  return { user, initialized, login, fetchMe, logout }
})
