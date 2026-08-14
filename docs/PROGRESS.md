# NeuralBase 项目进度记录

> 最后更新：2026-08-13（Phase 6 前端完成）。本文件记录「设计文档 vs 实际实现」的逐项核对结果。✅=已实现并验证　⏸=部分实现（已裁决延迟）　❌=未开始
> 对照基准：`docs/NeuralBase-系统设计文档.md`；各阶段实施计划见 `docs/superpowers/plans/`。

---

## 一、Roadmap 阶段状态

| 阶段 | 目标 | 状态 | 说明 |
|------|------|------|------|
| **Phase 1** | 脚手架、数据库、认证 JWT、知识库 CRUD | ✅ | 完成 |
| **Phase 2** | 上传 + SiliconFlow Embedding + pgvector 检索 | ✅ | 完成，真实 embedding 冒烟通过 |
| **Phase 3** | AI 对话（SSE 流式 + RAG 模式 + DeepSeek） | ✅ | 完成，真实冒烟双模式通过 |
| **Phase 4** | 八股文爬取 + DeepSeek 结构化整理 | ✅ | 后端完成（前端仍 ❌） |
| **Phase 5** | AI 日报定时爬取 + DeepSeek 汇总 | ✅ | 2026-08-14:GitHub Trending + Hacker News 两源、单篇中文摘要+五分类、`@Scheduled` 定时(未引入 Quartz)+ 手动触发 `/api/news/crawl`、任务状态机与去重、时间线页(筛选/已读/收藏进 KB)。真实冒烟:30 条真抓 + 30/30 真实摘要 |
| **Phase 6** | UI 精细化打磨、动画、响应式 | ✅ | 主要页面完成（登录/知识库/对话/八股文/日报占位）+ 文档阅读器 + 亮色模式 + 动画打磨（2026-08-14）；延后项补做另列 |
| **Phase 7** | Docker 本地部署、性能优化、压测 | ✅ | 2026-08-14:后端/前端 Dockerfile + nginx SPA 反代 + docker-compose 一键部署(压测按 YAGNI 裁决不做);本机无 Docker CLI,`docker compose config` 静态校验与容器实跑均留待 Docker 环境按文档验收(已如实记录);compose 变量与 .env.example 已人工交叉核对一致;review 修复 README 端口错误(80 前端/8080 后端) |
| **Phase 8** | Docker 部署文档 | ✅ | 2026-08-14:README + docs/部署文档.md(环境变量表/备份/FAQ);application.yml 恢复跟踪为纯环境变量版,密钥迁移本地 .env |

---

## 二、四大功能模块逐项核对

### 1️⃣ 知识库（RAG）

| 功能点 | 状态 | 说明 |
|--------|------|------|
| 多格式上传 | ✅ | 后端 multipart 上传 + Tika 提取（PDF/Word/MD/TXT）✅；前端拖拽上传 ✅ |
| 异步解析（提取→切片→Embedding→入库） | ✅ | `DocumentService` + `@Async`，状态机 PENDING→PROCESSING→READY/FAILED；提取全文落 `document.content`（V5） |
| 语义搜索 | ✅ | `POST /api/document/search`，pgvector 余弦距离 |
| 文档阅读器（文件树/大纲） | ✅ | 2026-08-13：独立路由 `/kb/:kbId/reader/:docId?`，两级树（KB→文档，非 READY 置灰带徽标），分格式渲染（md→MarkdownRenderer 锚点模式、pdf→iframe、docx/txt/BAGUWEN→提取文本），md 目录抽屉（点击跳转 + 滚动高亮），下载原文/查看原文外链；后端新增 `GET /api/document/{id}/content` 与 `/{id}/file`（inline 流式） |
| RAG 问答 | ✅ | Phase 3，请求带 `kbId` 进入知识库模式，引用存 `message.sources` |

### 2️⃣ AI 对话

