# AI 日报「真·AI 内容 + 分页」实施计划

**Goal:** 修复 GitHub/HF 源零数据(TLS 证书链)→ 日报内容真·AI 化(上游 AI 化 + 关键词兜底过滤 + 历史清理)→ 列表分页(前后端)。

**Architecture:** 代码内 WindowsTrust SSLContext 作用于 GitHub/HF 客户端;`NewsFetcher.needsAiFilter()` 标记需关键词过滤的源;`AiKeywordFilter` 静态纯函数入库前过滤;`GET /api/news` 走 MyBatis-Plus 分页返回 `NewsPage`;`POST /api/news/cleanup` 清理三个通用源的历史非 AI 条目;前端 n-pagination + 爬取完成自动刷新。

**规格:** `docs/superpowers/specs/2026-08-17-news-ai-content-pagination-design.md`

**约定:** 全部 TDD(先 fixture 测试后实现);运行 `cd backend && DB_PASSWORD=root mvn test -Dtest=Xxx -q`(需本地 Postgres neuralbase_test);前端 `cd frontend && pnpm vitest run`。

---

## Task 1:AiKeywordFilter(TDD)

- Create: `backend/src/main/java/com/neuralbase/news/AiKeywordFilter.java`
- Test: `backend/src/test/java/com/neuralbase/news/AiKeywordFilterTest.java`

静态方法 `isAiRelated(String title)`:英文按词边界(忽略大小写)匹配、中文按子串;null/blank 返回 false。

核心实现(正则一次编译):

```java
public final class AiKeywordFilter {
    private static final Pattern EN = Pattern.compile(
        "\\b(ai|llm|gpt|llama|claude|gemini|deepseek|mistral|qwen|openai|anthropic|chatgpt|copilot|"
        + "transformer|diffusion|neural|machine learning|deep learning|rag|embedding|fine-tun(ing|ed)?|"
        + "multimodal|agent|chatbot|prompt|pytorch|tensorflow|langchain|inference|vision|asr|tts)\\b",
        Pattern.CASE_INSENSITIVE);
    private static final List<String> ZH = List.of(
        "AI", "人工智能", "大模型", "机器学习", "深度学习", "神经网络", "智能体",
        "多模态", "生成式", "提示词", "微调", "具身智能", "文生图", "文生视频");

    public static boolean isAiRelated(String title) { ... }
    private AiKeywordFilter() {}
}
```

测试样本:放行 `AI agent 框架`、`OpenAI 发布`、`大模型评测`、`Airfoil 气动仿真`(含 ai 但非词边界→**应判非 AI**)、`ComfyUI workflow`(不放行,由"上游已 AI"保证不误删,此样本仅固化函数语义);拒绝 `Airfoil`、`维护笔记`、`如何保养汽车`。

Run: `DB_PASSWORD=root mvn test -Dtest=AiKeywordFilterTest -q`
Commit: `feat: ai keyword filter for daily news`

## Task 2:needsAiFilter + 入库过滤

- Modify: `backend/src/main/java/com/neuralbase/news/NewsFetcher.java`(default needsAiFilter)
- Modify: `HackerNewsNewsFetcher`、`InfoQNewsFetcher`、`GenericHtmlNewsFetcher`(override true)
- Modify: `backend/src/main/java/com/neuralbase/service/DailyNewsService.java`(runCrawlNow 过滤)
- Test: `backend/src/test/java/com/neuralbase/news/DailyNewsServiceTest.java`(扩展)

```java
public interface NewsFetcher {
    List<NewsItem> fetch();
    default String sourceName() { return null; }
    /** 该源上游未保证 AI 主题时返回 true,入库前按标题关键词兜底过滤。 */
    default boolean needsAiFilter() { return false; }
}
```

`runCrawlNow()` 中 `for (NewsItem item : items)` 之前:

```java
if (fetcher.needsAiFilter()) {
    items = items.stream().filter(i -> AiKeywordFilter.isAiRelated(i.title())).toList();
}
if (items.isEmpty()) { log.warn("新闻源过滤后无 AI 条目: {}", fetcher.getClass().getSimpleName()); continue; }
```

测试:`MockNewsConfig` 的 mockHnFetcher 增 `needsAiFilter()→true` 并返回一条非 AI 标题假件 → 断言非 AI 不入库、AI 入库。

Run: `DB_PASSWORD=root mvn test -Dtest=DailyNewsServiceTest -q`
Commit: `feat: drop non-ai items at crawl time for general sources`

## Task 3:WindowsTrustSslContext(TDD)

- Create: `backend/src/main/java/com/neuralbase/news/WindowsTrustSslContext.java`
- Test: `backend/src/test/java/com/neuralbase/news/WindowsTrustSslContextTest.java`

