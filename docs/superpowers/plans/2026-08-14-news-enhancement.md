# AI 日报增强 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 日报排序/来源筛选/绝对时间/来源管理 CRUD/GitHub Star AI 新源/机器之心 Playwright 化/自建源通用抓取。

**Architecture:** `NewsFetcher.sourceName()` 标识内置源 → service 按 crawler_source.enabled 过滤;新增 GenericHtmlNewsFetcher 读库抓自建静态源;排序/筛选全走后端参数;前端 toolbar + 来源管理弹窗。

**Tech Stack:** 同 Phase 5;Playwright 复用既有依赖。

**规格:** `docs/superpowers/specs/2026-08-14-news-enhancement-design.md`

## Global Constraints

- 后端测试模式同 Phase 5;命令 `cd backend && DB_PASSWORD=root mvn test`;前端 `cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run build`
- 内置源名单常量(6 个):GitHub Trending / Hacker News / Papers with Code / 机器之心 / InfoQ / GitHub Star AI——内置可停用不可删
- 失败容忍:单源异常/无条目仅告警;GitHubStar 与机器之心真实验证受本机 TLS 环境限制,如实记录
- commit 英文 subject;逐任务提交

---

### Task 1: sourceName 标识 + 启停过滤 + 种子补源(TDD)

**Files:**
- Modify: `backend/src/main/java/com/neuralbase/news/NewsFetcher.java`(default sourceName)
- Modify: 5 个既有 fetcher + 覆写 sourceName
- Modify: `backend/src/main/java/com/neuralbase/service/DailyNewsService.java`(启停过滤)
- Modify: `backend/src/main/java/com/neuralbase/config/DataInitializer.java`(补 GitHub Star AI 种子)
- Test: `backend/src/test/java/com/neuralbase/news/DailyNewsServiceTest.java`(扩展)

**Interfaces:**
- Produces: `NewsFetcher.sourceName(): String|null`;service 过滤语义——Task 4/5 消费

- [ ] **Step 1: 写失败测试**

`DailyNewsServiceTest` 加(需注入 `CrawlerSourceMapper` 与 `JdbcTemplate` 已有):

```java
    @Test
    void disabledSourceIsSkipped() {
        // 停用两个假件的源名后,爬取结果为 0 条
        jdbcTemplate.update("UPDATE crawler_source SET enabled = FALSE WHERE name = 'GitHub Trending'");
        jdbcTemplate.update("UPDATE crawler_source SET enabled = FALSE WHERE name = 'Hacker News'");
        service.startCrawl();
        service.runCrawlNow();
        assertThat(dailyNewsMapper.selectList(null)).isEmpty();
    }
```

注意:MockNewsConfig 的假件 sourceName 分别应为 "GitHub Trending"/"Hacker News"——测试通过后恢复 enabled(用 try/finally 或 @AfterEach 重置)

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=DailyNewsServiceTest -q`
Expected: FAIL — 假件无 sourceName,过滤不生效(爬取仍有 3 条)

- [ ] **Step 3: 写实现**

`NewsFetcher.java`:

```java
package com.neuralbase.news;

import java.util.List;

public interface NewsFetcher {
    List<NewsItem> fetch();

    /** 内置源标识(与 crawler_source.name 对应);Generic 抓取器返回 null。 */
    default String sourceName() {
        return null;
    }
}
```

各内置 fetcher 加(以 GitHub 为例,其余同型):

```java
    @Override
    public String sourceName() {
        return "GitHub Trending";
    }
```

(Hacker News / Papers with Code / 机器之心 / InfoQ / 后续 GitHub Star AI 对应各自名字)

`MockNewsConfig` 两个假件同样覆写 sourceName("GitHub Trending"/"Hacker News")。

`DailyNewsService.runCrawlNow()` 的遍历改为:

```java
            for (NewsFetcher fetcher : fetchers) {
                if (fetcher.sourceName() != null && !sourceEnabled(fetcher.sourceName())) {
                    continue; // 停用的内置源跳过
                }
                List<NewsItem> items;
                ...原逻辑...
            }
