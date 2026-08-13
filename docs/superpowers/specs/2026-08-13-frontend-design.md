# NeuralBase 前端设计 — Vue 3 全模块

> 日期：2026-08-13　范围：前端全部页面（登录 / Dashboard / 知识库 / AI 对话 / 八股文库 / 日报占位）　基准：`docs/NeuralBase-系统设计文档.md` 前端章节 + `docs/PROGRESS.md`　后端 API：Phase 1–4 已就绪，本机 8081 端口

## 一、目标与范围

对接 Phase 1–4 已完成的后端 API，落地设计文档的 5 个页面。UI 为深色玻璃拟态风格。

**本轮范围（已确认）**：
- ✅ 登录 + Dashboard + 知识库 + AI 对话（SSE 流式）+ 八股文库，全功能对接现有 API
- ✅ AI 日报页做静态占位（后端 Phase 5 未开始，无 API）
- ✅ 后端配套改动：`DELETE /api/document`（向量+文件+元数据全删）、cookie 认证改造（login 设 HttpOnly cookie + 新增 logout 端点）
- ✅ 移动端（<768px）本轮完整做：底部 Tab 导航 + 聊天会话抽屉
- ✅ 实施分两个迭代：迭代 1 = 脚手架 + 设计系统 + 布局 + 登录（含 cookie 认证改造）+ AI 对话；迭代 2 = 知识库（含 DELETE document）+ 八股文库 + Dashboard + 日报占位

**范围外（已裁决）**：会话重命名/收藏、消息点赞点踩、模型切换、日报真功能（Phase 5/6 后端就绪后再做）；动画打磨（Phase 6）。

## 二、已裁决的技术决策

| 决策 | 结论 | 理由 |
|------|------|------|
| 目录 | 新建 `frontend/`（与 `backend/` 平级） | 与仓库实际布局一致（设计文档的 `neural-base-frontend/` 对应 `backend/` 的旧布局） |
| UI 组件策略 | Naive UI 2 + Tailwind 3.4 | Naive UI 提供表单/上传/分页/弹窗，themeOverrides 覆盖为玻璃拟态；Tailwind 管布局。用 3.4 而非 4：与 Naive UI 的 preflight 兼容性最稳 |
| 裁剪依赖 | 不装 TipTap / ECharts / GSAP | 无后端文档编辑与统计 API；动画用 CSS（Phase 6 再考虑） |
| SSE 传输 | fetch + ReadableStream 手写解析（非 EventSource） | 接口是 POST 且需 Bearer header，EventSource 无法用 |
| 暗色模式 | 仅深色（不做亮色切换） | 设计文档以深色玻璃拟态为唯一风格；亮色切换延后 Phase 6 |
| 包管理器 | npm | 本机 Node 22.21.1 + npm 10.9.4，无 pnpm |
| 后端地址 | Vite dev proxy `/api` → `http://localhost:8081` | 本机 8080 被占用，后端按 PROGRESS.md 惯例跑 8081 |
| 认证方案 | HttpOnly cookie 双通道 | login 同时 `Set-Cookie`(HttpOnly+SameSite=Lax+Path=/)并保留 body token（curl/冒烟兼容）；filter 改为 header 优先、cookie 兜底；新增 `POST /api/auth/logout` 清 cookie；前端不存 token（cookie 同源自动携带），`GET /api/auth/me` 校验登录态，401 全局拦截跳登录 |
| Markdown 消毒 | DOMPurify | AI 输出渲染前消毒（markdown-it `html:false` 输出后再过 DOMPurify） |
| 响应式 | 本轮完整做移动端 | `<768px` 底部 Tab 导航（五模块）+ 聊天会话列表收进抽屉；内容单列堆叠 |
| KB 颜色 | 8 色预设色板 | 四模块强调色 + 蓝/粉/橙/灰四补充色，存 hex 到 `Kb.color` |
| 文档删除 | 本轮补后端 `DELETE /api/document/{id}` | 向量行（按 metadata docId）+ 磁盘文件 + 元数据全删；删除模式复用 `process()` 中已有的按 docId 清向量 SQL |
| 组件测试 | 增加组件级测试 | MarkdownRenderer、ChatMessage 用 @vue/test-utils + happy-dom 测试 |

