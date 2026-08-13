import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useThemeStore } from '@/stores/theme'

describe('theme store', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
    setActivePinia(createPinia())
  })

  it('默认亮色:无记忆时 theme=light 且 html 无 dark 类', () => {
    const store = useThemeStore()
    expect(store.theme).toBe('light')
    expect(store.isDark).toBe(false)
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('toggleTheme 切换并写入 localStorage 与 html.dark', () => {
    const store = useThemeStore()
    store.toggleTheme()
    expect(store.theme).toBe('dark')
    expect(localStorage.getItem('nb-theme')).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)

    store.toggleTheme()
    expect(store.theme).toBe('light')
    expect(localStorage.getItem('nb-theme')).toBe('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('初始化从 localStorage 恢复 dark', () => {
    localStorage.setItem('nb-theme', 'dark')
    const store = useThemeStore()
    expect(store.isDark).toBe(true)
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('非法存储值回退 light', () => {
    localStorage.setItem('nb-theme', 'oops')
    const store = useThemeStore()
    expect(store.theme).toBe('light')
  })
})