| 功能点 | 状态 | 说明 |
|--------|------|------|
| 双模式切换（普通/知识库） | ✅ | 请求 `kbId` 有无决定；知识库模式注入检索片段 |
| 模型切换（flash/pro） | ⏸ | 仅 `deepseek-v4-flash`，`neuralbase.ai.deepseek-model` 可配，参数已预留 |
| 流式输出 SSE | ✅ | `POST /api/chat/stream`，事件 `meta→sources→delta*→done` |
| 停止生成 | ✅ | 前端停止按钮 + AbortController 断开 SSE |
| Markdown 渲染 / 代码操作 | ✅ | 渲染 + 代码复制；插入编辑器仍无 |
| 会话管理 | ⏸ | 后端列表/删除/自动标题 ✅；前端列表/新建/删除 ✅；重命名/收藏仍延后 |
| 反馈机制（点赞/点踩） | ❌ | 裁决延后 Phase 6 |

### 3️⃣ 八股文库

| 功能点 | 状态 |
|--------|------|
| URL 输入 / 批量导入 | ✅ `POST /api/baguwen/import`，异步任务 + 进度表，可重试 |
| 智能爬取（Jsoup + Playwright 兜底） | ✅ `CompositeCrawler`：静态优先，JS 渲染站点降级 Playwright |
| AI 结构化提取问答对 | ✅ `BaguwenAiService`：DeepSeek 非流式提取 `{qa_pairs, tags}` |
| 分类标签 / 筛选 / 全文搜索 | ✅ tags 聚合 + 分页列表（tag 精确 / keyword 匹配标题正文与问答对） |
| 收藏进知识库（embedding） | ✅ `POST /api/baguwen/{id}/favorite` 复用 VectorStore 管线 |
| 阅读模式 / 前端 | ✅ 列表/筛选/详情/收藏前端完成；原文渲染仍是纯文本问答对 |

### 4️⃣ AI 日报

| 功能点 | 状态 |
|--------|------|
| 定时爬取（Quartz）+ 信息源配置 | ❌ |
| AI 汇总分类 | ❌ |
| 时间线 / 一键收藏 / 已读标记 | ❌ |

---

## 三、技术架构：设计文档 vs 实际

| 项 | 设计文档 | 实际 | 差异说明 |
|----|---------|------|---------|
| JDK | OpenJDK 21 | ✅ OpenJDK 21 | — |
| 框架 | Spring Boot 3.2+ | ✅ 3.5.16 | — |
| AI 框架 | Spring AI 1.0+ | ✅ 1.1.8（openai + pgvector-store 模块，**显式 Bean 非 starter**） | — |
| ORM | MyBatis-Plus 3.5+ | ✅ 3.5.17 | — |
| 文档处理 | Tika + PDFBox | ✅ Tika 3.3.2 + parsers | — |
| 对话模型 | DeepSeek V4-Flash/Pro | ✅ `deepseek-v4-flash`（openai 兼容接入） | — |
| Embedding | Qwen3-Embedding-8B | ✅ via SiliconFlow，4096 维 | — |
| 关系库 | **MySQL**（用户/文档元数据） | ❌ **未采用** | 实际为单 PostgreSQL 同库 |
| 向量库 | PostgreSQL + pgvector | ✅ 单 PostgreSQL 18.4 同库 | — |
| 缓存/队列/限流 | **Redis / Redis Stream** | ❌ **未实现** | pom 无 redis 依赖 |
| 爬虫引擎 | Playwright + Jsoup | ✅ Jsoup 1.22.2 + Playwright 1.60.0（`CompositeCrawler` 兜底链） | — |
| 任务调度 | Quartz | ❌ 未实现 | Phase 5 |
| API 文档 | SpringDoc OpenAPI | ❌ 未加 | 可选补 |
| 安全 | Spring Security + JWT | ✅ | cookie 认证（login Set-Cookie + logout 端点 + filter 兜底，header 兼容保留） |
| 前端 | 设计文档 Vue 3 + Vite 5 | ✅ Vue 3.5 + Vite 7 + Pinia 3 + Naive UI 2 + Tailwind 3.4 | TipTap/ECharts/GSAP 裁剪（见 spec） |
| 部署 | Docker + nginx | ❌ 无 `docker/`、`docker-compose.yml`、`.env.example` | Phase 7/8 |

