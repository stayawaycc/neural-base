# AI 日报「真·AI 内容 + 分页」设计规格

> 日期:2026-08-17　状态:已敲定(经 grilling 三轮压测)
> 所属:Phase 5 AI 日报修正与增强;**推翻 `2026-08-14-news-enhancement-design.md` 决策 #8(limit 200 不分页)**

---

## 一、背景:三个真实问题(已实测定位)

| # | 问题 | 实测根因 |
|---|------|----------|
| 1 | GitHub Trending / GitHub Star AI **爬不到任何数据** | 本机安全软件破坏 GitHub/HuggingFace 证书链:Windows Schannel 报 `CRYPT_E_NO_REVOCATION_CHECK`;Java JSSE 报 `PKIX path building failed`(默认 cacerts 建不出链)。**已验证**:JSSE 改用 Windows 根证书库(`trustStoreType=Windows-ROOT`)后访问 api.github.com / huggingface.co 均 200 |
| 2 | 爬取的「AI 热点技术」不是 AI 热点技术 | 三个**通用源**无任何 AI 过滤:GitHub Trending 抓全站热门仓库、Hacker News 抓全站 top、InfoQ 抓全站首页;非 AI 条目直接入库 |
| 3 | 无分页 | `GET /api/news` 硬编码 `LIMIT 200`,前端一次全量拉取、分类在前端本地过滤 |

附加发现:Papers with Code 已 302 迁移至 `huggingface.co/papers/trending`(同样被证书问题波及);InfoQ 的 AI 频道页(`/topic/ai` 500、`/ai`、`/aibriefs`)均为 JS 壳,需 Playwright 渲染;本机已装 Playwright chromium。

## 二、术语表

| 术语 | 定义 |
|------|------|
| AI 原生源 | 上游内容本身即 AI 主题:机器之心、Papers with Code(HF papers)、GitHub `topic:ai` 检索类 |
| 通用源 | 上游是全站/全领域内容:GitHub Trending 原页、Hacker News top、InfoQ 首页、自建源 |
| 上游 AI 化 | 把通用源的抓取目标换成 AI 主题上游(GitHub Search `topic:ai`、HN Algolia `query=AI`、InfoQ `/aibriefs`) |
| 关键词兜底过滤 | 入库前按标题关键词白名单判定,剔除明显非 AI 条目;只作用于仍可能混入非 AI 内容的源 |

## 三、已敲定决策(Grilling 记录)

| # | 决策点 | 结论 |
|---|--------|------|
| 1 | 问题定性 | 首要问题是「GitHub 源零数据」(证书链),其次「内容非 AI」(无过滤),再次「无分页」 |
| 2 | 内容 AI 化方式 | **上游 AI 化为主 + 关键词兜底过滤**(d) |
| 3 | 非 AI 条目处置 | 爬取时**直接丢弃不入库**(a) |
| 4 | 历史脏数据 | **一次性清理**(b) |
| 5 | 分页形态 | 传统页码分页(n-pagination),默认每页 20,返回 `{items,total,page,size}`(a) |
| 6 | 分页范围 | 仅列表分页;上游不做多页抓取(a) |
| 7 | GitHub Trending 改法 | Search API:`topic:ai` + `pushed:>{T-30天}` + `sort=stars` + `per_page=20`(b) |
| 8 | GitHub/HF TLS 修复 | **代码内为这两个源的 HttpClient 构造专用 SSLContext(读 Windows 根证书库)**,不引入启动参数、不动全局信任链(a);非 Windows(如 Linux 容器)加载失败时回退系统默认 SSLContext |
| 9 | Papers with Code | 抓取目标改为 `huggingface.co/papers/trending`,**源名保留** "Papers with Code"(a) |
| 10 | 历史清理实现 | 手动接口 `POST /api/news/cleanup` + 前端按钮(可重复执行)(b) |
| 11 | 清理范围 | 通用内置源(Hacker News / InfoQ)的历史条目,按关键词判定删除(a)。**评审修正**:GitHub Trending 因决策 7 已转为 topic:ai 上游(AI 原生源),按标题关键词清理会误删 "ComfyUI" 类真实 AI 仓库,与本文档「不误删 AI 原生源」原则冲突,故移出清理范围 |
| 12 | InfoQ | **实测 `/aibriefs` 为 Nuxt 壳且数据接口对 headless 浏览器反爬(Playwright 渲染后文章链接恒为 0),经用户确认回退**:抓首页 + 关键词兜底过滤(a) |