```java
public final class WindowsTrustSslContext {
    /** Windows 上读系统根证书库构造 SSLContext(绕开被安全软件破坏的默认证书链验证);失败回退系统默认。 */
    public static SSLContext create() {
        try {
            KeyStore ks = KeyStore.getInstance("Windows-ROOT");
            ks.load(null, null);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx;
        } catch (Exception e) {
            return SSLContext.getDefault(); // 非 Windows / Linux 容器
        }
    }
    private WindowsTrustSslContext() {}
}
```

测试(Windows 环境):`create()` 不抛、trustManagers 非空;Linux/CI 上仅断言不抛。

Run: `DB_PASSWORD=root mvn test -Dtest=WindowsTrustSslContextTest -q`
Commit: `feat: windows-root truststore ssl context for github/hf fetchers`

## Task 4:GithubTrendingNewsFetcher 重写(TDD)

- Modify: `backend/src/main/java/com/neuralbase/news/GithubTrendingNewsFetcher.java`
- Test: 改 `backend/src/test/java/com/neuralbase/news/NewsFetcherParseTest.java`(新 fixture JSON)

`fetch()`:

```java
String since = LocalDate.now().minusDays(30).toString();
String url = API + "?q=" + URLEncoder.encode("topic:ai pushed:>" + since, UTF_8)
    + "&sort=stars&order=desc&per_page=" + TOP_N;
HttpClient client = HttpClient.newBuilder().sslContext(WindowsTrustSslContext.create()).build();
// GET, headers: User-Agent=NeuralBase, Accept=application/vnd.github+json
```

`static List<NewsItem> parseResponse(String json)`:items[] → title = `full_name` + (`description` 非空 ? `" — " + desc 截断 200 字` : ""),url = html_url,source "GitHub Trending"。fixture 覆盖:有/无 description、缺字段跳过。

Run: `DB_PASSWORD=root mvn test -Dtest=NewsFetcherParseTest -q`
Commit: `feat: github trending via search api topic:ai (30d, stars)`

## Task 5:HackerNewsNewsFetcher 重写(TDD)

- Modify: `backend/src/main/java/com/neuralbase/news/HackerNewsNewsFetcher.java`
- Test: `backend/src/test/java/com/neuralbase/news/MoreNewsFetcherParseTest.java`(扩展)

`fetch()`:GET `https://hn.algolia.com/api/v1/search?query=AI&tags=story&hitsPerPage=30`(Jsoup connect 或 JDK HttpClient,无证书问题);`static List<NewsItem> parseResponse(String json)`:hits[] → title、url(空则 `https://news.ycombinator.com/item?id={objectID}`)。`needsAiFilter()=true`。

Run: `DB_PASSWORD=root mvn test -Dtest=MoreNewsFetcherParseTest -q`
Commit: `feat: hacker news via algolia ai query with keyword fallback filter`

## Task 6:PapersWithCodeNewsFetcher 改 HF(TDD)

- Modify: `backend/src/main/java/com/neuralbase/news/PapersWithCodeNewsFetcher.java`
- Test: `MoreNewsFetcherParseTest`(扩展)

目标 `https://huggingface.co/papers/trending`;`HttpClient.sslContext(WindowsTrustSslContext.create())`;`static parseHtml(html, baseUri)`:解析 `article` 内 `a[href^="/papers/"]`(title=text、url=absUrl);sourceName 保留 "Papers with Code"。

Run: `DB_PASSWORD=root mvn test -Dtest=MoreNewsFetcherParseTest -q`
Commit: `feat: papers source now follows huggingface papers/trending`

## Task 7:InfoQ 兜底方案(经用户确认回退)

`/aibriefs` 实测为 Nuxt 壳 + 数据接口反爬 headless(Playwright 渲染后文章链接恒 0)→ **InfoQ 保持抓首页 + `needsAiFilter()=true`**(Task 2 已完成,无额外代码)。

## Task 8:GithubStarAiNewsFetcher 加 SSLContext + 修复分隔符编码 bug

- Modify: `backend/src/main/java/com/neuralbase/news/GithubStarAiNewsFetcher.java`(HttpClient 加 `.sslContext(...)`;**query 分隔符由 `+` 改为空格** —— 原 `+` 被 URL 编码为 `%2B` 后 GitHub 返回 422 "2026-08-10+topic:ai is not a recognized date/time format",是该源零数据的第二根因;提取 `static buildUrl(LocalDate)` 供回归测试)
- Test: `GithubStarAiNewsFetcherTest.buildUrlKeepsQualifierSeparatorAsSpaceNotPlus`

## Task 9:列表分页(TDD)

- Modify: `backend/src/main/java/com/neuralbase/service/DailyNewsService.java`(list 签名 + NewsPage record + selectPage)
- Modify: `backend/src/main/java/com/neuralbase/controller/DailyNewsController.java`(page/size 参数,返回 Result<NewsPage>)
- Test: `DailyNewsServiceTest` / `DailyNewsControllerTest`(扩展)