> **架构偏差注意**：设计文档的「MySQL + PostgreSQL + Redis 三库」架构在实际实现中简化为「单 PostgreSQL（业务 + 向量同库）」+ 无 Redis。Phase 4/5（爬虫/日报）如需队列限流再考虑引入。

---

## 四、测试与冒烟

- **自动化测试**：后端 **105/105 绿**（`mvn test`，测试用假 ChatModel + 假 EmbeddingModel + 假 Crawler，不依赖真实 API key/浏览器）；前端 **Vitest 67/67 绿** + typecheck 无输出 + build 成功（2026-08-14，Phase 5 后更新）
  - 后端：Auth(7)+CookieAuth(3)、Kb(1)、Document 上传/处理/检索/删除/竞态防护(11)、Chat 服务(3)+HTTP 层 SSE(3)+Conversation CRUD(2)、TextChunker(5)、TextExtractor(2)、AiContext(1)、ChatContext(1)、SchemaMigration(3)、应用上下文(1)
  - 后端 Phase 4 新增：ContentExtractor(2)、JsoupCrawler(2)、CompositeCrawler(4)、BaguwenAiService(3)、BaguwenService(12)、BaguwenController(8)
  - 前端：GlassCard(2)、user(3)、sse(7)、chat(5)、MarkdownRenderer(2)、ChatMessage(3)、document(5)、baguwen(3)
- **真实冒烟**：
  - Phase 2：SiliconFlow embedding 链路 ✅
  - Phase 3：普通模式（DeepSeek 流式）+ RAG 模式（真 embedding 检索 → 引用回答 → `message.sources` 落库）✅
  - Phase 4（2026-08-12）：真实 URL 导入（阮一峰周刊 287 期）→ Jsoup 抓取 103KB → 正文提取 6.8K 字符 → DeepSeek 提取 10 问答对 + 5 标签 → 任务 SUCCESS → 收藏进 KB → 检索命中问答对 ✅
  - 冒烟发现并修复：① 生产环境 ArticleCrawler 三实现注入歧义（测试 @Primary 掩盖，改为真实爬虫 `@Profile("!test")` + `CompositeCrawler @Primary`）；② Jsoup 壳页面（VuePress 等 JS 渲染站）183 字符正文误判"有实质内容"，兜底阈值 100→500；③ Playwright 渲染超时 15s→45s（重型首屏）；④ 计划中 javaguide URL 已失效（404），冒烟改用有效站点
  - Phase 6（2026-08-13）：登录 cookie 落盘（nb_token HttpOnly）→ 上传 md → PENDING→READY → 删除文档（DB 记录 + 文件均删）→ 退出登录 cookie 清除 ✅；八股文导入：任务状态机 PENDING→PROCESSING→FAILED 与 errorMsg 落库验证通过，但 **SUCCESS 未能在本机复现**——brief 的 287 期 URL 已 404（站点改版，Chrome UA 实测 404），当前最新 407 期被 Cloudflare 指纹拦截，且本机 Playwright 浏览器二进制安装停滞（驱动进程疑似被本地安全软件阻断，curl 同源下载 12MB/s 正常）→ 导入 SUCCESS、收藏、RAG 命中问答对等项留待浏览器/换网络验证（管线代码自 Phase 4 未变，2026-08-12 已真实冒烟通过）
  - Phase 6 终审与修复（2026-08-13）：全分支终审（19 commits）判定 **Ready to complete**，3 个 MUST-FIX 已修复——① `process()` 删除竞态双防护 + 回归测试（删除后迟到的处理回调不再重插向量，杜绝"幽灵向量"：不可删 + RAG 命中 title 为 null，commit `a137558`）② `feedback.ts` discrete API 注入 `darkTheme`（此前 message/dialog 弹层为默认亮色，commit `8df7144`）③ glass-card 修饰类全局化（BaguwenView 卡片 hover 提升生效，同 commit）；另有 20+ 项 DEFER 类 Minor 有意保留（见五）
