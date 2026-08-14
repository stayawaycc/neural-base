import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AppHeader from '@/components/AppHeader.vue'
import { useThemeStore } from '@/stores/theme'
import { useUserStore } from '@/stores/user'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ path: '/' }),
  RouterLink: { template: '<a><slot /></a>' },
}))

// user store → api/auth → request.ts → @/router 依赖链,截断以避免加载真实路由
vi.mock('@/api/auth', () => ({ login: vi.fn(), logout: vi.fn(), me: vi.fn() }))

describe('AppHeader 主题切换', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
    setActivePinia(createPinia())
    useUserStore().user = { id: 1, username: 'admin', role: 'ADMIN', createdAt: '' }
  })

  it('点击切换按钮翻转主题并同步 html.dark', async () => {
    const wrapper = mount(AppHeader, {
      global: {
        stubs: {
          'n-dropdown': { template: '<div><slot /></div>' },
          Icon: { template: '<i />' },
        },
      },
    })
    const themeStore = useThemeStore()
    expect(themeStore.isDark).toBe(false)
    await wrapper.find('button.theme-toggle').trigger('click')
    expect(themeStore.isDark).toBe(true)
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })
})