**关键词兜底过滤的作用范围**(由决策 2/3 推导):仅应用于仍可能混入非 AI 内容的源 —— Hacker News(Algolia 前缀匹配会混入 "Airfoil" 之类)、InfoQ(频道页仍可能混入)、自建源;不应用于 AI 原生源(机器之心、Papers、两个 GitHub topic:ai 源),避免误删 "ComfyUI" 这类标题无 AI 关键词的真实 AI 内容。

## 四、后端设计

### 4.1 WindowsTrustSslContext(新工具类 `crawler` 或 `news` 包)

- 静态工厂:`KeyStore.getInstance("Windows-ROOT")` → `TrustManagerFactory` → `SSLContext`(TLS)
- 加载失败(非 Windows / 无该 keystore)返回 `SSLContext.getDefault()`,不抛异常
- 作用于三个需连 GitHub/HF 的 fetcher:`GithubTrendingNewsFetcher`(新)、`GithubStarAiNewsFetcher`、`PapersWithCodeNewsFetcher` 的 `HttpClient`(`.sslContext(...)`)

### 4.2 各 fetcher 变更

| Fetcher | 变更 | 过滤 |
|---------|------|------|
| `GithubTrendingNewsFetcher` | **重写**:GET `https://api.github.com/search/repositories?q=topic:ai+pushed:>{T-30天}&sort=stars&order=desc&per_page=20`(JDK HttpClient + WindowsTrust SSLContext);标题 = `full_name` + (description 非空时 `" — " + description` 截断 200 字);解析独立静态函数 `parseResponse(String)` 供 fixture 测试 | 否(上游 topic:ai) |
| `GithubStarAiNewsFetcher` | 加 WindowsTrust SSLContext;**修复既有 bug:查询分隔符 `+` 被整体 URL 编码为 `%2B`,GitHub 将日期解析为 "YYYY-MM-DD+topic:ai" 返回 422,该源在任何机器都拿不到数据**(改为空格分隔符,已加 buildUrl 回归测试);口径不变 | 否 |
| `HackerNewsNewsFetcher` | **重写**:GET `https://hn.algolia.com/api/v1/search?query=AI&tags=story&hitsPerPage=30`;解析 hits[]{title, url(空则 `https://news.ycombinator.com/item?id={objectID}`)};静态 `parseResponse(String)` | **是** |
| `InfoQNewsFetcher` | **回退**:继续抓首页(JDK HttpClient,SSR 有 137 条标题)+ 关键词兜底过滤;`/aibriefs` 频道页为 Nuxt 壳,数据接口对 headless 浏览器反爬(实测 Playwright 渲染后文章链接恒 0) | **是** |
| `PapersWithCodeNewsFetcher` | 目标改为 `https://huggingface.co/papers/trending`;解析 `article` 内 `a[href^="/papers/"]` 的标题(限定 article 防导航链接混入),按 URL 去重;WindowsTrust SSLContext;sourceName 保留 | 否 |
| `机器之心` | 不变 | 否 |
| `GenericHtmlNewsFetcher` | 不变 | **是** |

### 4.3 AiKeywordFilter(新类,静态纯函数 `isAiRelated(String title)`)