## 三、技术栈

| 类别 | 选型 | 说明 |
|------|------|------|
| 框架/构建 | Vue 3.5 + Vite 7 + TypeScript 5.9 | |
| 状态/路由 | Pinia 3 + Vue Router 4 | |
| UI | Naive UI 2 + Tailwind CSS 3.4 + SCSS | |
| 渲染 | markdown-it + highlight.js + DOMPurify | 聊天 Markdown、代码高亮、消毒 |
| 图标 | @iconify/vue | |
| HTTP | axios（普通接口，cookie 自动携带）+ fetch（SSE） | |
| 测试 | Vitest + @vue/test-utils + happy-dom | SSE 解析器、stores、组件 |

## 四、目录结构

```
frontend/
├── index.html
├── package.json
├── vite.config.ts          # dev proxy: /api → http://localhost:8081
├── tsconfig.json
├── tailwind.config.js
├── vitest.config.ts
└── src/
    ├── main.ts             # 挂载 Naive UI(nMessage/nDialog/nNotification 全局化)、pinia、router
    ├── App.vue             # n-config-provider(darkTheme + themeOverrides) + 全局路由视图
    ├── api/
    │   ├── request.ts      # axios 实例 + Result 壳解包 + 401 拦截
    │   ├── auth.ts         # login / me
    │   ├── kb.ts           # list/get/create/update/delete
    │   ├── document.ts     # upload / search / list
    │   ├── chat.ts         # stream（fetch SSE 封装）/ conversation CRUD / messages
    │   └── baguwen.ts      # import / tasks / retry / list / detail / tags / delete / favorite
    ├── stores/
    │   ├── user.ts         # 登录态（me 校验，token 由 cookie 携带，不存前端）/ login / me / logout
    │   ├── chat.ts         # 会话列表 / 当前消息 / 流式追加 / send(kbId) / stop
    │   ├── document.ts     # KB 列表 / 文档列表 / 上传 / 搜索 / 状态轮询
    │   └── baguwen.ts      # 文章分页列表 / 标签 / 导入任务轮询 / 收藏
    ├── components/
    │   ├── AppHeader.vue   # 顶部导航 h-16，backdrop-blur，模块切换 + 用户菜单
    │   ├── BottomTabBar.vue # 移动端(<768px)底部 Tab 导航，五模块
    │   ├── GlassCard.vue   # 玻璃卡片基底（全站统一）
    │   ├── ModuleCard.vue  # Dashboard 模块入口卡片（四色强调）
    │   ├── MarkdownRenderer.vue  # markdown-it + highlight.js + DOMPurify + 代码块复制按钮
    │   ├── ChatMessage.vue # 单条消息（用户/助手/引用来源/打字光标）
    │   └── FileUploader.vue # 拖拽上传 + 进度 + 状态提示
    ├── views/
    │   ├── LoginView.vue
    │   ├── DashboardView.vue
    │   ├── KnowledgeBaseView.vue
    │   ├── ChatView.vue
    │   ├── BaguwenView.vue
    │   ├── BaguwenDetailView.vue  # 问答对详情页（/baguwen/:id）
    │   └── DailyNewsView.vue   # 静态占位
    ├── router/index.ts      # 路由表 + 未登录守卫（cookie 登录态）
    ├── types/index.ts       # 后端 DTO 的 TS 镜像（见第六节）
    ├── utils/sse.ts         # SSE 事件流解析器（纯函数，独立可测）
    ├── utils/feedback.ts    # createDiscreteApi 全局 message/dialog（全局化提示的实现）
    └── styles/
        ├── variables.scss   # 色值/间距/圆角 token
        └── global.scss      # 玻璃拟态全局样式、滚动条、打字光标
```

## 五、设计系统（深色玻璃拟态）

### 5.1 配色（严格按设计文档）

