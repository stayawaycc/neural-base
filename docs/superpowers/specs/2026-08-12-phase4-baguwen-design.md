# NeuralBase Phase 4 — 八股文库设计

> 日期：2026-08-12　范围：后端全链路（前端未开始）　基准：`docs/NeuralBase-系统设计文档.md` 3️⃣八股文爬取整理

## 一、目标与范围

用户粘贴 URL，系统自动爬取文章正文、用 DeepSeek 提取「问题-答案」对并分类打标签，形成可筛选/搜索的面试题库；支持批量导入（多 URL 异步队列）与「收藏到知识库」。

**本轮范围（已确认）**：
- ✅ URL 提交 → 爬取 → AI 结构化问答对 → 标签分类 → CRUD + 筛选/搜索 API
- ✅ 批量导入：多 URL 提交，每 URL 一条任务，异步处理，进度可观测，失败可重试，启动断点续跑
- ✅ 收藏到知识库：把问答对文本 embedding 进指定 KB（复用现有 embedding 管线）

**范围外（后续 Phase 处理）**：前端阅读模式/卡片展示、会话重命名等 Phase 6 事项。

## 二、已裁决的技术决策

| 决策 | 结论 | 理由 |
|------|------|------|
| 爬虫引擎 | Jsoup 静态优先 + Playwright JS 渲染兜底（`CompositeCrawler`） | 兼容 SSR 与 JS 渲染站点 |
| 批量队列 | @Async 进程内 + `baguwen_import_task` 进度表，启动时续跑 PENDING/PROCESSING | 无新依赖，单用户够用；任务表提供可观测性 |
| 收藏 embedding 内容 | 问答对拼文本（`Q:…\nA:…`） | 命中具体问题，符合面试刷题用途 |
| 文章状态 | 文章表不带 status，状态全在任务表；文章只存提取成功的产物 | 避免双状态源 |
| 批量分组 | 不加 batch 分组，每 URL 一条任务 | 单用户按 URL 足够 |
| 全文搜索 | 关键词筛选用 ILIKE（title/content/question/answer） | 单用户量小，不引入 tsvector |

## 三、数据模型（Flyway V4__baguwen_tables.sql）

### `baguwen_article` — 文章（仅提取成功后创建）

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGSERIAL PRIMARY KEY | |
| title | VARCHAR(255) NOT NULL | 页面标题 |
| url | TEXT | 来源链接 |
| content | TEXT NOT NULL | 提取后的正文 |
| tags | JSONB | 如 `["Java","JVM"]` |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT now() | |
| updated_at | TIMESTAMPTZ NOT NULL DEFAULT now() | |

### `baguwen_import_task` — 导入任务（可观测性）

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGSERIAL PRIMARY KEY | |
| url | TEXT NOT NULL | 每个 URL 一条任务 |
| status | VARCHAR(16) NOT NULL | PENDING / PROCESSING / SUCCESS / FAILED |
| error_msg | TEXT | 失败原因 |
| article_id | BIGINT REFERENCES baguwen_article(id) ON DELETE SET NULL | 成功后回填 |
| attempt | INT NOT NULL DEFAULT 0 | 重试次数 |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT now() | |
| updated_at | TIMESTAMPTZ NOT NULL DEFAULT now() | |

索引：`idx_baguwen_task_status ON baguwen_import_task(status)`、`idx_baguwen_article_title ON baguwen_article(title)`。

### `baguwen_qa_pair` — 问答对（独立存储可查询）

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGSERIAL PRIMARY KEY | |
| article_id | BIGINT NOT NULL REFERENCES baguwen_article(id) ON DELETE CASCADE | |
| question | TEXT NOT NULL | |
| answer | TEXT NOT NULL | |
| difficulty | VARCHAR(8) NOT NULL | 简单 / 中等 / 困难 |
| sort_order | INT NOT NULL DEFAULT 0 | 展示顺序 |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT now() | |

索引：`idx_baguwen_qa_article ON baguwen_qa_pair(article_id, sort_order)`。

## 四、架构组件与数据流

新增包：`com.neuralbase.crawler`、`com.neuralbase.baguwen`（entity/mapper/service/controller）。

### 4.1 爬取链路

```
POST /api/baguwen/import {urls:[]}
  → 每个 URL 建 baguwen_import_task(PENDING) → 返回 tasks
  → @Async processTask(id):
      task→PROCESSING
      → CompositeCrawler.fetch(url):
          JsoupCrawler 静态解析
          └ 无正文/失败 → PlaywrightCrawler 渲染兜底
      → ContentExtractor 提取正文（文本密度评分，剥 nav/aside/script/ad/footer）
      → BaguwenAiService 结构化（DeepSeek 非流式 call）
      → 存 article + qa_pairs → task→SUCCESS 回填 article_id
      → 任一步异常 → task→FAILED + error_msg
```

### 4.2 组件清单

| 组件 | 职责 |
|------|------|
| `ArticleCrawler`（接口） | `CrawledArticle fetch(String url)`；抛 `CrawlException` |
| `JsoupCrawler` | Jsoup 静态抓取，返回原始 HTML + title |
| `PlaywrightCrawler` | 无正文时 Playwright 渲染出 HTML，返回同一结构 |
| `CompositeCrawler` | 编排兜底链（Jsoup→Playwright），聚合正文 |
| `ContentExtractor` | 通用正文提取：对 `<article>`/`<main>`/body 候选做文本密度评分，选最高分块；剥离 `script/style/nav/aside/footer/ad` |
| `CrawledArticle` | 载体：title、url、html |
| `BaguwenAiService` | 复用 DeepSeek ChatModel bean，非流式 `call()`；Prompt：正文→`{qa_pairs:[{question,answer,difficulty}], tags:[]}`；strip markdown 代码块后 Jackson 解析，校验字段非空 |
| `BaguwenService` | importUrls / @Async processTask / retry / favorite / 查询 / 删除 / tags 聚合 / 启动续跑 |
| `BaguwenController` | REST API |
| Mapper ×3 | `BaguwenArticleMapper`、`BaguwenImportTaskMapper`、`BaguwenQaPairMapper`（IRepository） |