- 英文词边界匹配(忽略大小写):`\b(ai|llm|gpt|llama|claude|gemini|deepseek|mistral|qwen|openai|anthropic|chatgpt|copilot|transformer|diffusion|neural|machine learning|deep learning|rag|embedding|fine-?tuning|multimodal|agent|chatbot|prompt|pytorch|tensorflow|langchain|inference|vision|asr|tts)\b`(最终表以测试固化为准;`fine-?tun(ing|ed)?` 同时命中 finetune/fine-tune 两形)
- 中文子串:人工智能、大模型、机器学习、深度学习、神经网络、智能体、多模态、生成式、提示词、微调、具身智能、文生图、文生视频、语音("AI" 一词由英文 `\bai\b` 覆盖,避免大小写混排的子串误判)
- `NewsFetcher` 加 `default boolean needsAiFilter() { return false; }`;通用源(HN / InfoQ / Generic)覆写 true

### 4.4 DailyNewsService

- `runCrawlNow()`:入库前对 `needsAiFilter()` 为 true 的源逐条 `AiKeywordFilter.isAiRelated(title)` 过滤;过滤后为空记 warn 跳过(不 touch last_crawl)
- **分页**:`list(category, unreadOnly, sourceId, sort, page, size)` 用 MyBatis-Plus `Page` + `selectPage`(分页拦截器已配置),返回 `NewsPage{items, total, page, size}`(service 内 record);page≥1、size 默认 20 上限 100;sourceName 回填逻辑不变
- **清理**:`cleanupNonAi()`:查 source 名 ∈ {Hacker News, InfoQ}(`CLEANUP_SOURCES` 常量;GitHub Trending 已 AI 原生化,不列入)的 `source_id` → 拉取这些源的 title/id → Java 侧 `!isAiRelated(title)` 判删 → 批量删除,返回删除条数

### 4.5 接口

- `GET /api/news` 增参 `page`(默认 1)、`size`(默认 20):返回 `Result<NewsPage>`
- `POST /api/news/cleanup` → `Result<Long>`(删除条数,0 亦 200)

## 五、前端设计

- `types/index.ts`:`NewsPage{items,total,page,size}`;`listNews` 增 `page/size/category` 参数并返回 `NewsPage`;新增 `cleanupNonAi()`
- `stores/news.ts`:新增 `page/total/size` 状态;`filterCategory` 与 `page` 变更 `watch` → 重拉;`filtered` 移除本地 category/unread 过滤(服务端分页后本地过滤会破坏页码),直接返回 items
- `DailyNewsView.vue`:
  - 底部 `n-pagination`(总数 total、页码 page、每页 20 固定)
  - 来源管理弹窗内加「清理非 AI 历史数据」按钮(确认后调 cleanup,提示删除条数并刷新列表)
  - **爬取完成后自动刷新列表**(任务由 RUNNING → SUCCESS 时重新 load,否则修复后用户仍需手动刷新才能看到 GitHub 数据)

## 六、测试与验收

- 后端(TDD):`AiKeywordFilterTest`(词边界/中文/大小写/误伤样本 "Airfoil"/"ComfyUI" 放行)、`NewsFetcher` 各新解析函数 fixture 测试(Search API JSON / Algolia JSON / HF HTML / InfoQ HTML)、`WindowsTrustSslContextTest`(Windows 上可加载,不抛)、`DailyNewsServiceTest`(过滤丢弃、清理范围与计数、分页 page/size/total)、`DailyNewsControllerTest`(分页参数、cleanup 端点);既有 116 不回归
- 前端(Vitest):store 分页状态/load 参数/cleanup action;typecheck + build
- 真实冒烟:手动爬取一轮 → GitHub Trending(新口径)与 GitHub Star AI 有数据、Papers 有 HF 数据、HN/InfoQ 无 "Airfoil" 类非 AI 条目 → 列表翻页正常 → cleanup 后三个通用源的旧非 AI 条目被删、机器之心等源不受影响

## 七、环境说明

TLS 修复完全内置代码(Windows-ROOT),**无需改启动参数/证书**;Linux/Docker 下自动回退默认 truststore(正常网络证书链可验证)。Playwright 依赖本机浏览器(已安装 chromium),缺失时该源失败容忍。