- **真机冒烟(浏览器 CDP,2026-08-13)发现并修复 4 个真实缺陷**(commit `dff8316`):① **App.vue `<transition mode="out-in">` + 懒加载路由组件 → SPA 跳转后内容区空白**(全站级,非阅读器问题;修复:移除 transition 包裹,切换动画留待「动画打磨」用带 key 方式重做)② **存量文档(用户全部数据)content 为 NULL → 阅读器全报「内容缺失」**(修复:content 接口惰性回填,原文件在磁盘即重提取落库自愈,新增回填测试)③ **PDF iframe 被 Spring Security 默认 `X-Frame-Options: DENY` 阻断**(修复:改为 SAMEORIGIN)④ **知识库页「全部文档」模式点击文档无反应**(修复:入口改用文档自身 kbId)。回归:后端 89/89、前端 50/50 绿。
- 遗留:用户库中有 2 条历史冒烟残留 FAILED 文档(标题为 "404 - Not Found" / "metadata cannot have null values"),未动,待用户决定清理。

- **运行**：本机 8080 被占用，用 `--server.port=8081`；**本地 PG 密码 `root`**（`export DB_PASSWORD='root'`，yml 默认值同；test 库 `neuralbase_test` 同密码；2026-08-13 实测，旧值 Huangwei990758 已失效）

---

## 四点五、Phase 4 执行记录（与计划的偏差，2026-08-12）

计划 `docs/superpowers/plans/2026-08-12-phase4-baguwen.md` 已全部执行完成，以下偏差为执行中发现并修正，后续阶段注意：

| # | 偏差 | 说明 |
|---|------|------|
| 1 | **MyBatis-Plus 分页插件需额外依赖** | 3.5.17 起 `PaginationInnerInterceptor` 拆到 `mybatis-plus-jsqlparser`，starter 不含，pom 需手动加 |
| 2 | **AI 返回 snake_case 与 record camelCase 不匹配** | 提示词要求输出 `qa_pairs`，record 字段是 `qaPairs` → 组件加 `@JsonAlias("qa_pairs")` |
| 3 | **计划中 `favorite()` 方法代码缺失** | Task 5/6 代码片段均未包含，按设计文档 4.5 补全（QA 拼文本→TextChunker→VectorStore，Document 表落 READY 记录） |
| 4 | **测试库 kb 表为空** | 计划测试假设共享库有 kb id=1 残留，新测试库需在 `@BeforeEach` seed（`INSERT ... ON CONFLICT`） |
| 5 | **生产 ArticleCrawler 注入歧义** | 3 个实现无主导致启动失败（测试 @Primary 掩盖）→ 真实爬虫 `@Profile("!test")` + `CompositeCrawler @Primary` |
| 6 | **壳页面误判阈值** | VuePress 等 JS 渲染站静态正文 100~300 字符 ≥ 100 阈值 → 不触发 Playwright 兜底 → 阈值 100→500 |
| 7 | **Playwright 超时 15s 不足** | 重型首屏渲染超时 → 45s |
| 8 | **Flyway checksum 手动登记坑** | 手动执行 SQL + 插 `flyway_schema_history` 时，**checksum 不能用标准 CRC32 手算**（Flyway 11 算法不同）——应复制测试库中 Flyway 自算的权威值，或用 Flyway 命令执行迁移 |
| 9 | **计划中 javaguide URL 已失效** | 冒烟时返回 404，改用阮一峰周刊验证（SSR 站点 Jsoup 直达） |

---

