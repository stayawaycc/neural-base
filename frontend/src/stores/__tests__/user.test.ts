import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/stores/user'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  me: vi.fn(),
}))

import { login, logout, me } from '@/api/auth'

describe('user store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('登录成功写入 user 且 initialized 为 true', async () => {
    vi.mocked(me).mockResolvedValue({ id: 1, username: 'admin', role: 'ADMIN', createdAt: '' })
    vi.mocked(login).mockResolvedValue('token')
    const store = useUserStore()
    await store.login('admin', 'admin123')
    expect(store.user?.username).toBe('admin')
    expect(store.initialized).toBe(true)
  })

  it('fetchMe 失败时 user 为 null 不抛错', async () => {
    vi.mocked(me).mockRejectedValue(new Error('未认证'))
    const store = useUserStore()
    await store.fetchMe()
    expect(store.user).toBeNull()
    expect(store.initialized).toBe(true)
  })

  it('登出清空 user', async () => {
    vi.mocked(me).mockResolvedValue({ id: 1, username: 'admin', role: 'ADMIN', createdAt: '' })
    vi.mocked(logout).mockResolvedValue(null)
    const store = useUserStore()
    await store.fetchMe()
    expect(store.user).not.toBeNull()
    await store.logout()
    expect(store.user).toBeNull()
    expect(logout).toHaveBeenCalled()
  })
})
