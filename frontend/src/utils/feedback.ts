import { createDiscreteApi, darkTheme, lightTheme } from 'naive-ui'
import type { GlobalTheme } from 'naive-ui'

// 全局 message/dialog(discrete api 不依赖组件上下文)。主题随 store 切换:
// 重建 discrete api 并重赋值模块级导出,导入方通过 ESM live binding 拿到新实例。
function build(theme: GlobalTheme | null) {
  return createDiscreteApi(['message', 'dialog'], {
    configProviderProps: { theme },
  })
}

let api = build(darkTheme)
export let message = api.message
export let dialog = api.dialog

export function applyFeedbackTheme(isDark: boolean) {
  api = build(isDark ? darkTheme : lightTheme)
  message = api.message
  dialog = api.dialog
}