## 五、已裁决的延后项（有意不做，非遗漏）

- 会话重命名/收藏、消息反馈（点赞/点踩）→ Phase 6
- 模型双切换（pro）→ 需用时加一行配置
- SSE 前端用 fetch + ReadableStream（非原生 EventSource，Bearer header）
- 测试 mock 用 `@Configuration` 组件扫描自动注册（非 `@TestConfiguration`）
- Phase 6 终审 DEFER（2026-08-13 裁决保留）：happy-dom ^18 有 RCE 公告（dev-only，修复需大版本升级）；SSE reader 未 releaseLock（GC 兜底）；document/baguwen 轮询 tick 无错误处理（网络失败仅控制台噪音，网络恢复自愈）；登录页回车可重复提交（重复登录无害）；BaguwenDetail 收藏弹窗不重置 kbId 且无 loading（列表页行为更完善）；discrete api 未带 themeOverrides（可选一行补全视觉一致性）；删除落在提取期间的 Windows 文件残留窄窗口（Linux 无此问题）；登录页本地 glass-card 样式副本已冗余（像素一致，仅注释过期）；`data:` 行 trimStart 与 SSE 规范差一个空格（delta 文本无前导空格场景）

---

## 六、前端状态

**已完成**（2026-08-13）：
- 登录页（cookie 认证）；Dashboard（四模块卡 + 最近会话/最近文档 + 日报占位）；知识库页（KB CRUD + 色板、拖拽上传、状态轮询、语义搜索、删除 + READY 文档点击进阅读器）；Chat 页（普通/知识库双模式、SSE fetch 流式 + 停止生成、Markdown 渲染 + 代码复制、会话列表/新建/删除）；八股文库（导入任务、标签筛选列表、详情问答对 + 难度徽标、收藏）；移动端（<768px）底部 Tab 导航；**文档阅读器**（两级树 + 分格式渲染 + md 目录抽屉/滚动高亮 + 下载/原文链接，规格 `docs/superpowers/specs/2026-08-13-document-reader-design.md`）；**亮色模式**（默认亮色 #f5f6f8 + 白卡片、顶栏太阳/月亮切换、localStorage 持久化、naive-ui/弹层/代码高亮全联动，CDP 真机验收亮暗两态，规格 `docs/superpowers/specs/2026-08-13-light-theme-design.md`，计划 `docs/superpowers/plans/2026-08-13-light-theme.md`）。
- **动画打磨**（2026-08-14）：页面切换过渡（带 key 的 transition 重做，CDP 实验证实 out-in 仍空白、plain transition 稳定）、三个列表页 NSkeleton 骨架屏（Dashboard/KB/八股文，含确定性生命周期单测）、按压与涟漪微交互（.btn-press/.upload-ripple/.fade-in）、prefers-reduced-motion 全覆盖，规格 `docs/superpowers/specs/2026-08-13-animation-polish-design.md`。
- **延后项补做**（2026-08-14，前端收尾 4/4 完成）：会话重命名（hover 铅笔内联编辑）与会话收藏（星标 + 「仅看收藏」筛选，V6 `conversation.favorite`）；消息点赞点踩（👍👎 互斥可取消，V6 `message.feedback`，新 MessageController）；八股详情「问答/原文」Tab。规格 `docs/superpowers/specs/2026-08-14-deferred-items-design.md`，计划 `docs/superpowers/plans/2026-08-14-deferred-items.md`。
- **未做项**：日报真实功能（Phase 5）。

---

## 七、下一步建议

> **Roadmap Phase 1-8 全部完成**(2026-08-14)。剩余均为可选增强:

1. Docker 容器实跑验收(本机无 Docker,按 `docs/部署文档.md` 在有 Docker 环境执行)
2. 可选:补 SpringDoc OpenAPI、Redis(若需要限流/队列)、更多新闻源
3. 八股文库遗留:JS 渲染站点需本机先 `playwright install chromium`(Java CLI:`mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"`)
