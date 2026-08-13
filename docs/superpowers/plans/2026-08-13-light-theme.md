# 亮色模式 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增亮色主题(默认亮色,柔和浅灰 #f5f6f8 + 白卡片),深色保留可切换,localStorage 持久化。

**Architecture:** Tailwind `dark:` 变体反转——亮色为默认类值,深色全部变 `dark:` 前缀,根 `<html class="dark">` 由 `stores/theme.ts` 控制;naive-ui 按 isDark 换 dark/light theme + 两套 overrides;全局 SCSS 改两套 CSS 变量;feedback 弹层与 highlight.js 高亮按主题切换。

**Tech Stack:** Vue 3.5 + Pinia + Tailwind 3.4(`darkMode: 'class'`)+ Naive UI 2;vitest + happy-dom。

**规格:** `docs/superpowers/specs/2026-08-13-light-theme-design.md`

## Global Constraints

- 不新增依赖;不改后端;测试命令:`cd frontend && npx vitest run`、`npx vue-tsc --noEmit`、`npm run build`
- **灰色映射表**(全计划统一,深色值与改造前完全一致):
  - `text-gray-100` → `text-gray-900 dark:text-gray-100`;`text-gray-200` → `text-gray-800 dark:text-gray-200`
  - `text-gray-300` → `text-gray-700 dark:text-gray-300`;`text-gray-400` → `text-gray-600 dark:text-gray-400`
  - `text-gray-500` 两端通用**不改**;`text-gray-600` → `text-gray-400 dark:text-gray-600`
  - `border-white/5` → `border-gray-100 dark:border-white/5`;`border-white/10` → `border-gray-200 dark:border-white/10`
  - `bg-white/[0.02]`、`bg-white/[0.03]` → `bg-gray-50 dark:bg-white/[0.02]`(原值)
  - `bg-white/5`、`hover:bg-white/5` → `bg-gray-100 dark:bg-white/5`、`hover:bg-gray-100 dark:hover:bg-white/5`
  - `bg-[#15151f]` → `bg-white dark:bg-[#15151f]`;`hover:!border-white/10` → `hover:!border-gray-300 dark:hover:!border-white/10`
  - `!border-cyan-400/40` → `!border-cyan-500/50 dark:!border-cyan-400/40`;`text-cyan-400` → `text-cyan-600 dark:text-cyan-400`
  - `text-cyan-300` → `text-cyan-600 dark:text-cyan-300`;`bg-cyan-400/10` → `bg-cyan-50 dark:bg-cyan-400/10`
  - `text-red-300` → `text-red-600 dark:text-red-300`;`bg-red-500/10` → `bg-red-50 dark:bg-red-500/10`;`border-red-400/30` → `border-red-300 dark:border-red-400/30`
- theme store 键名 `nb-theme`,值 `light`/`dark`;`isDark = theme === 'dark'`;副作用统一在 store 的 `apply()`:`document.documentElement.classList.toggle('dark', isDark)`
- commit 英文 subject,格式 `feat:`;逐任务提交

---

### Task 1: theme store(TDD)

**Files:**
- Create: `frontend/src/stores/theme.ts`
- Test: `frontend/src/stores/__tests__/theme.test.ts`

**Interfaces:**
- Produces: `useThemeStore()` → `{ theme: Ref<'light'|'dark'>, isDark: ComputedRef<boolean>, toggleTheme(): void }` — Task 3/4/9 消费;副作用:html.dark class + localStorage

- [ ] **Step 1: 写失败测试**

新建 `frontend/src/stores/__tests__/theme.test.ts`:

```ts
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
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/stores/__tests__/theme.test.ts`
Expected: FAIL — 找不到 `@/stores/theme`

- [ ] **Step 3: 写最小实现**

新建 `frontend/src/stores/theme.ts`:

```ts
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
```

- [ ] **Step 4: 运行确认通过**

Run: `cd frontend && npx vitest run src/stores/__tests__/theme.test.ts`
Expected: 4 个测试全 PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/src/stores/theme.ts frontend/src/stores/__tests__/theme.test.ts
git commit -m "feat: theme store (light default, dark toggle, localStorage + html.dark sync)"
```

---

### Task 2: Tailwind darkMode + 全局 CSS 变量 + naive-ui 两套 overrides

**Files:**
- Modify: `frontend/tailwind.config.js`(darkMode)
- Modify: `frontend/src/styles/global.scss`(变量两套 + 全部改变量引用)
- Delete: `frontend/src/styles/variables.scss`(先 grep 确认无其他引用)
- Modify: `frontend/src/styles/theme.ts`(darkThemeOverrides 改名导出 + lightThemeOverrides)

**Interfaces:**
- Produces: `darkThemeOverrides`、`lightThemeOverrides`(Task 3 消费);CSS 变量 `--nb-bg/--nb-card-bg/--nb-border/--nb-text-primary/--nb-text-secondary/--nb-text-faint/--nb-header-bg/--nb-nav-active-bg/--nb-hover-shadow/--nb-scrollbar/--nb-selection`

- [ ] **Step 1: 确认 variables.scss 引用并修改配置**

Run: `cd frontend && grep -rn "variables" src --include="*.vue" --include="*.ts" --include="*.scss" | grep -v node_modules`
Expected: 仅 `global.scss` 的 `@use './variables' as *;`(若有其他引用,先记录并改为变量)

修改 `frontend/tailwind.config.js`:

```js
/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  darkMode: 'class',
  theme: { extend: {} },
  plugins: [],
}
```

- [ ] **Step 2: 重写 global.scss(两套 CSS 变量 + 变量引用)**

替换 `frontend/src/styles/global.scss` 全文:

```scss
@tailwind base;
@tailwind components;
@tailwind utilities;

