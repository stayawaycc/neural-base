# 动画打磨设计规格

> 日期:2026-08-13　状态:已敲定(经 brainstorming 澄清)
> 所属:前端收尾 · 子项目 3/4(动画打磨 → 延后项补做)
> 基准:系统设计文档「交互动效规范」表(卡片悬浮/页面切换/骨架屏/AI 打字/上传涟漪/按钮按压)

---

## 一、目标

补齐设计文档动效规范中未实现的 3 项(页面切换、骨架屏、上传涟漪)与按钮按压反馈;已实现项(卡片悬浮、AI 打字光标)不动。全部纯 CSS/现有组件,不引入 GSAP(已裁决裁剪)。

## 二、已敲定决策

| # | 决策点 | 结论 |
|---|--------|------|
| 1 | 页面切换动画 | 淡入 + 上滑 300ms(设计文档规格),带 `key` 的 transition 重做 |
| 2 | 骨架屏范围 | 三个关键列表页:Dashboard 最近会话/最近文档、知识库文档列表、八股文文章卡片 |
| 3 | 微交互 | 按钮按压 scale(0.97)+ 上传拖拽区涟漪,都做 |
| 4 | 动效可访问性 | 全部尊重 `prefers-reduced-motion: reduce`(禁用/弱化) |

**不做(YAGNI)**:GSAP、路由级转场花活、滚动驱动动画、全站加载态骨架屏。

## 三、设计

### 3.1 页面切换动画

- `App.vue`:`<router-view v-slot="{ Component }"><transition name="page" mode="out-in"><component :is="Component" :key="route.fullPath" /></transition></router-view>`
- `key=route.fullPath` 强制区分组件实例(修复上次无 key 时 transition 与懒加载组件的复用隐患)
- CSS 已存在(global.scss `.page-enter-*`/`.page-leave-*`,300ms 淡入上滑)
- **transitionend 悬案先实验后定稿**:上次 SPA 空白页的根因(Vue Transition 自带 setTimeout 兜底却仍卡住)未完全查明。实现顺序:先在 CDP 浏览器做最小实验验证 leave 过渡能完成;若 `out-in` 仍卡,退化为带 key 的无 `out-in` 方案(进入动画保留,旧页立即卸载)
- 验收断言:SPA 跳转后新页正常渲染、旧页 DOM 完全卸载(上次 bug 的回归点)

### 3.2 骨架屏(NSkeleton,不造轮子)

- **Dashboard**(`DashboardView.vue`):两个玻璃卡片内,`loading` 为真时渲染 3 行 `n-skeleton`(text 行,模拟会话/文档条目),数据到达后替换为真实列表
- **知识库**(`KnowledgeBaseView.vue`):文档列表区,`loading` 为真时渲染标题行 + 徽标形状骨架,数据到达后替换
- **八股文**(`BaguwenView.vue`):文章网格区,`loading` 为真时渲染 3 个骨架卡片(标题两行 + 标签行),数据到达后替换
- 三个页面各加 `loading` ref(挂载时 true,首次数据加载完成置 false;后续轮询/筛选不触发骨架,仅首次)
- 内容替换后渐显:全局 `.fade-in`(opacity 0→1,200ms ease)加到列表容器

### 3.3 微交互(global.scss 全局类)

```scss
/* 按钮按压反馈(自绘按钮专用,naive-ui 按钮自带动效不叠加) */
.btn-press {
  transition: transform 0.1s ease;
}
.btn-press:active {
  transform: scale(0.97);
}

/* 上传拖拽区涟漪:悬停时轻微放大 + 边框呼吸 */
.upload-ripple {
  transition: transform 0.2s ease, border-color 0.2s ease;
}
.upload-ripple:hover {
  transform: scale(1.01);
  animation: nb-border-pulse 1.2s ease-in-out infinite;
}
@keyframes nb-border-pulse {
  0%, 100% { border-color: var(--nb-accent-cyan); }
  50% { border-color: var(--nb-border); }
}

/* 内容渐显(骨架屏替换后) */
.fade-in {
  animation: nb-fade-in 0.2s ease;
}
@keyframes nb-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}
```

- `.btn-press` 应用到约 10 处自绘按钮:阅读器树条目/目录按钮、KB 页 KB 按钮与文档行、Chat 会话按钮、AppHeader 主题切换、ModuleCard 卡片、Baguwen 文章卡片、抽屉大纲项
- `.upload-ripple` 应用到 FileUploader 的 `n-upload-dragger`
- `prefers-reduced-motion: reduce` 下:`.btn-press:active`、`.upload-ripple:hover`、`.fade-in` 动画禁用

## 四、文件清单

**修改**:`frontend/src/App.vue`(transition 接线)、`frontend/src/styles/global.scss`(新类与 reduced-motion)、`frontend/src/views/DashboardView.vue`、`frontend/src/views/KnowledgeBaseView.vue`、`frontend/src/views/BaguwenView.vue`(骨架屏 + loading)、`frontend/src/components/FileUploader.vue`(涟漪类)、`frontend/src/components/AppHeader.vue`/`ModuleCard.vue`、`frontend/src/views/DocumentReaderView.vue`/`ChatView.vue`/`BaguwenDetailView.vue`(btn-press 类)

## 五、验收标准

1. **自动化**:前端现有 58 个测试不回归;typecheck 无输出;build 成功
2. **CDP 真机验收**:① SPA 页面切换:新页渲染正常 + 旧页 DOM 完全卸载(回归断言)② 骨架屏:首次加载出现、数据到达消失 ③ 亮暗两态动画类正常 ④ 按压/涟漪由用户在浏览器体感确认 ⑤ `prefers-reduced-motion` 模拟下动画禁用
3. **明确不做**:GSAP、转场花活、滚动驱动