| 用途 | 色值 |
|------|------|
| 背景主色 | `#0a0a0f` |
| 卡片底色 / 悬浮 | `rgba(255,255,255,0.02)` / `rgba(255,255,255,0.05)` |
| 边框 | `rgba(255,255,255,0.05)` |
| 文字 主/次/弱化 | `#f3f4f6` / `#9ca3af` / `#4b5563` |
| 模块强调色 | 知识库 `#22d3ee` · 对话 `#a78bfa` · 八股文 `#fbbf24` · 日报 `#34d399` |

### 5.2 玻璃卡片（GlassCard）

- 基底：`background: rgba(255,255,255,0.02)` + `border: 1px solid rgba(255,255,255,0.05)` + `border-radius: 12px` + `backdrop-filter: blur(12px)`
- 悬浮：`translateY(-2px)` + 边框变模块强调色 30% 透明度 + 外发光扩散，300ms ease
- 支持 `accent` prop（cyan/violet/amber/emerald），控制悬浮边框色与顶部渐变线

### 5.3 Naive UI 主题覆盖

- `n-config-provider` 挂 `darkTheme` + `themeOverrides`：`common.primaryColor` 按模块场景使用、`bodyColor: #0a0a0f`、`cardColor: rgba(255,255,255,0.02)`、圆角 8px、边框色统一 `rgba(255,255,255,0.05)`
- 全局化 `nMessage`/`nDialog`/`nNotification`，深色玻璃样式

### 5.4 布局与动效

- 固定顶部导航 h-16（backdrop-blur-xl）+ 左侧栏 w-64 可折叠 + 主内容自适应
- 响应式：`≥1280px` 侧栏常驻；`≥768px` 侧栏可折叠；`<768px` 顶部导航收纳
- 动效只用 CSS：页面切换 Vue `<Transition>` 淡入上滑 300ms、卡片悬浮、AI 打字光标闪烁（`cursor: blink` 1s）

## 六、后端 API 契约（types/index.ts 镜像依据）

统一响应壳 `Result<T> = { code: number, message: string, data: T }`，`code===0` 成功。除 `POST /api/auth/login` 与 `GET /api/health` 外全部需要认证（Authorization header 或 HttpOnly cookie，header 优先）；401 时响应体同为 Result 壳。cookie 方案下同源请求（Vite proxy / nginx 反代）浏览器自动携带，前端无需处理。

> 标注 🆕 的接口为本轮后端新增/改动。

| 接口 | 请求 | 响应 data |
|------|------|-----------|
| `POST /api/auth/login` 🆕 | `{username, password}` | `string`（JWT）；同时 `Set-Cookie: nb_token=…; HttpOnly; SameSite=Lax; Path=/` |
| `POST /api/auth/logout` 🆕 | — | `null`；清除 nb_token cookie |
| `GET /api/auth/me` | — | `{id, username, role, createdAt}` |
| `GET/POST /api/kb`、`GET/PUT/DELETE /api/kb/{id}` | `{name, description?, color?}` | `Kb = {id, name, description?, color?, createdAt}` |
| `POST /api/document`(multipart) | `file` + `kbId?` | `Document = {id, kbId?, title, fileType, status, chunkCount?, error?, createdAt}` |
| `POST /api/document/search` | `{query, kbId?, topK?}` | `SearchResult[] = {docId, title, content, score}` |
| `GET /api/document?kbId=` | — | `Document[]` |
| `DELETE /api/document/{id}` 🆕 | — | `null`；向量行（`DELETE FROM vector_store WHERE metadata->>'docId'=?`）+ 磁盘文件 + 元数据全删 |
| `POST /api/chat/stream`(SSE) | `{conversationId?, kbId?, question}` | 事件序列见 6.1 |
| `POST /api/conversation` | `{title?, kbId?}` | `Conversation = {id, title, kbId?, createdAt, updatedAt}` |
| `GET /api/conversation` | — | `ConversationView[] = {id, title, kbId?, createdAt, updatedAt, lastMessageContent?}` |
| `GET /api/conversation/{id}/messages` | — | `MessageView[] = {id, role(USER/ASSISTANT), content, sources?, createdAt}` |
| `DELETE /api/conversation/{id}` | — | `null` |
| `POST /api/baguwen/import` | `{urls: string[]}` | `ImportTask[] = {id, url, status, errorMsg?, articleId?, attempt, createdAt, updatedAt}` |
| `GET /api/baguwen/tasks?status=` | — | `ImportTask[]` |
| `POST /api/baguwen/tasks/{id}/retry` | — | `ImportTask` |
| `GET /api/baguwen?tag=&keyword=&page=&size=` | — | MyBatis-Plus `Page<ArticleView>`：`{records, total, size, current}`，`ArticleView = {id, title, url, tags, createdAt}` |
| `GET /api/baguwen/{id}` | — | `{id, title, url, content, tags, createdAt, qaPairs: {id, question, answer, difficulty}[]}` |
| `GET /api/baguwen/tags` | — | `{tag, count}[]` |
| `DELETE /api/baguwen/{id}` | — | `null` |
| `POST /api/baguwen/{id}/favorite` | `{kbId}` | `{embedded: number}` |