/* 亮色(默认)/深色(.dark)两套设计 token(规格 3.4) */
:root {
  --nb-bg: #f5f6f8;
  --nb-card-bg: #ffffff;
  --nb-border: #e5e7eb;
  --nb-text-primary: #111827;
  --nb-text-secondary: #4b5563;
  --nb-text-faint: #9ca3af;
  --nb-header-bg: rgba(255, 255, 255, 0.85);
  --nb-nav-active-bg: rgba(17, 24, 39, 0.06);
  --nb-hover-shadow: 0 4px 16px -8px var(--nb-accent);
  --nb-scrollbar: rgba(17, 24, 39, 0.15);
  --nb-selection: rgba(34, 211, 238, 0.25);
}

.dark {
  --nb-bg: #0a0a0f;
  --nb-card-bg: rgba(255, 255, 255, 0.02);
  --nb-border: rgba(255, 255, 255, 0.05);
  --nb-text-primary: #f3f4f6;
  --nb-text-secondary: #9ca3af;
  --nb-text-faint: #4b5563;
  --nb-header-bg: rgba(10, 10, 15, 0.72);
  --nb-nav-active-bg: rgba(255, 255, 255, 0.05);
  --nb-hover-shadow: 0 8px 32px -8px var(--nb-accent);
  --nb-scrollbar: rgba(255, 255, 255, 0.12);
  --nb-selection: rgba(34, 211, 238, 0.3);
}

/* 玻璃卡片基础样式(全局:GlassCard.vue 组件与裸 class="glass-card" 使用方共用) */
.glass-card {
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
  border-radius: 12px;
  backdrop-filter: blur(12px);
  transition:
    transform 0.3s ease,
    border-color 0.3s ease,
    box-shadow 0.3s ease;
}
/* 玻璃卡片修饰类(全局:裸 class 使用方如 BaguwenView 卡片因此生效) */
.glass-card--padded {
  padding: 16px;
}
.glass-card--hover:hover {
  transform: translateY(-2px);
  border-color: var(--nb-accent);
  box-shadow: var(--nb-hover-shadow);
}
.glass-card--cyan {
  --nb-accent: #22d3ee;
}
.glass-card--violet {
  --nb-accent: #a78bfa;
}
.glass-card--amber {
  --nb-accent: #fbbf24;
}
.glass-card--emerald {
  --nb-accent: #34d399;
}

html,
body,
#app {
  height: 100%;
}

body {
  background: var(--nb-bg);
  color: var(--nb-text-primary);
  font-family: 'Inter', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  transition: background-color 0.3s ease, color 0.3s ease;
}

/* 滚动条 */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}
::-webkit-scrollbar-thumb {
  background: var(--nb-scrollbar);
  border-radius: 4px;
}
::-webkit-scrollbar-track {
  background: transparent;
}

::selection {
  background: var(--nb-selection);
}

/* AI 打字光标(设计文档: cursor blink 1s) */
@keyframes nb-blink {
  0%,
  49% {
    opacity: 1;
  }
  50%,
  100% {
    opacity: 0;
  }
}
.nb-cursor {
  display: inline-block;
  width: 8px;
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: #a78bfa;
  animation: nb-blink 1s infinite;
}

/* 页面切换动画暂移除(见 App.vue 注释),此段保留供「动画打磨」子项目使用 */

/* 尊重系统减弱动效设置(仅减弱动画,不改变任何视觉 token) */
@media (prefers-reduced-motion: reduce) {
  .nb-cursor {
    animation: none;
    opacity: 1;
  }
}
```

删除 `frontend/src/styles/variables.scss`:

```bash
git rm frontend/src/styles/variables.scss
```

- [ ] **Step 3: theme.ts 加两套 overrides**

替换 `frontend/src/styles/theme.ts` 全文:

```ts
import type { GlobalThemeOverrides } from 'naive-ui'

