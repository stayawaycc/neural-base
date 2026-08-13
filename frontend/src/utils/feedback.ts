import { createDiscreteApi, darkTheme, lightTheme } from 'naive-ui'
import type { GlobalTheme } from 'naive-ui'

// 全局 message/dialog(discrete api 不依赖组件上下文)。主题随 store 切换:
// 重建 discrete api 并重赋值模块级导出,导入方通过 ESM live binding 拿到新实例。
// 初始主题直接读 localStorage(与 theme store 的 readStored 一致,不依赖挂载时序)。
function buildApi(theme: GlobalTheme) {
  return createDiscreteApi(['message', 'dialog'], {
    configProviderProps: { theme },
  })
}

let api = buildApi(localStorage.getItem('nb-theme') === 'dark' ? darkTheme : lightTheme)
export let message = api.message
export let dialog = api.dialog

export function applyFeedbackTheme(isDark: boolean) {
  api.unmount() // 移除旧实例挂载的容器,避免每次切换向 body 累积空节点
  api = buildApi(isDark ? darkTheme : lightTheme)
  message = api.message
  dialog = api.dialog
}