### 6.1 SSE 事件协议（`POST /api/chat/stream`）

事件顺序：`meta`（必发，`{conversationId}`，无 conversationId 时后端自动建会话）→ `sources`（可选，RAG 模式检索命中时发 `RagSource[] = {docId, title, content, score}`）→ `delta`*（文本增量，纯文本 data）→ `done`；出错时发 `error`（data 为 "stream error"）。客户端断开（AbortController）即停止生成，后端已生成部分仍会持久化。

## 七、页面功能点

### 7.1 Login
用户名密码表单 → 登录成功（后端 Set-Cookie，前端不存 token）→ 调 `/api/auth/me` 确认 → 跳转原目标页；接口 401 全局拦截跳登录。登录失败展示后端中文 message。登出：`POST /api/auth/logout` 后清空本地状态跳登录。

### 7.2 Dashboard
- 四模块入口卡片（GlassCard + ModuleCard，四色强调，显示模块一句描述）
- 最近会话列表（`GET /api/conversation` 前 5 条）与最近文档（`GET /api/document` 前 5 条）
- AI 日报占位卡：「后端开发中（Phase 5）」

### 7.3 KnowledgeBase
- 左侧知识库列表：创建（名称 + 描述 + 8 色预设选择器，存 `Kb.color`）、切换、删除（确认弹窗）
- 文档区：FileUploader 拖拽上传（多格式 PDF/Word/MD/TXT）+ 上传进度；文档列表展示 `status` 徽标（PENDING 灰 / PROCESSING 蓝 / READY 绿 / FAILED 红 + error 文案），处理中每 2s 轮询刷新直至无 PENDING/PROCESSING
- 语义搜索框：自然语言查询 → 结果列表（标题 + 片段 + 相似度分数），无 kbId 时全局搜
- 文档删除：`DELETE /api/document/{id}`（确认弹窗 → 从列表移除）

### 7.4 Chat（核心页面）
- 左侧会话列表（新建/切换/删除；移动端收进抽屉，按钮呼出）；右侧消息区
- 模式切换：普通模式 / 知识库模式（下拉选 KB，选中后请求带 kbId）
- 发送：`POST /api/chat/stream`（fetch + sse.ts 解析）；首条消息自动建会话，从 `meta` 事件取 conversationId 并写入会话列表
- 流式打字机效果（delta 追加 + 光标闪烁）；「停止生成」按钮（AbortController）
- MarkdownRenderer 渲染助手消息（代码高亮 + 代码块一键复制）；知识库模式展示 sources 引用卡片（点击无跳转，仅展示标题/片段）
- 切换会话时加载 `GET /api/conversation/{id}/messages`；`error` 事件 → 已生成部分保留 + 错误提示条
- 空状态：无会话时引导新建

