# NeuralBase 项目进度记录

> 最后更新：2026-08-12（Phase 4 后端完成）。本文件记录「设计文档 vs 实际实现」的逐项核对结果。✅=已实现并验证　⏸=部分实现（已裁决延迟）　❌=未开始
> 对照基准：`docs/NeuralBase-系统设计文档.md`；各阶段实施计划见 `docs/superpowers/plans/`。

---

## 一、Roadmap 阶段状态

| 阶段 | 目标 | 状态 | 说明 |
|------|------|------|------|
| **Phase 1** | 脚手架、数据库、认证 JWT、知识库 CRUD | ✅ | 完成 |
| **Phase 2** | 上传 + SiliconFlow Embedding + pgvector 检索 | ✅ | 完成，真实 embedding 冒烟通过 |
| **Phase 3** | AI 对话（SSE 流式 + RAG 模式 + DeepSeek） | ✅ | 完成，真实冒烟双模式通过 |
| **Phase 4** | 八股文爬取 + DeepSeek 结构化整理 | ✅ | 后端完成（前端仍 ❌） |
| **Phase 5** | AI 日报定时爬取 + DeepSeek 汇总 | ❌ | 未开始 |
| **Phase 6** | UI 精细化打磨、动画、响应式 | ❌ | 依赖前端，前端未开始 |
| **Phase 7** | Docker 本地部署、性能优化、压测 | ❌ | 未开始 |
| **Phase 8** | Docker 部署文档 | ❌ | 未开始 |

---

## 二、四大功能模块逐项核对

### 1️⃣ 知识库（RAG）

| 功能点 | 状态 | 说明 |
|--------|------|------|
| 多格式上传 | ⏸ | 后端 multipart 上传 + Tika 提取（PDF/Word/MD/TXT）✅；前端拖拽上传 ❌ |
| 异步解析（提取→切片→Embedding→入库） | ✅ | `DocumentService` + `@Async`，状态机 PENDING→PROCESSING→READY/FAILED |
| 语义搜索 | ✅ | `POST /api/document/search`，pgvector 余弦距离 |
| 文档阅读器（文件树/大纲） | ❌ | 前端未开始 |
| RAG 问答 | ✅ | Phase 3，请求带 `kbId` 进入知识库模式，引用存 `message.sources` |

### 2️⃣ AI 对话

| 功能点 | 状态 | 说明 |
|--------|------|------|
| 双模式切换（普通/知识库） | ✅ | 请求 `kbId` 有无决定；知识库模式注入检索片段 |
| 模型切换（flash/pro） | ⏸ | 仅 `deepseek-v4-flash`，`neuralbase.ai.deepseek-model` 可配，参数已预留 |
| 流式输出 SSE | ✅ | `POST /api/chat/stream`，事件 `meta→sources→delta*→done` |
| 停止生成 | ⏸ | 客户端断开即取消 Flux（无显式停止接口） |
| Markdown 渲染 / 代码操作 | ❌ | 前端未开始 |
| 会话管理 | ⏸ | 列表 + 删除 + 自动标题（首条问题前 20 字）✅；重命名/收藏 ❌（裁决延后 Phase 6） |
| 反馈机制（点赞/点踩） | ❌ | 裁决延后 Phase 6 |

### 3️⃣ 八股文库

| 功能点 | 状态 |
|--------|------|
| URL 输入 / 批量导入 | ✅ `POST /api/baguwen/import`，异步任务 + 进度表，可重试 |
| 智能爬取（Jsoup + Playwright 兜底） | ✅ `CompositeCrawler`：静态优先，JS 渲染站点降级 Playwright |
| AI 结构化提取问答对 | ✅ `BaguwenAiService`：DeepSeek 非流式提取 `{qa_pairs, tags}` |
| 分类标签 / 筛选 / 全文搜索 | ✅ tags 聚合 + 分页列表（tag 精确 / keyword 匹配标题正文与问答对） |
| 收藏进知识库（embedding） | ✅ `POST /api/baguwen/{id}/favorite` 复用 VectorStore 管线 |
| 阅读模式 / 前端 | ❌ 前端未开始（Phase 6） |

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
| 安全 | Spring Security + JWT | ✅ | — |
| 部署 | Docker + nginx | ❌ 无 `docker/`、`docker-compose.yml`、`.env.example` | Phase 7/8 |