// 深色玻璃拟态主题覆盖(设计文档 5.3)
export const darkThemeOverrides: GlobalThemeOverrides = {
  common: {
    bodyColor: '#0a0a0f',
    cardColor: 'rgba(255, 255, 255, 0.02)',
    modalColor: '#15151f',
    popoverColor: '#15151f',
    borderColor: 'rgba(255, 255, 255, 0.08)',
    dividerColor: 'rgba(255, 255, 255, 0.06)',
    borderRadius: '8px',
    textColorBase: '#f3f4f6',
    textColor1: '#f3f4f6',
    textColor2: '#9ca3af',
    textColor3: '#4b5563',
    primaryColor: '#22d3ee',
    primaryColorHover: '#67e8f9',
    primaryColorPressed: '#0e7490',
    primaryColorSuppl: '#67e8f9',
    infoColor: '#a78bfa',
    infoColorHover: '#c4b5fd',
    infoColorPressed: '#7c3aed',
    infoColorSuppl: '#c4b5fd',
    successColor: '#34d399',
    successColorHover: '#6ee7b7',
    successColorPressed: '#059669',
    successColorSuppl: '#6ee7b7',
    warningColor: '#fbbf24',
    warningColorHover: '#fcd34d',
    warningColorPressed: '#d97706',
    warningColorSuppl: '#fcd34d',
    errorColor: '#f87171',
    errorColorHover: '#fca5a5',
    errorColorPressed: '#dc2626',
    errorColorSuppl: '#fca5a5',
    inputColor: 'rgba(255, 255, 255, 0.03)',
  },
}

// 亮色主题覆盖(规格 3.3:浅灰底 + 白卡片,强调色压暗一档保证白底对比度)
export const lightThemeOverrides: GlobalThemeOverrides = {
  common: {
    bodyColor: '#f5f6f8',
    cardColor: '#ffffff',
    modalColor: '#ffffff',
    popoverColor: '#ffffff',
    borderColor: '#e5e7eb',
    dividerColor: '#f3f4f6',
    borderRadius: '8px',
    textColorBase: '#111827',
    textColor1: '#111827',
    textColor2: '#4b5563',
    textColor3: '#9ca3af',
    primaryColor: '#0891b2',
    primaryColorHover: '#06b6d4',
    primaryColorPressed: '#0e7490',
    primaryColorSuppl: '#06b6d4',
    infoColor: '#7c3aed',
    infoColorHover: '#8b5cf6',
    infoColorPressed: '#6d28d9',
    infoColorSuppl: '#8b5cf6',
    successColor: '#059669',
    successColorHover: '#10b981',
    successColorPressed: '#047857',
    successColorSuppl: '#10b981',
    warningColor: '#d97706',
    warningColorHover: '#f59e0b',
    warningColorPressed: '#b45309',
    warningColorSuppl: '#f59e0b',
    errorColor: '#dc2626',
    errorColorHover: '#ef4444',
    errorColorPressed: '#b91c1c',
    errorColorSuppl: '#ef4444',
    inputColor: '#f9fafb',
  },
}
```

- [ ] **Step 4: App.vue 最小同步修复(改名引用)**

`App.vue` 中 `import { themeOverrides } from '@/styles/theme'` 改为:

```ts
import { darkThemeOverrides } from '@/styles/theme'
```

template 中 `:theme-overrides="themeOverrides"` 改为 `:theme-overrides="darkThemeOverrides"`(其余不动,Task 3 再完整接线)

- [ ] **Step 5: 验证**

Run: `cd frontend && npx vue-tsc --noEmit && npm run build`
Expected: typecheck 无输出,build 成功

- [ ] **Step 6: 提交**

```bash
git add frontend/tailwind.config.js frontend/src/styles/global.scss frontend/src/styles/theme.ts frontend/src/styles/variables.scss frontend/src/App.vue
git commit -m "feat: two-theme css variables and naive-ui overrides (light default + dark)"
```

---

### Task 3: App.vue 主题接线 + feedback 弹层主题化(TDD)

**Files:**
- Modify: `frontend/src/App.vue`(isDark → theme/overrides;watchEffect → applyFeedbackTheme)
- Modify: `frontend/src/utils/feedback.ts`(applyFeedbackTheme + live binding 重建)
- Test: `frontend/src/utils/__tests__/feedback.test.ts`

**Interfaces:**
- Consumes: `useThemeStore().isDark`(Task 1)、`darkThemeOverrides`/`lightThemeOverrides`(Task 2)
- Produces: `applyFeedbackTheme(isDark: boolean): void` — App.vue 调用;`message`/`dialog` 随主题切换

- [ ] **Step 1: 写失败测试**

新建 `frontend/src/utils/__tests__/feedback.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { applyFeedbackTheme, message } from '@/utils/feedback'

