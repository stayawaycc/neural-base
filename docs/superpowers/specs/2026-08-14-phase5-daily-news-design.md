# Phase 5 AI 日报设计规格

> 日期:2026-08-14　状态:已敲定(经 brainstorming 澄清)
> 所属:Phase 5(前端收尾已完成后的下一个 Roadmap 阶段)
> 需求:定时爬取 + DeepSeek 单篇汇总 + **手动爬取** + 前端时间线页

---

## 一、已敲定决策

| # | 决策点 | 结论 |
|---|--------|------|
| 1 | 首批信息源 | GitHub Trending(Jsoup 列表页)+ Hacker News(官方 API top 30);机器之心/InfoQ 等反爬风险源后续再加 |
| 2 | AI 汇总 | 单篇一次 DeepSeek 调用:中文一句话摘要 + 固定分类(大模型/框架/工具/论文/其他);失败仍入库,summary 为 null |
| 3 | 调度 | **不引入 Quartz**:Spring `@Scheduled` cron 配置化(`neuralbase.news.cron`,默认每日 8:00);手动触发 `POST /api/news/crawl` 异步执行 |
| 4 | 手动爬取 | 用户明确要求:按钮/接口触发全源一轮;`crawl_task` 表记录;RUNNING 时拒绝重复触发(400) |
| 5 | 前端 | 时间线页(卡片+分类彩标+摘要+原文链接)+ 一键收藏进 KB + 已读标记(单条/批量) |
| 6 | 收藏管线 | 复用 Document + TextChunker + VectorStore(正文=标题+摘要),fileType 用 `NEWS` |

**不做(YAGNI)**:crawler_source 动态配置管理 UI、新闻检索、多语言摘要、按源单独触发、分页(limit 200 全量+前端筛选)。

## 二、后端设计

### 2.1 数据(复用 V1 已建三表)

- `crawler_source`:DataInitializer 种子写入两个源(GitHub Trending、Hacker News),`ON CONFLICT DO NOTHING`;`last_crawl` 每次爬取后更新
- `daily_news`:`url` 去重判据(已存在跳过);`category` 存中文枚举值;`is_read` 默认 false
- `crawl_task`:type=`DAILY_NEWS`;状态 PENDING/RUNNING/SUCCESS/FAILED;progress 0/50/100(爬取完成=50,汇总完成=100)

### 2.2 组件

- `news/NewsItem.java`(record:title,url,sourceId,sourceName)
- `news/NewsFetcher.java`(接口 `List<NewsItem> fetch()`)+ `GithubTrendingNewsFetcher`(Jsoup)+ `HackerNewsNewsFetcher`(HN API,jsoup connect 拉 JSON + ObjectMapper 解析)
  - 测试 profile 下用假实现(`@Profile("!test")` 真实实现 + 测试 `@Primary` 假件,沿用 Phase 4 模式)
- `service/DailyNewsService.java`:
  - `crawlAsync()`(@Async):建任务 RUNNING → 逐源 fetch(失败记日志继续下一源)→ URL 去重入库(progress 50)→ 逐条 `NewsAiService.summarize` 回填 summary/category(失败保留 null,progress 100)→ SUCCESS;异常 → FAILED + error
  - `list(category, unreadOnly)`、`markRead(id, isRead)`、`markAllRead()`、`favorite(id, kbId)`(构造 NEWS Document:title + summary 文本 → chunk → vector store → READY)
  - `latestTask()`
- `service/NewsAiService.java`:DeepSeek(OpenAI 兼容 ChatModel,同 ChatConfig)prompt 输出 JSON `{summary, category}`;category 校验映射到五类中文枚举,非法归「其他」
- `controller/DailyNewsController.java`:
  - `GET /api/news`(category/unreadOnly 可选,limit 200)
  - `POST /api/news/crawl`(RUNNING 拒绝)
  - `PUT /api/news/{id}/read` `{isRead}`
  - `PUT /api/news/read-all`
  - `POST /api/news/{id}/favorite` `{kbId}`
  - `GET /api/news/task/latest`
- `@EnableScheduling`(NeuralBaseApplication)+ `DailyNewsJob`(`@Scheduled` 调 `crawlAsync`,任务已 RUNNING 则跳过)
- `application.yml`:`neuralbase.news.cron: 0 0 8 * * *`

### 2.3 后端测试

- `DailyNewsServiceTest`:假 fetcher + 假 ChatModel——爬取流程(去重、AI 失败仍入库、任务状态流转、手动触发 RUNNING 拒绝)、已读、收藏向量落库
- `DailyNewsControllerTest`:HTTP 层(列表/触发/已读/收藏/任务)
- `NewsAiServiceTest`:prompt 解析、分类映射、非法分类兜底

## 三、前端设计

- `api/news.ts`:`listNews(params)`、`triggerCrawl()`、`latestTask()`、`markRead(id, isRead)`、`markAllRead()`、`favoriteNews(id, kbId)`
- `stores/news.ts`:items/task/categories/筛选状态;任务 PENDING/RUNNING 时 2s 轮询(仿 document store)
- `DailyNewsView.vue` 重写:
  - 工具栏:「立即爬取」按钮(loading 态 + 任务状态徽标)、分类 chips(全部/大模型/框架/工具/论文/其他)、仅未读开关、「全部已读」
  - 卡片时间线:分类彩标(沿用 emerald 系模块色)、标题(点击 `target="_blank"` 打开原文 + 调 markRead)、一句话摘要(summary null 时显示「暂无摘要」)、来源名、未读圆点、「收藏」按钮(弹窗选 KB,复用八股文收藏弹窗样式)
  - 空态:「还没有日报,点击右上角立即爬取」
  - 亮暗两态按既有映射表
- `types/index.ts`:`NewsItem`/`NewsTask` 类型

## 四、验收标准

1. **自动化**:后端新增测试绿 + 94 存量不回归;前端 64 存量 + 新增绿;typecheck 无输出;build 成功
2. **CDP + 真实冒烟**:手动爬取一轮(GitHub Trending + HN 真抓)→ DeepSeek 真实摘要 → 时间线渲染/分类筛选/未读筛选 → 点标题新标签 + 自动已读 → 全部已读 → 收藏进 KB(RAG 可命中)
3. **不做**:Quartz、动态源管理 UI、新闻搜索