> **架构偏差注意**：设计文档的「MySQL + PostgreSQL + Redis 三库」架构在实际实现中简化为「单 PostgreSQL（业务 + 向量同库）」+ 无 Redis。Phase 4/5（爬虫/日报）如需队列限流再考虑引入。

---

## 四、测试与冒烟

- **自动化测试**：66/66 绿（`mvn test`，测试用假 ChatModel + 假 EmbeddingModel + 假 Crawler，不依赖真实 API key/浏览器）
  - Auth(5)、Kb(1)、Document 上传/处理/检索(8)、Chat 服务(3)、Chat HTTP 层 SSE(3)、Conversation CRUD(2)、TextChunker(5)、TextExtractor(2)、AiContext(1)、ChatContext(1)、SchemaMigration(3)、应用上下文(1)
  - Phase 4 新增：ContentExtractor(2)、JsoupCrawler(2)、CompositeCrawler(4)、BaguwenAiService(3)、BaguwenService(12)、BaguwenController(8)
- **真实冒烟**：
  - Phase 2：SiliconFlow embedding 链路 ✅
  - Phase 3：普通模式（DeepSeek 流式）+ RAG 模式（真 embedding 检索 → 引用回答 → `message.sources` 落库）✅
  - Phase 4（2026-08-12）：真实 URL 导入（阮一峰周刊 287 期）→ Jsoup 抓取 103KB → 正文提取 6.8K 字符 → DeepSeek 提取 10 问答对 + 5 标签 → 任务 SUCCESS → 收藏进 KB → 检索命中问答对 ✅
  - 冒烟发现并修复：① 生产环境 ArticleCrawler 三实现注入歧义（测试 @Primary 掩盖，改为真实爬虫 `@Profile("!test")` + `CompositeCrawler @Primary`）；② Jsoup 壳页面（VuePress 等 JS 渲染站）183 字符正文误判"有实质内容"，兜底阈值 100→500；③ Playwright 渲染超时 15s→45s（重型首屏）；④ 计划中 javaguide URL 已失效（404），冒烟改用有效站点
- **运行**：本机 8080 被占用，用 `--server.port=8081`；**本地 PG 密码 `Huangwei990758`**（`export DB_PASSWORD='Huangwei990758'`，yml 默认值同；⚠️ 早期文档误记 `root`，已更正，2026-08-12 实测）

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

---

## 五、已裁决的延后项（有意不做，非遗漏）

- 会话重命名/收藏、消息反馈（点赞/点踩）→ Phase 6
- 模型双切换（pro）→ 需用时加一行配置
- SSE 前端用 fetch + ReadableStream（非原生 EventSource，Bearer header）
- 测试 mock 用 `@Configuration` 组件扫描自动注册（非 `@TestConfiguration`）

---

## 六、前端状态

**完全未开始**（Vue 3 + Vite + Pinia + Naive UI + Tailwind，设计文档有完整目录结构与 UI 规范）。Phase 6 前端落地时，本文件需同步更新所有标 ❌ 的前端功能点。

---

## 七、下一步建议

> Phase 1-4 后端已完成（知识库 RAG、AI 对话、八股文库）。前端整体未开始。

1. **前端**（价值最高）：对接现有 API，做知识库 + AI 聊天（SSE fetch 流式）+ 八股文库 UI（导入/阅读/筛选/收藏）
2. **Phase 5** AI 日报（Quartz 定时爬取 + DeepSeek 汇总；`crawler_source`/`daily_news`/`crawl_task` 表已建好）
3. **Phase 7/8** Docker 部署 + 文档
4. 可选：补 SpringDoc OpenAPI、Redis（若需要限流/队列）
5. 八股文库遗留：阅读模式、JS 渲染站点需本机先 `playwright install chromium`（Java CLI：`mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"`）