### 7.5 Baguwen
- 顶部导入区：多行 URL 输入（一行一个）→ `POST /api/baguwen/import` → 任务进度列表（轮询 2s，SUCCESS/FAILED 停止对应任务轮询），FAILED 可重试
- 文章列表：卡片网格 + 标签筛选（`GET /api/baguwen/tags`，点击切换）+ 关键词搜索 + 分页（size 20）
- 详情页（路由 `/baguwen/:id`）：标题/标签/原文链接 + 问答对卡片列表（question + answer 分条 + difficulty 徽标）
- 文章操作：删除、收藏进知识库（弹窗选 KB → 成功提示 embedded 数量）
- 空状态：引导输入 URL

### 7.6 DailyNews
静态占位页：设计文档风格空态 + 「Phase 5 后端就绪后接入」说明。

## 八、错误处理

- 响应壳 `code!==0` → 抛 `Error(message)`，页面统一 nMessage.error 展示（后端中文 message 透传）
- HTTP 401 → 清 token → 跳登录并提示；网络错误 → 提示重试
- 上传失败 / 文档 FAILED / 八股文任务 FAILED：页内可见状态 + 可操作入口（重试/重新上传）
- SSE：`error` 事件或异常中断 → 保留已生成文字，标记「已中断」；AbortController 主动停止不算错误
- 加载态：Naive UI skeleton 脉冲（设计文档规范：骨架屏 → 内容渐显 200ms）

## 九、测试与验证

- **前端 Vitest 单测**：
  - `utils/sse.ts`：分块到达的跨事件拼接、meta/sources/delta/done/error 全事件序列、提前终止
  - `stores/user.ts`：登录/登出状态流转、401 清登录态
  - `stores/chat.ts`：流式追加、中断保留已生成内容、meta 回填 conversationId
  - 组件：MarkdownRenderer（渲染/消毒/代码块复制）、ChatMessage（用户/助手/引用来源/打字光标）
- **后端测试**（随本轮后端改动，沿用现有假依赖风格）：`DELETE /api/document` 全删链路（向量+文件+元数据）、login Set-Cookie、logout 清 cookie、filter cookie 兜底解析
- **静态验证**：前端 `npm run typecheck` + `npm run build` 全绿；后端 `mvn test` 全绿
- **真实冒烟**（后端跑 8081）：登录（cookie 落盘）→ 建 KB（选色）→ 上传文档（真 embedding）→ 语义搜索 → RAG 模式流式问答（sources 展示）→ 普通模式问答 → 文档删除 → 八股文 URL 导入 → 任务 SUCCESS → 筛选/详情 → 收藏进 KB → 登出；日报页可见占位

## 十、迭代划分

- **迭代 1**：后端 cookie 认证改造（login Set-Cookie + logout + filter cookie 兜底 + 测试）→ 前端脚手架（Vite/Vue/TS/Tailwind/Pinia/Router 配置）→ 设计系统（GlassCard/主题/布局 AppHeader+AppSidebar/移动端底部 Tab）→ request.ts + types → 登录 → Chat（SSE 全链路）→ 测试与冒烟（登录 + 双模式对话）
- **迭代 2**：后端 `DELETE /api/document`（+ 测试）→ 知识库（KB CRUD 含色板/上传/搜索/状态轮询/删除）→ 八股文库（导入/任务/列表/详情/收藏）→ Dashboard → 日报占位 → 全量测试与冒烟

## 十一、与设计文档的偏差记录

| 项 | 设计文档 | 本设计 | 理由 |
|----|---------|--------|------|
| TipTap / ECharts / GSAP | 引入 | 裁剪 | 无对应后端能力，YAGNI |
| EventSource | SSE 接收 | fetch + ReadableStream | 接口为 POST + Bearer |
| 目录名 | `neural-base-frontend/` | `frontend/` | 与仓库 `backend/` 布局一致 |
| 亮暗切换 composable | 有 useDarkMode | 仅深色 | 设计文档风格为深色唯一；Phase 6 再议 |
| Vite 版本 | 5.x | 7 | 当前稳定版（Node 22 支持） |
