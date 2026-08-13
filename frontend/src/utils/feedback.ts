import { createDiscreteApi, darkTheme } from 'naive-ui'

// 全局 message/dialog(spec 5.3「全局化提示」的落地:discrete api 不依赖组件上下文;
// 显式传 darkTheme,否则 discrete app 用默认亮色主题)
export const { message, dialog } = createDiscreteApi(['message', 'dialog'], {
  configProviderProps: { theme: darkTheme },
})