```java
public record NewsPage(List<DailyNews> items, long total, long page, long size) {}

public NewsPage list(String category, Boolean unreadOnly, Long sourceId, String sort, long page, long size) {
    LambdaQueryWrapper<DailyNews> qw = ...; // 既有条件 + 排序不变
    long safePage = Math.max(1, page);
    long safeSize = Math.min(100, Math.max(1, size));
    Page<DailyNews> p = dailyNewsMapper.selectPage(new Page<>(safePage, safeSize), qw);
    // sourceName 回填同前
    return new NewsPage(p.getRecords(), p.getTotal(), safePage, safeSize);
}
```

测试:插入 25 条 → page=2/size=10 返回 10 条、total=25;page/size 越界回退(0 → 1,超大 → 100);筛选条件与 total 一致(如 unreadOnly=true 只数未读)。

Run: `DB_PASSWORD=root mvn test -Dtest=DailyNewsServiceTest,DailyNewsControllerTest -q`
Commit: `feat: paginated news list (page/size/total)`

## Task 10:cleanup 接口(TDD)

- Modify: `DailyNewsService.cleanupNonAi()` + `DailyNewsController` `POST /api/news/cleanup`
- Test: `DailyNewsServiceTest` / `DailyNewsControllerTest`(扩展)

```java
public long cleanupNonAi() {
    List<CrawlerSource> sources = crawlerSourceMapper.selectList(
        new LambdaQueryWrapper<CrawlerSource>().in(CrawlerSource::getName, CLEANUP_SOURCES));
    // CLEANUP_SOURCES = {Hacker News, InfoQ};GitHub Trending 已 AI 原生化(topic:ai),不列入(评审修正,防 ComfyUI 类误删)
    ...
}
```

测试:HN/InfoQ 各插 AI/非 AI 条目 + GitHub Trending 与机器之心插非 AI 标题条目 → cleanup 只删 HN/InfoQ 的非 AI、返回计数、Trending 与机器之心不动;重跑返回 0。

Run: `DB_PASSWORD=root mvn test -Dtest=DailyNewsServiceTest,DailyNewsControllerTest -q`
Commit: `feat: manual cleanup of legacy non-ai news rows`

## Task 11:前端分页 + 清理按钮 + 爬取完成刷新

- Modify: `frontend/src/types/index.ts`(`NewsPage`)
- Modify: `frontend/src/api/news.ts`(`listNews` 参数/返回、`cleanupNonAi`)
- Modify: `frontend/src/stores/news.ts`(page/total/cleanup、filtered 去本地过滤、任务 RUNNING→SUCCESS 后自动 load)
- Modify: `frontend/src/views/DailyNewsView.vue`(n-pagination、来源管理弹窗加清理按钮、分类 chips 绑 store)
- Test: `frontend/src/__tests__/` 下 news store 测试扩展(Vitest)

```ts
// stores/news.ts 核心变化
const page = ref(1); const size = 20; const total = ref(0)
watch([unreadOnly, sort, sourceFilter, filterCategory, page], () => void load())
const filtered = computed(() => items.value) // 服务端分页,本地不再过滤
async function load() {
  const r = await newsApi.listNews({ category: filterCategory.value || undefined, unreadOnly: ..., sourceId: ..., sort: sort.value, page: page.value, size })
  items.value = r.items; total.value = r.total
}
async function cleanupNonAi() { const n = await newsApi.cleanupNonAi(); await load(); return n }
// 爬取完成刷新:refreshTask 中 task 由 RUNNING 变非 RUNNING 时 void load()
```

View:n-pagination `:page="newsStore.page" :page-size="newsStore.size" :item-count="newsStore.total" @update:page="newsStore.page = $event"`(卡片列表下方);来源管理弹窗底部「清理非 AI 历史数据」按钮(NPopconfirm)→ `newsStore.cleanupNonAi()` → message 显示删除条数。

Run: `pnpm vitest run`、`pnpm typecheck`、`pnpm build`
Commit: `feat: news list pagination, cleanup button, auto-refresh after crawl`

## Task 12:全量回归 + 真实冒烟 + 文档

- 后端 `DB_PASSWORD=root mvn test`(116 + 新增全绿);前端 `pnpm vitest run` + typecheck + build
- 真实冒烟(后端跑 8081 + 前端 dev):
  1. `POST /api/news/crawl` → 任务 SUCCESS 后:GitHub Trending / GitHub Star AI / Papers 三源有数据(TLS 修复生效);HN / InfoQ 无 "Airfoil" 类非 AI 条目
  2. 列表翻页:第 2 页正常、total 正确、排序/筛选/未读切换后回第 1 页
  3. `POST /api/news/cleanup` → 三个通用源旧非 AI 条目被删、机器之心等不动;前端按钮同验
  4. 前端 CDP 巡检:分页器、清理按钮、爬取完成后列表自动刷新
- 文档:`docs/PROGRESS.md`(Phase 5 段更新)、`docs/待优化事项.md`(TLS 环境问题闭环为代码内置修复)、`docs/已完成事项.md` 补记

Commit: `docs: news ai-content pagination delivery notes`
