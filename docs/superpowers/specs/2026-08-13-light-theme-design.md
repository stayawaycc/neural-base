# 亮色模式设计规格

> 日期:2026-08-13　状态:已敲定(经 brainstorming 澄清)
> 所属:前端收尾 · 子项目 2/4(亮色模式 → 动画打磨 → 延后项补做)
> 动机:用户反馈「页面太暗」,要求全站白色系,同时保留深色

---

## 一、目标

新增亮色主题:**默认亮色**,深色保留为可切换选项。全站(含登录页)跟随,选择持久化,刷新不丢。

## 二、已敲定决策

| # | 决策点 | 结论 |
|---|--------|------|
| 1 | 默认主题 | **亮色**(首次无本地记忆即亮色;深色为可切换选项) |
| 2 | 切换入口 | 顶栏右侧(用户头像旁)太阳/月亮图标按钮 |
| 3 | 亮色基调 | **柔和浅灰**:背景 `#f5f6f8`,卡片白色,深色文字 |
| 4 | 登录页 | 同样跟随主题(默认亮色) |
| 5 | 持久化 | localStorage 键 `nb-theme`,值 `light`/`dark` |
| 6 | 实现方案 | **Tailwind `dark:` 变体反转**(方案 A):亮色为默认类值,深色全部变 `dark:` 前缀;根 `<html class="dark">` 控制 |

**不做(YAGNI)**:跟随系统外观、更多主题色板、定时切换、按页面独立主题。

## 三、基础设施设计

### 3.1 主题状态(`stores/theme.ts`,新)

- Pinia store:`theme = ref<'light' | 'dark'>('light')`
- `toggleTheme()`:翻转并持久化到 localStorage
- 初始化:读 `nb-theme`,无则 `'light'`
- 副作用(store 内统一):`document.documentElement.classList.toggle('dark', isDark)`
- 导出 `isDark` computed 供 App.vue / feedback / MarkdownRenderer 使用

### 3.2 Tailwind

- `tailwind.config.js` 加 `darkMode: 'class'`
- 约定:文字 `text-gray-100`(深色值)→ 亮色默认 `text-gray-900` + `dark:text-gray-100`;`text-gray-300` → `text-gray-700 dark:text-gray-300`;`text-gray-400` → `text-gray-600 dark:text-gray-400`;`text-gray-500` → `text-gray-500 dark:text-gray-500`(两端都适用的保持原样);`text-gray-600`(弱化文字)→ 亮色 `text-gray-400` + `dark:text-gray-600`
- 边框 `border-white/5`、`border-white/10` → 亮色 `border-gray-200`/`border-gray-300` + `dark:border-white/5` 等对应
- 悬浮底 `bg-white/5` → `bg-gray-100 dark:bg-white/5`
- 深色实底 `bg-[#15151f]` → `bg-white dark:bg-[#15151f]`
- 内联深色 style(BottomTabBar 等 rgba 背景)→ 改为 CSS 变量或亮/暗双类

### 3.3 naive-ui 主题

- `theme.ts` 增加 `lightThemeOverrides`:bodyColor `#f5f6f8`、cardColor `#ffffff`、modal/popover `#ffffff`、borderColor `#e5e7eb`、textColor1/2/3 用深灰系(`#111827`/`#4b5563`/`#9ca3af`),四个模块强调色保持现有 cyan/violet/amber/emerald 体系(压暗一档保证白底对比度)
- `App.vue`:`<n-config-provider :theme="isDark ? darkTheme : lightTheme" :theme-overrides="isDark ? darkThemeOverrides : lightThemeOverrides">`

### 3.4 全局样式

- `variables.scss` → 输出两套 CSS 变量:`:root`(亮色)与 `.dark`(深色),含 `--nb-bg`、`--nb-card-bg`、`--nb-border`、`--nb-text-primary/secondary/faint`、`--nb-accent-*`、滚动条色、选区色
- `global.scss`:body 背景/文字、`.glass-card`(卡片底与边框)、滚动条、`::selection`、`.nb-cursor` 全部改用上述变量;移除对固定深色 SCSS 值的引用
- 玻璃拟态在亮色下:卡片纯白 + 浅灰描边 + 微弱投影(blur 保留)

### 3.5 提示层与代码高亮

- `utils/feedback.ts`:discrete api 的 `theme` 按当前主题传 `darkTheme`/`lightTheme`(读取 theme store;需避免循环依赖——store 不依赖 feedback,feedback 读 store 状态字段即可)
- `MarkdownRenderer.vue`:按 `isDark` 动态 `import()` 高亮样式(`github-dark.css` / `github.css`),vite 自动分包,切换主题时重新渲染生效

## 四、组件改造清单(15 个 .vue)

其中 12 个含硬编码深色 Tailwind 类,另外 GlassCard(内联样式核查)、BottomTabBar(内联 rgba 深色 style)、MarkdownRenderer(高亮样式)按各自问题处理:

`AppHeader`(含新切换按钮)、`BottomTabBar`、`GlassCard`、`ModuleCard`、`ChatMessage`、`MarkdownRenderer`(仅高亮)、`FileUploader`、`LoginView`、`DashboardView`、`KnowledgeBaseView`、`ChatView`、`BaguwenView`、`BaguwenDetailView`、`DailyNewsView`、`DocumentReaderView`(含大纲抽屉、目录按钮的深色底)——全部按 3.2 约定改「亮色默认 + dark: 变体」。

## 五、数据流

```
打开应用 → theme store 初始化(localStorage 或 light)
  → html.dark class + naive-ui 主题 + feedback 主题 + 高亮样式 全联动
点击顶栏按钮 → toggleTheme() → 同上联动 + 写 localStorage
刷新 → 从 localStorage 恢复,深色不丢
```

## 六、测试与验收

1. **自动化**:`stores/__tests__/theme.test.ts`(默认 light、切换后 localStorage 与 html.dark 同步、初始化恢复);AppHeader 切换按钮冒烟;现有 50 个前端测试不回归;typecheck + build 绿
2. **浏览器 CDP 真机验收**(本机有 CDP 环境):
   - 首访(清 localStorage)→ 亮色:body 背景 `#f5f6f8`、卡片白色
   - 点切换 → `html.dark` 生效,computed 样式断言(背景回到 `#0a0a0f`)
   - 刷新 → 深色保持;再切回亮色 → 刷新保持
   - 逐页巡检:登录、Dashboard、知识库、阅读器(树/目录抽屉)、Chat(消息气泡 + 代码高亮)、八股文、日报占位,亮暗两态下文字与边框对比度正常
3. 深色回归:深色态视觉与改造前一致(同 token 值)

## 七、已知注意

- 阅读器目录按钮/抽屉的深色实底(`bg-[#15151f]`)亮色下改白底 + 描边
- PDF iframe 内 PDF 查看器颜色跟随浏览器,与主题无关(接受)
- `glass-card--hover` 的 accent 发光在亮色下改为浅色投影(变量化)