### 4.3 AI 结构化输出形状

```json
{
  "qa_pairs": [
    { "question": "JVM 内存模型有哪些区域？", "answer": "…", "difficulty": "中等" }
  ],
  "tags": ["Java", "JVM"]
}
```

Prompt 要点：问题简洁、答案分条、标注难度、输出 JSON 数组（设计文档 3️⃣ 的模板基础上扩展 tags 字段）。

### 4.4 断点续跑与重试

- 启动时 `@PostConstruct`：查询 status IN (PENDING, PROCESSING) 的任务重新排队（@Async 进程内重启丢失的补偿）。
- `POST /api/baguwen/tasks/{id}/retry`：仅允许 FAILED 任务重跑——attempt+1、状态重置为 PENDING、重新排队；非 FAILED 状态返回 400。

### 4.5 收藏到知识库

`favorite(articleId, kbId)`：
1. 校验 kbId 存在（复用 KbMapper）；校验文章存在且 qa_pairs 非空，否则 400
2. QA 对按 sort_order 拼 `Q: {question}\nA: {answer}\n` 文本
3. 用 `TextChunker` 切片 → 现有 `VectorStore` 的 add（与 DocumentService 检索同链）
4. 返回收藏的切片数

## 五、API 设计

| 方法 | 路径 | 请求 | 响应 | 说明 |
|------|------|------|------|------|
| POST | `/api/baguwen/import` | `{urls: ["…", …]}` | `{tasks: […]}` | 批量建任务，返回任务列表 |
| GET | `/api/baguwen/tasks` | `?status=` | 分页任务列表 | 进度可观测 |
| POST | `/api/baguwen/tasks/{id}/retry` | — | `Result` | 重试 FAILED 任务 |
| GET | `/api/baguwen` | `?tag=&keyword=&page=&size=` | 分页文章列表 | tag 精确匹配（jsonb `@>`）；keyword 同时匹配 title/content（ILIKE）与问答对 question/answer（EXISTS 子查询） |
| GET | `/api/baguwen/{id}` | — | 文章 + qa_pairs[] | 详情 |
| GET | `/api/baguwen/tags` | — | `[{tag, count}]` | 标签聚合 |
| DELETE | `/api/baguwen/{id}` | — | `Result` | 删除，级联 qa_pair，task 回填置空 |
| POST | `/api/baguwen/{id}/favorite` | `{kbId}` | `{embedded: n}` | 问答对文本入库指定 KB |

统一包裹现有 `Result`（code/message/data），错误走 `GlobalExceptionHandler`（IAE→400）。

## 六、错误处理

- 爬取失败（网络/超时/无正文）→ task FAILED + error_msg，可重试
- AI JSON 解析失败 → 自动重试一次；仍失败 → FAILED
- 收藏：kbId 不存在 / 文章或问答对为空 → 400
- 空 urls 提交 → 400

## 七、测试策略（全离线，沿用现有 mock 模式）

沿用项目模式：假 `@Configuration`+`@Primary` Bean 组件扫描自动注册；集成测试 `@BeforeEach` 清表。

| 测试类 | 覆盖 |
|--------|------|
| `ContentExtractorTest` | 带广告/导航的 HTML fixture → 提取正文；去 script/nav/footer |
| `JsoupCrawlerTest` | 本地 HTML fixture → CrawledArticle（title/html） |
| `CompositeCrawlerTest` | Jsoup 成功不调 Playwright；Jsoup 失败降级 Playwright（假 Crawler） |
| `BaguwenAiServiceTest` | 假 ChatModel 返回含 markdown 包装的 JSON → 解析出 qa_pairs+tags；畸形 JSON → 异常 |
| `BaguwenServiceTest` | import→process→article+qa 落库；失败→FAILED+error_msg；retry 重跑；favorite 复用 VectorStore（假 embedding）|
| `BaguwenControllerTest` | HTTP 层：import/tasks/retry/列表/详情/tags/delete/favorite；鉴权 401 |

真机冒烟（可选）：爬一个真实 URL + 真 DeepSeek key，验证端到端。

## 八、与现有系统的衔接

- 复用：DeepSeek ChatModel bean、`TextChunker`、`VectorStore`（embedding）、`KbMapper`、`Result`、`GlobalExceptionHandler`、鉴权过滤器
- 新增依赖：`com.microsoft.playwright:playwright`（唯一新依赖）。首次运行需 `playwright install chromium` 下载浏览器（本机 Windows 手动执行一次）。**测试全用假 `ArticleCrawler`，不触碰浏览器**，CI/`mvn test` 不依赖浏览器二进制

## 九、验证标准

1. `mvn test` 全绿（新增测试全通过，不影响既有 35 个）
2. 单 URL 导入：PENDING→PROCESSING→SUCCESS，article + qa_pairs 落库
3. 失败任务 → FAILED + error_msg，retry 后成功
4. 收藏 → 目标 KB 可检索到问答对内容
5. 可选真机冒烟：爬真实 URL + DeepSeek 端到端