```

新增私有方法:

```java
    private boolean sourceEnabled(String name) {
        CrawlerSource s = crawlerSourceMapper.selectOne(
            new LambdaQueryWrapper<CrawlerSource>().eq(CrawlerSource::getName, name));
        return s == null || Boolean.TRUE.equals(s.getEnabled());
    }
```

`DataInitializer.seedNewsSources()` 加一行:

```java
        seedSource("GitHub Star AI", "https://api.github.com/search/repositories", "api");
```

- [ ] **Step 4: 运行确认通过 + 测试隔离**

测试类加恢复(在 `cleanUp()` 中):

```java
        jdbcTemplate.update("UPDATE crawler_source SET enabled = TRUE");
```

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=DailyNewsServiceTest -q`
Expected: PASS(新增 1 例)

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/neuralbase/news/ backend/src/main/java/com/neuralbase/service/DailyNewsService.java backend/src/main/java/com/neuralbase/config/DataInitializer.java backend/src/test/java/com/neuralbase/config/MockNewsConfig.java backend/src/test/java/com/neuralbase/news/DailyNewsServiceTest.java
git commit -m "feat: source identity on fetchers with enabled-based skip in crawl"
```

---

### Task 2: sort/sourceId 参数(TDD)

**Files:**
- Modify: `backend/src/main/java/com/neuralbase/service/DailyNewsService.java`(list 签名)
- Modify: `backend/src/main/java/com/neuralbase/controller/DailyNewsController.java`
- Test: `backend/src/test/java/com/neuralbase/news/DailyNewsControllerTest.java`(扩展)

**Interfaces:**
- Produces: `list(category, unreadOnly, sourceId, sort)` — Task 6 前端消费;sort ∈ created_desc/created_asc/source/category

- [ ] **Step 1: 写失败测试**

`DailyNewsControllerTest` 加:

```java
    @Test
    void listSupportsSortAndSourceFilter() throws Exception {
        String tk = token();
        service.startCrawl();
        service.runCrawlNow();

        mvc.perform(get("/api/news").header("Authorization", "Bearer " + tk)
                .param("sort", "source"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(3));

        mvc.perform(get("/api/news").header("Authorization", "Bearer " + tk)
                .param("sort", "bogus"))
            .andExpect(status().isOk()); // 非法 sort 回退默认,不 400

        Long githubId = jdbcTemplate.queryForObject(
            "SELECT id FROM crawler_source WHERE name = 'GitHub Trending'", Long.class);
        mvc.perform(get("/api/news").header("Authorization", "Bearer " + tk)
                .param("sourceId", String.valueOf(githubId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2)); // 假件 GitHub 源 2 条
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=DailyNewsControllerTest -q`
Expected: FAIL — sourceId 过滤不生效(返回 3 条)

- [ ] **Step 3: 写实现**

`DailyNewsService.list`:

```java
    public List<DailyNews> list(String category, Boolean unreadOnly, Long sourceId, String sort) {
        LambdaQueryWrapper<DailyNews> qw = new LambdaQueryWrapper<DailyNews>()
            .eq(category != null && !category.isBlank(), DailyNews::getCategory, category)
            .eq(Boolean.TRUE.equals(unreadOnly), DailyNews::getIsRead, false)
            .eq(sourceId != null, DailyNews::getSourceId, sourceId);
        switch (sort == null ? "created_desc" : sort) {
            case "created_asc" -> qw.orderByAsc(DailyNews::getCreatedAt);
            case "source" -> qw.orderByAsc(DailyNews::getSourceId);
            case "category" -> qw.orderByAsc(DailyNews::getCategory);
            default -> qw.orderByDesc(DailyNews::getCreatedAt); // 含非法值回退
        }
        List<DailyNews> news = dailyNewsMapper.selectList(qw.last("LIMIT 200"));
        ...(来源名填充不变)...
    }
```

`DailyNewsController.list` 加参数与透传:

```java
    @GetMapping
    public Result<List<DailyNews>> list(@RequestParam(required = false) String category,
                                        @RequestParam(required = false) Boolean unreadOnly,
                                        @RequestParam(required = false) Long sourceId,
                                        @RequestParam(required = false) String sort) {
        return Result.ok(dailyNewsService.list(category, unreadOnly, sourceId, sort));
    }
```

(所有既有调用点 `list(a, b)` → `list(a, b, null, null)` 同步)

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=DailyNewsControllerTest,DailyNewsServiceTest -q`
Expected: 全 PASS

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/neuralbase/service/DailyNewsService.java backend/src/main/java/com/neuralbase/controller/DailyNewsController.java backend/src/test/java/com/neuralbase/news/DailyNewsControllerTest.java backend/src/test/java/com/neuralbase/news/DailyNewsServiceTest.java
git commit -m "feat: news list sort and source filter parameters"
```

---

### Task 3: GithubStarAiNewsFetcher(TDD)

**Files:**
- Create: `backend/src/main/java/com/neuralbase/news/GithubStarAiNewsFetcher.java`
- Test: `backend/src/test/java/com/neuralbase/news/GithubStarAiNewsFetcherTest.java`

**Interfaces:**
- Produces: `GithubStarAiNewsFetcher.parseResponse(String json)` 静态解析;sourceName="GitHub Star AI" — Task 1 已种子

- [ ] **Step 1: 写失败测试**

新建测试:

```java
package com.neuralbase.news;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GithubStarAiNewsFetcherTest {

    @Test
    void parsesSearchResponse() {
        String json = """
            {"total_count":2,"items":[
              {"full_name":"org/repo1","html_url":"https://github.com/org/repo1","stargazers_count":1234},
              {"full_name":"org/repo2","html_url":"https://github.com/org/repo2","stargazers_count":567}
            ]}
            """;
        var items = GithubStarAiNewsFetcher.parseResponse(json);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("org/repo1");
        assertThat(items.get(0).url()).isEqualTo("https://github.com/org/repo1");
        assertThat(items.get(0).sourceName()).isEqualTo("GitHub Star AI");
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=GithubStarAiNewsFetcherTest -q`
Expected: 编译失败

- [ ] **Step 3: 写实现**

```java
package com.neuralbase.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** GitHub Search API:近 7 天新建的 AI 主题仓库,按 stars 排序 top 20(「star 新增量」的可实现口径)。 */
@Component
@Profile("!test")
public class GithubStarAiNewsFetcher implements NewsFetcher {

    private static final String API = "https://api.github.com/search/repositories";
    private static final int TOP_N = 20;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<NewsItem> fetch() {
        try {
            String since = LocalDate.now().minusDays(7).toString();
            String query = "created:>" + since + "+topic:ai";
            String url = API + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&sort=stars&order=desc&per_page=" + TOP_N;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "NeuralBase")
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
            String json = HttpClient.newBuilder().build()
                .send(request, HttpResponse.BodyHandlers.ofString()).body();
            return parseResponse(json);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("GitHub Star AI 爬取失败", e);
        }
    }

    @Override
    public String sourceName() {
        return "GitHub Star AI";
    }

    static List<NewsItem> parseResponse(String json) {
        List<NewsItem> items = new ArrayList<>();
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            for (JsonNode item : root.path("items")) {
                String name = item.path("full_name").asText(null);
                String url = item.path("html_url").asText(null);
                if (name == null || url == null) continue;
                items.add(new NewsItem(name, url, "GitHub Star AI"));
            }
        } catch (Exception ignored) {
            // 解析失败返回空,由上层告警
        }
        return items;
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=GithubStarAiNewsFetcherTest -q`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/neuralbase/news/GithubStarAiNewsFetcher.java backend/src/test/java/com/neuralbase/news/GithubStarAiNewsFetcherTest.java
git commit -m "feat: github star ai news fetcher (7-day new ai repos by stars)"
```

---

### Task 4: 机器之心 Playwright 化 + GenericHtmlNewsFetcher(TDD)

**Files:**
- Modify: `backend/src/main/java/com/neuralbase/news/JiQiZhiXinNewsFetcher.java`(Playwright 渲染)
- Create: `backend/src/main/java/com/neuralbase/news/GenericHtmlNewsFetcher.java`
- Test: `backend/src/test/java/com/neuralbase/news/GenericHtmlNewsFetcherTest.java`

**Interfaces:**
- Produces: `GenericHtmlNewsFetcher.parseHtml(html, baseUri, selector)` 静态解析;sourceName()=null(service 恒执行,内部过滤 enabled + 非内置)

- [ ] **Step 1: 写失败测试(Generic 解析)**

```java
package com.neuralbase.news;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenericHtmlNewsFetcherTest {

    @Test
    void parsesLinksBySelector() {
        String html = """
            <html><body>
            <ul class="news-list"><li><a href="/a/1">标题一</a></li><li><a href="/a/2">标题二</a></li></ul>
            </body></html>
            """;
        var items = GenericHtmlNewsFetcher.parseHtml(html, "https://example.com", "ul.news-list a");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).url()).isEqualTo("https://example.com/a/1");
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=GenericHtmlNewsFetcherTest -q`
Expected: 编译失败

- [ ] **Step 3: 写实现**

`JiQiZhiXinNewsFetcher.fetch()` 替换为 Playwright 渲染(parseHtml 与 sourceName 不变):

```java
    @Override
    public List<NewsItem> fetch() {
        try (Playwright pw = Playwright.create();
             Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();
            page.setDefaultTimeout(45_000);
            page.navigate(ARTICLES_URL);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            return parseHtml(page.content(), ARTICLES_URL);
        } catch (Exception e) {
            throw new RuntimeException("机器之心渲染失败: " + e.getMessage()
                + "（若未安装浏览器，请先执行 playwright install chromium）", e);
        }
    }
```

(import 补 com.microsoft.playwright.*;原 Jsoup connect 版 fetch 移除)

`GenericHtmlNewsFetcher`:

```java
package com.neuralbase.news;

import com.neuralbase.entity.CrawlerSource;
import com.neuralbase.mapper.CrawlerSourceMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** 用户自建静态源:读 crawler_source(enabled、type=html、非内置名单),按 selector 提取链接列表。 */
@Component
public class GenericHtmlNewsFetcher implements NewsFetcher {

    private static final List<String> BUILTIN = List.of(
        "GitHub Trending", "Hacker News", "Papers with Code", "机器之心", "InfoQ", "GitHub Star AI");

    private final CrawlerSourceMapper crawlerSourceMapper;

    public GenericHtmlNewsFetcher(CrawlerSourceMapper crawlerSourceMapper) {
        this.crawlerSourceMapper = crawlerSourceMapper;
    }

    @Override
    public List<NewsItem> fetch() {
        List<NewsItem> items = new ArrayList<>();
        for (CrawlerSource s : crawlerSourceMapper.selectList(null)) {
            if (!Boolean.TRUE.equals(s.getEnabled()) || s.getType() == null
                || !s.getType().equals("html") || BUILTIN.contains(s.getName())) {
                continue;
            }
            if (s.getSelector() == null || s.getSelector().isBlank()) {
                continue;
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(s.getUrl()))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
                String html = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString())
                    .body();
                List<NewsItem> found = parseHtml(html, s.getUrl(), s.getSelector());
                for (NewsItem item : found) {
                    items.add(new NewsItem(item.title(), item.url(), s.getName()));
                }
            } catch (Exception e) {
                // 单源失败跳过,由 service 汇总日志
            }
        }
        return items;
    }

    static List<NewsItem> parseHtml(String html, String baseUri, String selector) {
        Document doc = Jsoup.parse(html, baseUri);
        List<NewsItem> items = new ArrayList<>();
        for (var a : doc.select(selector)) {
            String title = a.text().strip();
            if (title.isEmpty()) continue;
            items.add(new NewsItem(title, a.absUrl("href"), null));
        }
        return items;
    }
}
```

注意:Generic 在 test profile 也注册(组件扫描)——DailyNewsServiceTest 的假件 @Primary 注入的是 List<NewsFetcher>:Generic 也在列表里!它会读测试库 crawler_source——测试里种子源存在但 BUILTIN 排除 + 无 selector → 返回空 ✓ 无碍。

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=GenericHtmlNewsFetcherTest,DailyNewsServiceTest -q`
Expected: 全 PASS(Generic 空结果不影响既有断言:3 条仍来自假件)

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/neuralbase/news/JiQiZhiXinNewsFetcher.java backend/src/main/java/com/neuralbase/news/GenericHtmlNewsFetcher.java backend/src/test/java/com/neuralbase/news/GenericHtmlNewsFetcherTest.java
git commit -m "feat: playwright rendering for jiqizhixin, generic html fetcher for user sources"
```

---

### Task 5: 来源 CRUD 接口(TDD)

**Files:**
- Modify: `backend/src/main/java/com/neuralbase/controller/DailyNewsController.java`(source 端点)
- Modify: `backend/src/main/java/com/neuralbase/service/DailyNewsService.java`(source 方法)
- Test: `backend/src/test/java/com/neuralbase/news/DailyNewsControllerTest.java`(扩展)

**Interfaces:**
- Produces: `GET/POST /api/news/source`、`PUT/DELETE /api/news/source/{id}` — Task 6 前端消费

- [ ] **Step 1: 写失败测试**

`DailyNewsControllerTest` 加:

```java
    @Test
    void sourceCrudRespectsBuiltinProtection() throws Exception {
        String tk = token();

        mvc.perform(post("/api/news/source")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"我的技术站\",\"url\":\"https://example.com\",\"selector\":\"ul.news a\",\"enabled\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").isNumber());

        long builtinId = jdbcTemplate.queryForObject(
            "SELECT id FROM crawler_source WHERE name = 'InfoQ'", Long.class);
        mvc.perform(delete("/api/news/source/" + builtinId)
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("内置")));

        mvc.perform(get("/api/news/source").header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(7)); // 6 内置 + 1 自建
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=DailyNewsControllerTest -q`
Expected: FAIL — 404

- [ ] **Step 3: 写实现**

`DailyNewsService` 加:

```java
    private static final List<String> BUILTIN_SOURCES = List.of(
        "GitHub Trending", "Hacker News", "Papers with Code", "机器之心", "InfoQ", "GitHub Star AI");

    public List<CrawlerSource> sources() {
        return crawlerSourceMapper.selectList(
            new LambdaQueryWrapper<CrawlerSource>().orderByAsc(CrawlerSource::getId));
    }

    public CrawlerSource createSource(String name, String url, String selector, Boolean enabled) {
        if (name == null || name.isBlank() || url == null || url.isBlank()) {
            throw new IllegalArgumentException("名称与 URL 不能为空");
        }
        if (crawlerSourceMapper.selectCount(
                new LambdaQueryWrapper<CrawlerSource>().eq(CrawlerSource::getName, name)) > 0) {
            throw new IllegalArgumentException("同名来源已存在: " + name);
        }
        CrawlerSource s = new CrawlerSource();
        s.setName(name);
        s.setUrl(url);
        s.setType("html");
        s.setSelector(selector);
        s.setEnabled(enabled == null || enabled);
        crawlerSourceMapper.insert(s);
        return s;
    }

    public CrawlerSource updateSource(Long id, String name, String url, String selector, Boolean enabled) {
        CrawlerSource s = crawlerSourceMapper.selectById(id);
        if (s == null) throw new IllegalArgumentException("来源不存在: " + id);
        if (name != null && !name.isBlank() && !name.equals(s.getName())) {
            s.setName(name);
        }
        if (url != null && !url.isBlank()) s.setUrl(url);
        if (selector != null) s.setSelector(selector);
        if (enabled != null) s.setEnabled(enabled);
        crawlerSourceMapper.updateById(s);
        return s;
    }

    public void deleteSource(Long id) {
        CrawlerSource s = crawlerSourceMapper.selectById(id);
        if (s == null) throw new IllegalArgumentException("来源不存在: " + id);
        if (BUILTIN_SOURCES.contains(s.getName())) {
            throw new IllegalArgumentException("内置来源不可删除,可停用: " + s.getName());
        }
        crawlerSourceMapper.deleteById(id);
    }
```

`DailyNewsController` 加端点:

```java
    @GetMapping("/source")
    public Result<List<CrawlerSource>> sources() {
        return Result.ok(dailyNewsService.sources());
    }

    @PostMapping("/source")
    public Result<CrawlerSource> createSource(@RequestBody SourceRequest req) {
        return Result.ok(dailyNewsService.createSource(
            req == null ? null : req.name(), req == null ? null : req.url(),
            req == null ? null : req.selector(), req == null ? null : req.enabled()));
    }

    @PutMapping("/source/{id}")
    public Result<CrawlerSource> updateSource(@PathVariable Long id, @RequestBody SourceRequest req) {
        return Result.ok(dailyNewsService.updateSource(id,
            req == null ? null : req.name(), req == null ? null : req.url(),
            req == null ? null : req.selector(), req == null ? null : req.enabled()));
    }

    @DeleteMapping("/source/{id}")
    public Result<Void> deleteSource(@PathVariable Long id) {
        dailyNewsService.deleteSource(id);
        return Result.ok(null);
    }

    public record SourceRequest(String name, String url, String selector, Boolean enabled) {
    }
```

(import CrawlerSource)

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=DailyNewsControllerTest -q`
Expected: 全 PASS(注意测试 count 断言与测试库既有行——cleanUp 只清 news/crawl_task 不清 crawler_source;断言 7 = 6 种子 + 1 新建 ✓ 幂等)

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/neuralbase/controller/DailyNewsController.java backend/src/main/java/com/neuralbase/service/DailyNewsService.java backend/src/test/java/com/neuralbase/news/DailyNewsControllerTest.java
git commit -m "feat: news source crud endpoints with builtin protection"
```

---

### Task 6: 前端 api/store/types(TDD)

**Files:**
- Modify: `frontend/src/types/index.ts`、`frontend/src/api/news.ts`、`frontend/src/stores/news.ts`
- Test: `frontend/src/stores/__tests__/news.test.ts`(扩展)

**Interfaces:**
- Consumes: Task 2/5 接口
- Produces: store `sort`/`sourceFilter`/`sources`/CRUD 动作 — Task 7 消费

- [ ] **Step 1: 写失败测试**

`news.test.ts` 加:

```ts
  it('sort 与 sourceFilter 变化触发带参 load', async () => {
    vi.mocked(listNews).mockResolvedValue([item])
    const store = useNewsStore()
    await store.load()
    store.sort = 'source'
    await vi.advanceTimersByTimeAsync(0)
    expect(listNews).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'source' }))
    store.sourceFilter = 1
    await vi.advanceTimersByTimeAsync(0)
    expect(listNews).toHaveBeenLastCalledWith(expect.objectContaining({ sourceId: 1 }))
  })

  it('来源 CRUD 调用接口', async () => {
    vi.mocked(listSources).mockResolvedValue([{ id: 1, name: 'InfoQ', url: 'https://x', type: 'html', selector: null, enabled: true }])
    vi.mocked(createSource).mockResolvedValue({ id: 9, name: '新源', url: 'https://y', type: 'html', selector: 'ul a', enabled: true })
    const store = useNewsStore()
    await store.loadSources()
    expect(store.sources).toHaveLength(1)
    await store.createSource({ name: '新源', url: 'https://y', selector: 'ul a' })
    expect(createSource).toHaveBeenCalled()
  })
```

(api mock 补 `listSources/createSource/updateSource/deleteSource`)

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/stores/__tests__/news.test.ts`
Expected: FAIL

- [ ] **Step 3: 写实现**

`types/index.ts` 加:

```ts
export type NewsSort = 'created_desc' | 'created_asc' | 'source' | 'category'

export interface NewsSource {
  id: number
  name: string
  url: string
  type: string
  selector?: string
  enabled: boolean
}
```

`api/news.ts` 扩展:

```ts
export const listNews = (params?: { category?: string; unreadOnly?: boolean; sourceId?: number; sort?: NewsSort }) =>
  http.get<NewsItem[]>('/news', params)

export const listSources = () => http.get<NewsSource[]>('/news/source')

export const createSource = (payload: { name: string; url: string; selector?: string; enabled?: boolean }) =>
  http.post<NewsSource>('/news/source', payload)

export const updateSource = (id: number, payload: { name?: string; url?: string; selector?: string; enabled?: boolean }) =>
  http.put<NewsSource>(`/news/source/${id}`, payload)

export const deleteSource = (id: number) => http.del<null>(`/news/source/${id}`)
```

`stores/news.ts`:

```ts
  const sort = ref<NewsSort>('created_desc')
  const sourceFilter = ref<number | null>(null)
  const sources = ref<NewsSource[]>([])

  async function load() {
    items.value = await newsApi.listNews({
      unreadOnly: unreadOnly.value || undefined,
      sourceId: sourceFilter.value ?? undefined,
      sort: sort.value,
    })
  }

  watch([sort, sourceFilter], () => void load())

  async function loadSources() {
    sources.value = await newsApi.listSources()
  }

  async function createSource(payload: { name: string; url: string; selector?: string }) {
    await newsApi.createSource(payload)
    await loadSources()
  }

  async function updateSource(id: number, payload: { name?: string; url?: string; selector?: string; enabled?: boolean }) {
    await newsApi.updateSource(id, payload)
    await loadSources()
  }

  async function removeSource(id: number) {
    await newsApi.deleteSource(id)
    await loadSources()
  }
```

(return 块补 `sort, sourceFilter, sources, loadSources, createSource, updateSource, removeSource`;imports 补 NewsSort/NewsSource 类型)

- [ ] **Step 4: 运行确认通过**

Run: `cd frontend && npx vitest run src/stores/__tests__/news.test.ts && npx vue-tsc --noEmit`
Expected: 全 PASS + typecheck 无输出

- [ ] **Step 5: 提交**

```bash
git add frontend/src/types/index.ts frontend/src/api/news.ts frontend/src/stores/news.ts frontend/src/stores/__tests__/news.test.ts
git commit -m "feat: news store sort/source filter and source crud"
```

---

### Task 7: DailyNewsView 工具栏与来源管理弹窗 + 时间显示

**Files:**
- Modify: `frontend/src/views/DailyNewsView.vue`

**Interfaces:**
- Consumes: Task 6 store

- [ ] **Step 1: 实现**

script 加:

```ts
import { NInput, NModal, NSelect, NSwitch, NTag } from 'naive-ui'  // NInput/NModal 并入既有 import

const sortOptions = [
  { label: '最新', value: 'created_desc' },
  { label: '最早', value: 'created_asc' },
  { label: '按来源', value: 'source' },
  { label: '按分类', value: 'category' },
] as const

const sourceModal = ref(false)
const sourceForm = ref({ name: '', url: '', selector: '' })
const savingSource = ref(false)

function fmtTime(iso: string): string {
  const d = new Date(iso)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

async function onCreateSource() {
  if (!sourceForm.value.name.trim() || !sourceForm.value.url.trim()) {
    message.warning('请填写名称与 URL')
    return
  }
  savingSource.value = true
  try {
    await newsStore.createSource({
      name: sourceForm.value.name.trim(),
      url: sourceForm.value.url.trim(),
      selector: sourceForm.value.selector.trim() || undefined,
    })
    sourceForm.value = { name: '', url: '', selector: '' }
    message.success('来源已添加,下次爬取生效')
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    savingSource.value = false
  }
}
```

onMounted 加 `await newsStore.loadSources()`。

template 工具栏(在「仅未读」开关行内)加:

```html
      <n-select v-model:value="newsStore.sort" :options="sortOptions" size="small" class="w-28" />
      <div class="flex flex-wrap gap-1.5">
        <n-tag size="small" :bordered="false" :type="newsStore.sourceFilter === null ? 'warning' : 'default'"
               class="cursor-pointer" @click="newsStore.sourceFilter = null">全部</n-tag>
        <n-tag v-for="s in newsStore.sources" :key="s.id" size="small" :bordered="false"
               :type="newsStore.sourceFilter === s.id ? 'warning' : 'default'" class="cursor-pointer"
               @click="newsStore.sourceFilter = newsStore.sourceFilter === s.id ? null : s.id">
          {{ s.name }}
        </n-tag>
      </div>
      <n-button size="small" quaternary @click="sourceModal = true">来源管理</n-button>
```

卡片时间行加(在摘要 p 之后):

```html
        <div class="mt-1 text-[11px] text-gray-400 dark:text-gray-600">
          {{ fmtTime(n.createdAt) }}<template v-if="n.sourceName"> · {{ n.sourceName }}</template>
        </div>
```

(替换原有 sourceName 单独 div)

弹窗:

```html
    <n-modal v-model:show="sourceModal" preset="dialog" title="来源管理" positive-text="关闭" @positive-click="sourceModal = false">
      <div class="space-y-3 py-2">
        <div class="space-y-1.5">
          <div v-for="s in newsStore.sources" :key="s.id" class="flex items-center justify-between gap-2 rounded-lg border border-gray-100 px-3 py-2 dark:border-white/5">
            <div class="min-w-0">
              <div class="truncate text-sm text-gray-800 dark:text-gray-200">{{ s.name }}</div>
              <div class="truncate text-xs text-gray-400 dark:text-gray-600">{{ s.url }}</div>
            </div>
            <div class="flex shrink-0 items-center gap-2">
              <n-switch size="small" :value="s.enabled" @update:value="(v: boolean) => newsStore.updateSource(s.id, { enabled: v })" />
              <button v-if="!['GitHub Trending','Hacker News','Papers with Code','机器之心','InfoQ','GitHub Star AI'].includes(s.name)"
                      class="btn-press text-gray-400 hover:text-red-400 dark:text-gray-600" @click="newsStore.removeSource(s.id)">
                <Icon icon="mdi:trash-can-outline" class="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
        <div class="border-t border-gray-100 pt-3 dark:border-white/5">
          <div class="mb-2 text-xs text-gray-500">新增静态来源(仅支持无 JS 渲染的列表页,需填写链接选择器)</div>
          <div class="space-y-2">
            <n-input v-model:value="sourceForm.name" size="small" placeholder="名称" />
            <n-input v-model:value="sourceForm.url" size="small" placeholder="URL" />
            <n-input v-model:value="sourceForm.selector" size="small" placeholder="链接选择器,如 ul.news-list a" />
            <n-button size="small" type="primary" :loading="savingSource" @click="onCreateSource">添加来源</n-button>
          </div>
        </div>
      </div>
    </n-modal>
```

- [ ] **Step 2: 验证与提交**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run build`
Expected: 全绿 + typecheck 无输出 + build 成功

```bash
git add frontend/src/views/DailyNewsView.vue
git commit -m "feat: news toolbar sort/source chips/absolute time and source management modal"
```

---

### Task 8: 全量回归 + 冒烟 + 文档

- [ ] **Step 1: 双端全量**

Run: `cd backend && DB_PASSWORD=root mvn test -q`(预期 110 + 新增 ≈ 118 绿)
Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run build`(预期 67 + 新增 ≈ 70 绿)

- [ ] **Step 2: 真实冒烟**

1. 重启后端 → 手动爬取 → 校验:`GET /api/news?sort=source&sourceId=<InfoQ id>` 返回仅 InfoQ 按来源序
2. 来源管理:POST 自建源(用 InfoQ 页面,selector `a.com-article-title`)→ 爬取一轮 → 新源条目入库(Generic 链路真实验证)
3. CDP:日报页排序下拉切换、来源 chips 筛选、时间显示、来源管理弹窗增删改停
4. GitHubStar/机器之心:本机 TLS/浏览器受限,如实记录

- [ ] **Step 3: PROGRESS 更新并提交**

```bash
git add docs/PROGRESS.md
git commit -m "docs: record news enhancement completion"
```

---

## Self-Review 记录

- **规格覆盖**:§2.1 → Task 2;§2.2 → Task 3/4;§2.3 → Task 1;§2.4 → Task 5;§三 → Task 6/7;§四 → Task 8
- **占位符**:无 TBD;内置名单常量在 service 与前端弹窗各一份(前端用于隐藏删除钮,后端为权威校验)
- **类型一致**:`NewsSort` 四值与后端 sort 参数一致;`sourceName()` 接口在 Task 1 定义、Task 3/4 覆写;`GenericHtmlNewsFetcher.parseHtml(html, baseUri, selector)` 签名全局一致