describe('feedback theme', () => {
  it('applyFeedbackTheme 切换后 message 实例更换且仍可用', async () => {
    const before = message
    applyFeedbackTheme(true)
    const dark = message
    expect(dark).not.toBe(before)
    applyFeedbackTheme(false)
    expect(message).not.toBe(dark)
    // 切换后调用不抛错(happy-dom 下 naive-ui message 正常渲染)
    message.success('ok')
    expect(document.body.textContent).toContain('ok')
  })
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/utils/__tests__/feedback.test.ts`
Expected: FAIL — `applyFeedbackTheme` 未导出

- [ ] **Step 3: 写最小实现**

替换 `frontend/src/utils/feedback.ts` 全文:

```ts
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
```

`frontend/src/App.vue` 的 `<script setup>` 改为:

```vue
<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import { darkTheme, lightTheme, NConfigProvider } from 'naive-ui'
import { darkThemeOverrides, lightThemeOverrides } from '@/styles/theme'
import { useThemeStore } from '@/stores/theme'
import { applyFeedbackTheme } from '@/utils/feedback'
import AppHeader from '@/components/AppHeader.vue'
import BottomTabBar from '@/components/BottomTabBar.vue'

const route = useRoute()
const isLogin = computed(() => route.name === 'login')
const themeStore = useThemeStore()

watchEffect(() => applyFeedbackTheme(themeStore.isDark))
</script>
```

template 第一行改为:

```html
  <n-config-provider
    :theme="themeStore.isDark ? darkTheme : lightTheme"
    :theme-overrides="themeStore.isDark ? darkThemeOverrides : lightThemeOverrides"
  >
```

(其余 template 不变;`darkTheme` 旧引用位置同步)

- [ ] **Step 4: 运行确认通过**

Run: `cd frontend && npx vitest run src/utils/__tests__/feedback.test.ts && npx vue-tsc --noEmit`
Expected: feedback 测试 PASS,typecheck 无输出

- [ ] **Step 5: 提交**

```bash
git add frontend/src/App.vue frontend/src/utils/feedback.ts frontend/src/utils/__tests__/feedback.test.ts
git commit -m "feat: wire theme into app shell and discrete feedback api"
```

---

### Task 4: AppHeader 变量化 + 主题切换按钮 + BottomTabBar(TDD)

**Files:**
- Modify: `frontend/src/components/AppHeader.vue`(内联 style 变量化 + scoped 色变量 + 切换按钮)
- Modify: `frontend/src/components/BottomTabBar.vue`(内联 style 变量化)
- Test: `frontend/src/components/__tests__/AppHeader.test.ts`

**Interfaces:**
- Consumes: `useThemeStore().isDark/toggleTheme`(Task 1)

- [ ] **Step 1: 写失败测试**

新建 `frontend/src/components/__tests__/AppHeader.test.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AppHeader from '@/components/AppHeader.vue'
import { useThemeStore } from '@/stores/theme'
import { useUserStore } from '@/stores/user'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  RouterLink: { template: '<a><slot /></a>' },
}))

