import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const KEY = 'nb-theme'

function readStored(): 'light' | 'dark' {
  return localStorage.getItem(KEY) === 'dark' ? 'dark' : 'light'
}

export const useThemeStore = defineStore('theme', () => {
  const theme = ref<'light' | 'dark'>(readStored())
  const isDark = computed(() => theme.value === 'dark')

  function apply() {
    document.documentElement.classList.toggle('dark', isDark.value)
  }

  function toggleTheme() {
    theme.value = isDark.value ? 'light' : 'dark'
    localStorage.setItem(KEY, theme.value)
    apply()
  }

  apply() // 初始化即同步 html.dark

  return { theme, isDark, toggleTheme }
})