describe('AppHeader 主题切换', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
    setActivePinia(createPinia())
    useUserStore().user = { id: 1, username: 'admin', role: 'ADMIN', createdAt: '' }
  })

  it('点击切换按钮翻转主题并同步 html.dark', async () => {
    const wrapper = mount(AppHeader, {
      global: { stubs: { 'n-dropdown': { template: '<div><slot /></div>' }, Icon: { template: '<i />' } } },
    })
    const themeStore = useThemeStore()
    expect(themeStore.isDark).toBe(false)
    await wrapper.find('button.theme-toggle').trigger('click')
    expect(themeStore.isDark).toBe(true)
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/components/__tests__/AppHeader.test.ts`
Expected: FAIL — 找不到 `button.theme-toggle`

- [ ] **Step 3: 写最小实现**

`AppHeader.vue` script 加(现有 import 后):

```ts
import { Icon } from '@iconify/vue'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
```

template 头部与用户区改为:

```html
  <header class="fixed inset-x-0 top-0 z-40 h-16 border-b backdrop-blur-xl" style="background: var(--nb-header-bg); border-color: var(--nb-border)">
    <div class="mx-auto flex h-full max-w-7xl items-center gap-2 px-4">
      <RouterLink to="/" class="flex shrink-0 items-center gap-2">
        <span class="h-2.5 w-2.5 rounded-full bg-gradient-to-r from-cyan-400 to-purple-400" />
        <span class="text-sm font-semibold tracking-wide text-gray-900 dark:text-gray-100">NeuralBase</span>
      </RouterLink>
      <nav class="ml-6 hidden items-center gap-1 md:flex">
        <RouterLink
          v-for="n in navs"
          :key="n.name"
          :to="n.path"
          class="nav-link rounded-lg px-3 py-1.5 text-sm transition-colors"
        >
          {{ n.label }}
        </RouterLink>
      </nav>
      <div class="ml-auto flex items-center">
        <button
          class="theme-toggle mr-2 flex h-9 w-9 items-center justify-center rounded-lg text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-700 dark:text-gray-400 dark:hover:bg-white/5 dark:hover:text-gray-200"
          title="切换主题"
          @click="themeStore.toggleTheme()"
        >
          <Icon v-if="themeStore.isDark" icon="mdi:white-balance-sunny" class="h-5 w-5" />
          <Icon v-else icon="mdi:weather-night" class="h-5 w-5" />
        </button>
        <n-dropdown :options="userOptions" trigger="click" @select="onUserSelect">
          <button class="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/5">
            <span class="flex h-6 w-6 items-center justify-center rounded-full bg-gradient-to-br from-cyan-400 to-purple-400 text-xs font-semibold text-black">
              {{ initial }}
            </span>
            <span class="hidden sm:inline">{{ userStore.user?.username }}</span>
          </button>
        </n-dropdown>
      </div>
    </div>
  </header>
```

scoped style 改为:

```css
.nav-link {
  color: var(--nb-text-secondary);
}
.nav-link:hover {
  color: var(--nb-text-primary);
}
.nav-link.router-link-active {
  color: var(--nb-text-primary);
  background: var(--nb-nav-active-bg);
}
```

`BottomTabBar.vue` template 的 nav 改为:

```html
  <nav
    class="fixed inset-x-0 bottom-0 z-40 border-t backdrop-blur-xl md:hidden"
    style="background: var(--nb-header-bg); border-color: var(--nb-border)"
  >
```

scoped style 的 `.tab-link { color: #6b7280; }` 改为 `.tab-link { color: var(--nb-text-faint); }`

- [ ] **Step 4: 运行确认通过**

Run: `cd frontend && npx vitest run src/components/__tests__/AppHeader.test.ts && npx vue-tsc --noEmit`
Expected: AppHeader 测试 PASS,typecheck 无输出

- [ ] **Step 5: 提交**

```bash
git add frontend/src/components/AppHeader.vue frontend/src/components/BottomTabBar.vue frontend/src/components/__tests__/AppHeader.test.ts
git commit -m "feat: header theme toggle button and variable-based shell styling"
```

---

### Task 5: 小组件亮暗类改造(FileUploader / ModuleCard / ChatMessage)

**Files:**
- Modify: `frontend/src/components/FileUploader.vue`
- Modify: `frontend/src/components/ModuleCard.vue`
- Modify: `frontend/src/components/ChatMessage.vue`

**Interfaces:** 无新接口;纯样式类替换(映射表见 Global Constraints)

- [ ] **Step 1: 应用替换(无测试——样式类;验证靠 typecheck + 后续 CDP)**

`FileUploader.vue`(2 处):

```html
        <span class="text-sm text-gray-700 dark:text-gray-300">
          {{ uploading ? '上传中…' : '点击或拖拽文件到此处上传' }}
        </span>
        <span class="text-xs text-gray-400 dark:text-gray-600">支持 PDF / Word / Markdown / TXT</span>
```

`ModuleCard.vue`(1 处):

```html
      <div class="mb-3 flex h-10 w-10 items-center justify-center rounded-xl border border-gray-200 bg-gray-50 dark:border-white/10 dark:bg-white/[0.03]">
        <Icon :icon="icon" class="h-5 w-5" :style="{ color: `var(--nb-accent)` }" />
      </div>
      <div class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ title }}</div>
      <div class="mt-1 text-xs leading-relaxed text-gray-500">{{ desc }}</div>
```

`ChatMessage.vue`(4 处):

```html
      <div
        v-if="role === 'USER'"
        class="whitespace-pre-wrap rounded-2xl rounded-br-sm border border-gray-200 bg-gradient-to-br from-cyan-500/10 to-purple-500/10 px-4 py-2.5 text-[15px] leading-relaxed text-gray-900 dark:border-white/10 dark:from-cyan-500/15 dark:to-purple-500/15 dark:text-gray-100"
      >
        {{ content }}
      </div>
```

来源块:

```html
          <div class="text-xs text-gray-500">📚 参考来源</div>
          <div
            v-for="(s, i) in sources"
            :key="i"
            class="rounded-lg border border-gray-100 bg-gray-50 px-3 py-2 dark:border-white/5 dark:bg-white/[0.02]"
          >
            <div class="text-xs text-gray-700 dark:text-gray-300">{{ s.title }}</div>
            <div class="mt-1 line-clamp-2 text-xs text-gray-500">{{ s.content }}</div>
          </div>
```

正文容器:

```html
        <div class="text-[15px] leading-relaxed text-gray-900 dark:text-gray-100">
```

- [ ] **Step 2: 验证**

Run: `cd frontend && npx vitest run src/components/__tests__/ChatMessage.test.ts && npx vue-tsc --noEmit && npm run build`
Expected: 现有 ChatMessage 测试 PASS,typecheck 无输出,build 成功

- [ ] **Step 3: 提交**

```bash
git add frontend/src/components/FileUploader.vue frontend/src/components/ModuleCard.vue frontend/src/components/ChatMessage.vue
git commit -m "feat: light/dark classes for uploader, module card, chat message"
```

---

### Task 6: Dashboard + Login 亮暗改造

**Files:**
- Modify: `frontend/src/views/DashboardView.vue`
- Modify: `frontend/src/views/LoginView.vue`

**Interfaces:** 无新接口

- [ ] **Step 1: 应用替换**

`DashboardView.vue`:

```html
      <h1 class="text-lg font-semibold text-gray-900 dark:text-gray-100">知识中枢</h1>
      <p class="mt-1 text-xs text-gray-500">上传文档构建知识库,与 AI 对话,自动整理八股文</p>
```

最近会话块:

```html
          <span class="text-sm text-gray-700 dark:text-gray-300">最近会话</span>
          <RouterLink to="/chat" class="text-xs text-cyan-600 hover:underline dark:text-cyan-400">全部 →</RouterLink>
        </div>
        <div class="space-y-1.5">
          <div v-for="c in conversations" :key="c.id" class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-white/[0.02]">
            <div class="truncate text-sm text-gray-800 dark:text-gray-200">{{ c.title }}</div>
            <div v-if="c.lastMessageContent" class="mt-0.5 truncate text-xs text-gray-500">
              {{ c.lastMessageContent }}
            </div>
          </div>
          <div v-if="!conversations.length" class="py-6 text-center text-xs text-gray-400 dark:text-gray-600">
            暂无会话
          </div>
```

最近文档块:

```html
          <span class="text-sm text-gray-700 dark:text-gray-300">最近文档</span>
          <RouterLink to="/kb" class="text-xs text-cyan-600 hover:underline dark:text-cyan-400">全部 →</RouterLink>
        </div>
        <div class="space-y-1.5">
          <div v-for="d in documents" :key="d.id" class="flex items-center justify-between gap-2 rounded-lg bg-gray-50 px-3 py-2 dark:bg-white/[0.02]">
            <span class="truncate text-sm text-gray-800 dark:text-gray-200">{{ d.title }}</span>
            <span class="shrink-0 text-xs text-gray-400 dark:text-gray-600">{{ d.status }}</span>
          </div>
          <div v-if="!documents.length" class="py-6 text-center text-xs text-gray-400 dark:text-gray-600">
            暂无文档
          </div>
```

`LoginView.vue`:

```html
        <h1 class="text-xl font-semibold tracking-wide text-gray-900 dark:text-gray-100">NeuralBase</h1>
        <p class="text-xs text-gray-500">个人 AI 知识中枢</p>
```

- [ ] **Step 2: 验证**

Run: `cd frontend && npx vue-tsc --noEmit && npm run build`
Expected: 无输出 + build 成功

- [ ] **Step 3: 提交**

```bash
git add frontend/src/views/DashboardView.vue frontend/src/views/LoginView.vue
git commit -m "feat: light/dark classes for dashboard and login views"
```

---

### Task 7: KnowledgeBase + DocumentReader 亮暗改造

**Files:**
- Modify: `frontend/src/views/KnowledgeBaseView.vue`
- Modify: `frontend/src/views/DocumentReaderView.vue`

**Interfaces:** 无新接口

- [ ] **Step 1: 应用替换**

`KnowledgeBaseView.vue`(全部 `text-gray-*`、`!border-cyan-400/40` 按映射表):

- `class="text-sm text-gray-400"`(知识库标题)→ `text-sm text-gray-600 dark:text-gray-400`
- 全部文档按钮 `!border-cyan-400/40` → `!border-cyan-500/50 dark:!border-cyan-400/40`(2 处)
- `<span class="text-sm text-gray-200">全部文档</span>` → `text-gray-800 dark:text-gray-200`
- KB 名 `text-sm text-gray-200` → `text-gray-800 dark:text-gray-200`
- 删除图标 `text-gray-600 hover:text-red-400` → `text-gray-400 hover:text-red-400 dark:text-gray-600`
- 空态 `text-xs text-gray-600`(3 处)→ `text-xs text-gray-400 dark:text-gray-600`
- 搜索框区 `text-xs text-gray-500`(2 处)→ 保持
- 搜索结果 `text-sm text-gray-200` → `text-gray-800 dark:text-gray-200`;`text-xs text-gray-600` → `text-xs text-gray-400 dark:text-gray-600`;`text-xs leading-relaxed text-gray-400` → `text-gray-600 dark:text-gray-400`
- 文档标题 `text-gray-200`(含 hover:text-cyan-300)→ `text-gray-800 dark:text-gray-200` + `hover:text-cyan-600 dark:hover:text-cyan-300`
- 错误行 `text-red-400` → `text-red-500 dark:text-red-400`
- 空态 `text-sm text-gray-600`(1 处)→ `text-sm text-gray-400 dark:text-gray-600`

`DocumentReaderView.vue`:

- 树标题区 KB select 无类改动
- `currentDoc?.id === d.id ? '!border-cyan-400/40'` → `'!border-cyan-500/50 dark:!border-cyan-400/40'`
- 树文档 `text-sm text-gray-200` → `text-gray-800 dark:text-gray-200`
- 树空态 `text-xs text-gray-600` → `text-gray-400 dark:text-gray-600`
- header 返回链接 `text-xs text-gray-500 hover:text-cyan-300` → `hover:text-cyan-600 dark:hover:text-cyan-300`
- 标题 `text-base text-gray-100` → `text-gray-900 dark:text-gray-100`
- 下载/原文链接 `text-xs text-gray-500 hover:text-cyan-300` → `hover:text-cyan-600 dark:hover:text-cyan-300`(2 处)
- 目录按钮 `border-white/10 bg-[#15151f]/90 text-gray-300 hover:text-cyan-300` → `border-gray-200 bg-white/90 text-gray-600 hover:text-cyan-600 dark:border-white/10 dark:bg-[#15151f]/90 dark:text-gray-300 dark:hover:text-cyan-300`
- 加载/错误/空态 `text-gray-500`/`text-gray-600`/`text-red-400` → `text-red-500 dark:text-red-400`(错误行);`text-gray-600` 提示行 → `text-gray-400 dark:text-gray-600`;`text-gray-500`(加载中)→ 保持
- iframe `border-white/5` → `border-gray-200 dark:border-white/5`
- pre 正文 `text-gray-300` → `text-gray-700 dark:text-gray-300`
- 抽屉高亮 `bg-cyan-400/10 text-cyan-300` → `bg-cyan-50 text-cyan-600 dark:bg-cyan-400/10 dark:text-cyan-300`;普通项 `text-gray-400 hover:text-gray-200` → `text-gray-600 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200`
- 抽屉空态 `text-xs text-gray-600` → `text-gray-400 dark:text-gray-600`
- header 底边 `border-white/5` → `border-gray-100 dark:border-white/5`

- [ ] **Step 2: 验证**

Run: `cd frontend && npx vitest run src/views/__tests__/DocumentReaderView.test.ts && npx vue-tsc --noEmit && npm run build`
Expected: reader 9 测试 PASS,typecheck 无输出,build 成功

- [ ] **Step 3: 提交**

```bash
git add frontend/src/views/KnowledgeBaseView.vue frontend/src/views/DocumentReaderView.vue
git commit -m "feat: light/dark classes for kb and reader views"
```

---

### Task 8: Chat / Baguwen ×2 / DailyNews 亮暗改造

**Files:**
- Modify: `frontend/src/views/ChatView.vue`
- Modify: `frontend/src/views/BaguwenView.vue`
- Modify: `frontend/src/views/BaguwenDetailView.vue`
- Modify: `frontend/src/views/DailyNewsView.vue`

**Interfaces:** 无新接口

- [ ] **Step 1: 应用替换**

`ChatView.vue`:

- 侧栏标题 `text-sm text-gray-400` → `text-gray-600 dark:text-gray-400`
- 会话卡 `!border-cyan-400/40` → `!border-cyan-500/50 dark:!border-cyan-400/40`;`hover:!border-white/10` → `hover:!border-gray-300 dark:hover:!border-white/10`
- 会话标题 `text-sm text-gray-200` → `text-gray-800 dark:text-gray-200`(2 处)
- 删除图标 `text-gray-600 hover:text-red-400` → `text-gray-400 hover:text-red-400 dark:text-gray-600`
- 摘要 `text-xs text-gray-500` 保持;空态 `text-xs text-gray-600`(2 处)→ `text-gray-400 dark:text-gray-600`
- 欢迎语 `text-sm text-gray-500` 保持
- 错误条 `border-red-400/30 bg-red-500/10 text-red-300` → `border-red-300 bg-red-50 text-red-600 dark:border-red-400/30 dark:bg-red-500/10 dark:text-red-300`

`BaguwenView.vue`:

- 导入说明 `text-sm text-gray-300` → `text-gray-700 dark:text-gray-300`
- 任务区 `text-xs text-gray-500` 保持;任务 URL `text-xs text-gray-400` → `text-gray-600 dark:text-gray-400`
- 文章标题 `text-sm font-medium text-gray-200` → `text-gray-800 dark:text-gray-200`
- 收藏/删除图标 `text-gray-500 hover:bg-white/5 hover:text-cyan-400`(2 处)→ `text-gray-500 hover:bg-gray-100 hover:text-cyan-600 dark:hover:bg-white/5 dark:hover:text-cyan-400`
- 日期/空态 `text-xs text-gray-600`(2 处)→ `text-xs text-gray-400 dark:text-gray-600`;空态 `text-sm text-gray-600` → `text-sm text-gray-400 dark:text-gray-600`

`BaguwenDetailView.vue`:

- 标题 `text-gray-100` → `text-gray-900 dark:text-gray-100`
- Q 序号/问题 `text-gray-500` 保持;`text-gray-100`(问题)→ `text-gray-900 dark:text-gray-100`
- 答案 `text-gray-400` → `text-gray-600 dark:text-gray-400`
- 加载/空态/不存在 `text-sm text-gray-600`(3 处)→ `text-sm text-gray-400 dark:text-gray-600`
- 原文链接 `text-cyan-400` → `text-cyan-600 dark:text-cyan-400`

`DailyNewsView.vue`:

- 卡片 `border-emerald-400/20 bg-emerald-500/10` → `border-emerald-500/20 bg-emerald-50 dark:border-emerald-400/20 dark:bg-emerald-500/10`
- 标题 `text-gray-100` → `text-gray-900 dark:text-gray-100`;描述 `text-gray-500` 保持;占位说明 `text-gray-600` → `text-gray-400 dark:text-gray-600`

- [ ] **Step 2: 验证**

Run: `cd frontend && npx vue-tsc --noEmit && npm run build`
Expected: 无输出 + build 成功

- [ ] **Step 3: 提交**

```bash
git add frontend/src/views/ChatView.vue frontend/src/views/BaguwenView.vue frontend/src/views/BaguwenDetailView.vue frontend/src/views/DailyNewsView.vue
git commit -m "feat: light/dark classes for chat, baguwen and news views"
```

---

### Task 9: MarkdownRenderer 高亮随主题切换

**Files:**
- Modify: `frontend/src/components/MarkdownRenderer.vue`(动态高亮样式)
- Test: `frontend/src/components/__tests__/MarkdownRenderer.test.ts`(现有 4 用例保持绿)

**Interfaces:**
- Consumes: `useThemeStore().isDark`(Task 1)

- [ ] **Step 1: 先给现有测试加 pinia 初始化(组件将依赖 theme store)**

`MarkdownRenderer.test.ts` 顶部 import 区加:

```ts
import { beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
```

`describe` 内加:

```ts
  beforeEach(() => {
    setActivePinia(createPinia())
  })
```

- [ ] **Step 2: 改实现**

`MarkdownRenderer.vue` 修改:

1. 删掉静态 import:`import 'highlight.js/styles/github-dark.css'`
2. script 加:

```ts
import { watch } from 'vue'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
let appliedHighlightTheme: 'light' | 'dark' | null = null

// 两套 hljs 样式都注入时会互相覆盖:按需 import,后注入者生效(vite 运行时注入 style)
async function applyHighlightTheme() {
  const target = themeStore.isDark ? 'dark' : 'light'
  if (appliedHighlightTheme === target) return
  appliedHighlightTheme = target
  if (target === 'dark') await import('highlight.js/styles/github-dark.css')
  else await import('highlight.js/styles/github.css')
}

void applyHighlightTheme()
watch(() => themeStore.isDark, () => void applyHighlightTheme())
```

注意:`watch` 已存在于现有 import(`import { nextTick, onMounted, ref, watch } from 'vue'`),不重复引入。

- [ ] **Step 3: 验证**

Run: `cd frontend && npx vitest run src/components/__tests__/MarkdownRenderer.test.ts && npx vue-tsc --noEmit && npm run build`
Expected: 4 测试 PASS,typecheck 无输出,build 成功(css 分包注入正常)

- [ ] **Step 4: 提交**

```bash
git add frontend/src/components/MarkdownRenderer.vue
git commit -m "feat: theme-aware highlight.js styles in markdown renderer"
```

---

### Task 10: 全量回归 + CDP 真机验收

**Files:** 无(验收失败才改)

- [ ] **Step 1: 前端全量**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run build`
Expected: 全绿(50 存量 + 4 theme + 1 feedback + 1 AppHeader = 56),typecheck 无输出,build 成功

- [ ] **Step 2: CDP 真机验收**(规格第六节;本机 CDP 环境已就绪:proxy 端口 3456,Chrome 9222)

1. 清 localStorage 后打开 `http://localhost:5173/kb` → 断言 `getComputedStyle(document.body).backgroundColor === 'rgb(245, 246, 248)'`,html 无 dark 类
2. 点 `.theme-toggle` → 断言 html.dark 存在、body 背景 `rgb(10, 10, 15)`
3. 刷新 → 断言仍为 dark(localStorage 恢复)
4. 再点切换回亮色 → 逐页巡检:登录、Dashboard、知识库、阅读器(树/目录按钮/抽屉)、Chat(气泡 + 代码块)、八股文、日报占位,断言关键元素对比度正常(如阅读器目录按钮亮色为白底)
5. 切回深色 → 抽查 2-3 页与改造前视觉一致(同 token)

- [ ] **Step 3: 验收问题修复(如有)**

修复后重跑 Step 1/2,然后:

```bash
git add -A
git commit -m "fix: light theme acceptance findings"
```

---

## Self-Review 记录

- **规格覆盖**:§3.1 → Task 1;§3.2/3.4 → Task 2 + 4~8;§3.3 → Task 2/3;§3.5 → Task 3/9;§四 15 组件 → Task 4~9;§六 验收 → Task 10
- **占位符**:无 TBD;每步含精确替换或完整代码
- **类型一致**:`useThemeStore` 接口 `{theme,isDark,toggleTheme}` 全计划一致;`applyFeedbackTheme(isDark: boolean)` Task 3 定义、App.vue 消费;`darkThemeOverrides`/`lightThemeOverrides` Task 2 定义、Task 3 消费
