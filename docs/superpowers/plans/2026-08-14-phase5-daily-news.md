# Phase 5 AI 日报 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AI 日报全链路:GitHub Trending + HN 两源定时/手动爬取、DeepSeek 单篇摘要分类、时间线前端(收藏/已读)。

**Architecture:** `NewsFetcher` 接口 + 两实现(解析函数独立可离线测试);`DailyNewsService` 异步任务状态机(crawl_task 表)+ URL 去重 + AI 汇总失败容忍;`@Scheduled` cron(不引入 Quartz);前端 stores/news + DailyNewsView 重写(仿 document store 轮询模式)。

**Tech Stack:** Spring Boot 3.5.16 + Jsoup + Spring AI(既有 ChatModel);Vue 3.5 + Pinia + Naive UI;vitest;CDP。

**规格:** `docs/superpowers/specs/2026-08-14-phase5-daily-news-design.md`

## Global Constraints

- 不新增依赖(Quartz 不引入,用 `@Scheduled`);后端测试 `@SpringBootTest @ActiveProfiles("test")`,假 ChatModel 用 `MockChatConfig`,假 fetcher 用 `@Profile("!test")` + 测试 `@Primary`(Phase 4 模式);命令 `cd backend && DB_PASSWORD=root mvn test`
- 业务错误抛 `IllegalArgumentException`(全局 400);AI 单篇失败仍入库(summary null);分类中文枚举:大模型/框架/工具/论文/其他
- 前端命令 `cd frontend && npx vitest run` 等;UI 亮暗两态按既有映射表;commit 英文 subject,逐任务提交

---

### Task 1: fetcher 基础设施 + 种子源 + 调度开关

**Files:**
- Create: `backend/src/main/java/com/neuralbase/news/NewsItem.java`、`backend/src/main/java/com/neuralbase/news/NewsFetcher.java`、`backend/src/main/java/com/neuralbase/news/GithubTrendingNewsFetcher.java`、`backend/src/main/java/com/neuralbase/news/HackerNewsNewsFetcher.java`
- Create: `backend/src/main/java/com/neuralbase/entity/CrawlerSource.java`、`backend/src/main/java/com/neuralbase/mapper/CrawlerSourceMapper.java`
- Modify: `backend/src/main/java/com/neuralbase/config/DataInitializer.java`(种子源)
- Modify: `backend/src/main/java/com/neuralbase/NeuralBaseApplication.java`(`@EnableScheduling`)
- Test: `backend/src/test/java/com/neuralbase/news/NewsFetcherParseTest.java`、`backend/src/test/java/com/neuralbase/config/MockNewsConfig.java`

**Interfaces:**
- Produces: `NewsItem(String title, String url, String sourceName)`;`NewsFetcher.fetch(): List<NewsItem>`;静态解析函数 `GithubTrendingNewsFetcher.parseHtml(String html)`、`HackerNewsNewsFetcher.parseItemJson(String json)`(可离线测试)— Task 3 消费

- [ ] **Step 1: 写失败测试(解析纯函数)**

新建 `backend/src/test/java/com/neuralbase/news/NewsFetcherParseTest.java`:

```java
package com.neuralbase.news;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsFetcherParseTest {

    @Test
    void githubTrendingParsesRows() {
        String html = """
            <html><body>
            <article class="Box-row"><h2><a href="/owner/repo">repo-name</a></h2><p>  A cool description </p></article>
            <article class="Box-row"><h2><a href="/o2/r2">repo2</a></h2><p>desc2</p></article>
            </body></html>
            """;
        List<NewsItem> items = GithubTrendingNewsFetcher.parseHtml(html);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("repo-name");
        assertThat(items.get(0).url()).isEqualTo("https://github.com/owner/repo");
    }

    @Test
    void hackerNewsParsesItem() {
        String json = "{\"id\":1,\"title\":\"Show HN: Foo\",\"url\":\"https://foo.example\"}";
        NewsItem item = HackerNewsNewsFetcher.parseItemJson(json, 1L);
        assertThat(item.title()).isEqualTo("Show HN: Foo");
        assertThat(item.url()).isEqualTo("https://foo.example");
    }

    @Test
    void hackerNewsFallsBackToCommentsUrl() {
        String json = "{\"id\":7,\"title\":\"Ask HN: Bar\"}";
        NewsItem item = HackerNewsNewsFetcher.parseItemJson(json, 7L);
        assertThat(item.url()).isEqualTo("https://news.ycombinator.com/item?id=7");
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=NewsFetcherParseTest -q`
Expected: 编译失败(类不存在)

- [ ] **Step 3: 写实现**

新建 `backend/src/main/java/com/neuralbase/news/NewsItem.java`:

```java
package com.neuralbase.news;

public record NewsItem(String title, String url, String sourceName) {
}
```

新建 `backend/src/main/java/com/neuralbase/news/NewsFetcher.java`:

```java
package com.neuralbase.news;

import java.util.List;

public interface NewsFetcher {
    List<NewsItem> fetch();
}
```

新建 `backend/src/main/java/com/neuralbase/news/GithubTrendingNewsFetcher.java`:

```java
package com.neuralbase.news;

import org.jsoup.Jsoup;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** GitHub Trending 列表页爬取;解析逻辑独立成静态函数以便离线测试。 */
@Component
@Profile("!test")
public class GithubTrendingNewsFetcher implements NewsFetcher {

    private static final String TRENDING_URL = "https://github.com/trending";

    @Override
    public List<NewsItem> fetch() {
        try {
            return parseHtml(Jsoup.connect(TRENDING_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15_000)
                .get().html());
        } catch (IOException e) {
            throw new RuntimeException("GitHub Trending 爬取失败", e);
        }
    }

    static List<NewsItem> parseHtml(String html) {
        var doc = Jsoup.parse(html);
        List<NewsItem> items = new ArrayList<>();
        for (var row : doc.select("article.Box-row")) {
            var a = row.selectFirst("h2 a");
            if (a == null) continue;
            String href = a.attr("href");
            String title = a.text().replaceAll("\\s+", " ").strip();
            if (title.isEmpty() || !href.startsWith("/")) continue;
            items.add(new NewsItem(title, "https://github.com" + href, "GitHub Trending"));
        }
        return items;
    }
}
```

新建 `backend/src/main/java/com/neuralbase/news/HackerNewsNewsFetcher.java`:

```java
package com.neuralbase.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Hacker News 官方 API:topstories → 逐条 item(标题/链接)。 */
@Component
@Profile("!test")
public class HackerNewsNewsFetcher implements NewsFetcher {

    private static final String API_BASE = "https://hacker-news.firebaseio.com/v0/";
    private static final int TOP_N = 30;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<NewsItem> fetch() {
        try {
            String topJson = Jsoup.connect(API_BASE + "topstories.json")
                .ignoreContentType(true).timeout(15_000).execute().body();
            List<NewsItem> items = new ArrayList<>();
            for (JsonNode idNode : objectMapper.readTree(topJson)) {
                long id = idNode.asLong();
                if (items.size() >= TOP_N) break;
                try {
                    String itemJson = Jsoup.connect(API_BASE + "item/" + id + ".json")
                        .ignoreContentType(true).timeout(10_000).execute().body();
                    NewsItem item = parseItemJson(itemJson, id);
                    if (item != null && item.title() != null && !item.title().isBlank()) {
                        items.add(item);
                    }
                } catch (Exception e) {
                    // 单条失败跳过,不影响整轮
                }
            }
            return items;
        } catch (IOException e) {
            throw new RuntimeException("Hacker News 爬取失败", e);
        }
    }

    static NewsItem parseItemJson(String json, long id) {
        try {
            JsonNode node = new ObjectMapper().readTree(json);
            String title = node.path("title").asText(null);
            if (title == null || title.isBlank()) return null;
            String url = node.path("url").asText(null);
            if (url == null || url.isBlank()) {
                url = "https://news.ycombinator.com/item?id=" + id;
            }
            return new NewsItem(title, url, "Hacker News");
        } catch (Exception e) {
            return null;
        }
    }
}
```

新建 `backend/src/main/java/com/neuralbase/entity/CrawlerSource.java`:

```java
package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("crawler_source")
public class CrawlerSource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String url;
    private String type;
    private String selector;
    private String category;
    private Boolean enabled;
    private Integer freqMin;
    private OffsetDateTime lastCrawl;
}
```

新建 `backend/src/main/java/com/neuralbase/mapper/CrawlerSourceMapper.java`:

```java
package com.neuralbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neuralbase.entity.CrawlerSource;

public interface CrawlerSourceMapper extends BaseMapper<CrawlerSource> {
}
```

`DataInitializer.run` 末尾加:

```java
        seedNewsSources();
    }

    private void seedNewsSources() {
        if (crawlerSourceMapper.selectCount(null) == 0) {
            CrawlerSource github = new CrawlerSource();
            github.setName("GitHub Trending");
            github.setUrl("https://github.com/trending");
            github.setType("html");
            github.setEnabled(true);
            crawlerSourceMapper.insert(github);

            CrawlerSource hn = new CrawlerSource();
            hn.setName("Hacker News");
            hn.setUrl("https://hacker-news.firebaseio.com/v0/topstories.json");
            hn.setType("api");
            hn.setEnabled(true);
            crawlerSourceMapper.insert(hn);
        }
    }
```

(构造器注入 `CrawlerSourceMapper crawlerSourceMapper`)

`NeuralBaseApplication` 加 `@EnableScheduling`:

```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan("com.neuralbase.mapper")
```

新建测试假件 `backend/src/test/java/com/neuralbase/config/MockNewsConfig.java`:

```java
package com.neuralbase.config;

import com.neuralbase.news.NewsFetcher;
import com.neuralbase.news.NewsItem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
public class MockNewsConfig {

    @Bean
    @Primary
    public NewsFetcher mockGithubFetcher() {
        return () -> List.of(
            new NewsItem("repo-1", "https://github.com/a/b", "GitHub Trending"),
            new NewsItem("repo-2", "https://github.com/c/d", "GitHub Trending"));
    }

    @Bean
    @Primary
    public NewsFetcher mockHnFetcher() {
        return () -> List.of(new NewsItem("HN story", "https://news.ycombinator.com/item?id=9", "Hacker News"));
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=NewsFetcherParseTest -q`
Expected: 3 个测试全 PASS

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/neuralbase/news/ backend/src/main/java/com/neuralbase/entity/CrawlerSource.java backend/src/main/java/com/neuralbase/mapper/CrawlerSourceMapper.java backend/src/main/java/com/neuralbase/config/DataInitializer.java backend/src/main/java/com/neuralbase/NeuralBaseApplication.java backend/src/test/java/com/neuralbase/news/ backend/src/test/java/com/neuralbase/config/MockNewsConfig.java
git commit -m "feat: news fetchers (github trending + hn api) with offline-testable parsers"
```

---

### Task 2: NewsAiService(TDD 假 ChatModel)

**Files:**
- Create: `backend/src/main/java/com/neuralbase/service/NewsAiService.java`
- Test: `backend/src/test/java/com/neuralbase/news/NewsAiServiceTest.java`

**Interfaces:**
- Produces: `NewsAiService.summarize(String title): SummaryResult(String summary, String category)` — Task 3 消费;分类 ∈ 大模型/框架/工具/论文/其他

- [ ] **Step 1: 写失败测试**

新建 `backend/src/test/java/com/neuralbase/news/NewsAiServiceTest.java`:

```java
package com.neuralbase.news;

import com.neuralbase.config.MockChatConfig;
import com.neuralbase.service.NewsAiService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(MockChatConfig.class)
class NewsAiServiceTest {

    @Autowired
    private ChatModel chatModel;

    @Test
    void summarizeParsesJsonAndFallsBackOnUnknownCategory() {
        // MockChatConfig 返回固定输出?不——测试需可控输出,改用构造注入的假件:
        NewsAiService svc = new NewsAiService(new FakeChatModel("{\"summary\":\"一个很酷的新项目\",\"category\":\"工具\"}"),
            new com.fasterxml.jackson.databind.ObjectMapper());
        var r = svc.summarize("cool project");
        assertThat(r.summary()).isEqualTo("一个很酷的新项目");
        assertThat(r.category()).isEqualTo("工具");
    }

    @Test
    void unknownCategoryFallsBackToOther() {
        NewsAiService svc = new NewsAiService(new FakeChatModel("{\"summary\":\"s\",\"category\":\"神秘领域\"}"),
            new com.fasterxml.jackson.databind.ObjectMapper());
        assertThat(svc.summarize("x").category()).isEqualTo("其他");
    }
}
```

`FakeChatModel`(测试内部类,实现 ChatModel 接口——参照 MockChatConfig 的实现方式;若 MockChatConfig 的 mock 返回可配置,直接复用并注入;否则在本测试类内定义私有静态实现,`call(Prompt)` 返回固定文本,其余方法抛 UnsupportedOperationException)

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=NewsAiServiceTest -q`
Expected: 编译失败(NewsAiService 不存在)

- [ ] **Step 3: 写实现**

新建 `backend/src/main/java/com/neuralbase/service/NewsAiService.java`:

```java
package com.neuralbase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class NewsAiService {

    private static final Set<String> CATEGORIES = Set.of("大模型", "框架", "工具", "论文", "其他");
    private static final String SYSTEM_PROMPT =
        "你是 AI 技术日报编辑。根据给定的文章标题(可能附带一句话描述),"
        + "输出 JSON:{\"summary\":\"一句话中文摘要(不超过 60 字)\",\"category\":\"大模型|框架|工具|论文|其他\"}。"
        + "只输出 JSON,不要 markdown 代码块。";

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public NewsAiService(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    public SummaryResult summarize(String title) {
        ChatResponse resp = chatModel.call(new Prompt(
            new SystemMessage(SYSTEM_PROMPT), new UserMessage(title)));
        String raw = resp.getResult() == null ? null : resp.getResult().getOutput().getText();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("AI 未返回内容");
        }
        String json = raw.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("```\\s*$", "").strip();
        }
        try {
            SummaryResult result = objectMapper.readValue(json, SummaryResult.class);
            String category = result.category() == null ? "其他" : result.category().strip();
            if (!CATEGORIES.contains(category)) {
                category = "其他";
            }
            String summary = result.summary() == null ? "" : result.summary().strip();
            return new SummaryResult(summary, category);
        } catch (Exception e) {
            throw new IllegalStateException("AI 返回的 JSON 无法解析: " + raw, e);
        }
    }

    public record SummaryResult(String summary, String category) {
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=NewsAiServiceTest -q`
Expected: 2 个测试全 PASS

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/neuralbase/service/NewsAiService.java backend/src/test/java/com/neuralbase/news/NewsAiServiceTest.java
git commit -m "feat: news ai summarize service (summary + fixed categories)"
```

---

### Task 3: DailyNewsService 爬取流程(TDD)

**Files:**
- Create: `backend/src/main/java/com/neuralbase/entity/DailyNews.java`、`backend/src/main/java/com/neuralbase/entity/CrawlTask.java`、`backend/src/main/java/com/neuralbase/mapper/DailyNewsMapper.java`、`backend/src/main/java/com/neuralbase/mapper/CrawlTaskMapper.java`
- Create: `backend/src/main/java/com/neuralbase/service/DailyNewsService.java`
- Test: `backend/src/test/java/com/neuralbase/news/DailyNewsServiceTest.java`

**Interfaces:**
- Consumes: `NewsFetcher`(Task 1,测试假件)、`NewsAiService.summarize`(Task 2)
- Produces: `crawlAsync()`、`list(category, unreadOnly)`、`markRead(id, isRead)`、`markAllRead()`、`favorite(id, kbId)`、`latestTask()`、`startCrawl()`(同步建任务校验,供 Controller)— Task 4 消费

- [ ] **Step 1: 写失败测试**

新建 `backend/src/test/java/com/neuralbase/news/DailyNewsServiceTest.java`:

```java
package com.neuralbase.news;

import com.neuralbase.config.MockEmbeddingConfig;
import com.neuralbase.config.MockNewsConfig;
import com.neuralbase.entity.CrawlTask;
import com.neuralbase.mapper.CrawlTaskMapper;
import com.neuralbase.mapper.DailyNewsMapper;
import com.neuralbase.service.DailyNewsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import({MockEmbeddingConfig.class, MockNewsConfig.class})
class DailyNewsServiceTest {

    @Autowired
    private DailyNewsService service;
    @Autowired
    private DailyNewsMapper dailyNewsMapper;
    @Autowired
    private CrawlTaskMapper crawlTaskMapper;

    @Test
    void crawlCreatesDeduplicatedNewsAndTaskSucceeds() {
        service.startCrawl();
        service.runCrawlNow(); // 同步执行(测试不可等异步)
        var all = dailyNewsMapper.selectList(null);
        assertThat(all).hasSize(3); // 2 + 1 假件
        service.startCrawl();
        service.runCrawlNow(); // 第二轮去重:不新增
        assertThat(dailyNewsMapper.selectList(null)).hasSize(3);

        CrawlTask latest = crawlTaskMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CrawlTask>()
                .orderByDesc(CrawlTask::getId).last("LIMIT 1")).get(0);
        assertThat(latest.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void secondCrawlRejectedWhileRunning() {
        // 手动触发两次:第一次建 RUNNING 任务,第二次应拒绝
        service.startCrawl();
        assertThatThrownBy(() -> service.startCrawl())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("进行中");
    }

    @Test
    void markReadAndListFilters() {
        service.startCrawl();
        service.runCrawlNow();
        var news = dailyNewsMapper.selectList(null);
        service.markRead(news.get(0).getId(), true);
        assertThat(service.list(null, true)).allMatch(n -> n.isRead());
    }
}
```

注意:`crawlAsync()` 保留异步(定时/接口用);测试走同步 `runCrawlNow()`(同逻辑私有核心方法,`crawlAsync` 仅包一层 try/catch + @Async)。AI 汇总在测试中用假 ChatModel(MockChatConfig 的 mock 返回什么?MockChatConfig mock 返回固定文本——NewsAiService 解析可能失败 → summary null 仍入库 ✓ 无碍;或测试里 AI 失败容忍正好被覆盖)。

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=DailyNewsServiceTest -q`
Expected: 编译失败

- [ ] **Step 3: 写实现**

`entity/DailyNews.java`:

```java
package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("daily_news")
public class DailyNews {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sourceId;
    private String url;
    private String title;
    private String category;
    private String summary;
    private Boolean isRead;
    private OffsetDateTime createdAt;
}
```

`entity/CrawlTask.java`:

```java
package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("crawl_task")
public class CrawlTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private String targetUrl;
    private String status;
    private Integer progress;
    private String error;
    private OffsetDateTime createdAt;
}
```

`mapper/DailyNewsMapper.java` / `mapper/CrawlTaskMapper.java`(标准 BaseMapper 两个文件)

`service/DailyNewsService.java`:

```java
package com.neuralbase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neuralbase.entity.CrawlTask;
import com.neuralbase.entity.CrawlerSource;
import com.neuralbase.entity.DailyNews;
import com.neuralbase.entity.Document;
import com.neuralbase.entity.Kb;
import com.neuralbase.mapper.CrawlTaskMapper;
import com.neuralbase.mapper.CrawlerSourceMapper;
import com.neuralbase.mapper.DailyNewsMapper;
import com.neuralbase.mapper.DocumentMapper;
import com.neuralbase.mapper.KbMapper;
import com.neuralbase.news.NewsFetcher;
import com.neuralbase.news.NewsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DailyNewsService {

    private static final Logger log = LoggerFactory.getLogger(DailyNewsService.class);

    private final List<NewsFetcher> fetchers;
    private final DailyNewsMapper dailyNewsMapper;
    private final CrawlTaskMapper crawlTaskMapper;
    private final CrawlerSourceMapper crawlerSourceMapper;
    private final NewsAiService newsAiService;
    private final TextChunker textChunker;
    private final VectorStore vectorStore;
    private final DocumentMapper documentMapper;
    private final KbMapper kbMapper;
    private final JdbcTemplate jdbcTemplate;

    public DailyNewsService(List<NewsFetcher> fetchers, DailyNewsMapper dailyNewsMapper,
                            CrawlTaskMapper crawlTaskMapper, CrawlerSourceMapper crawlerSourceMapper,
                            NewsAiService newsAiService, TextChunker textChunker,
                            VectorStore vectorStore, DocumentMapper documentMapper, KbMapper kbMapper,
                            JdbcTemplate jdbcTemplate) {
        this.fetchers = fetchers;
        this.dailyNewsMapper = dailyNewsMapper;
        this.crawlTaskMapper = crawlTaskMapper;
        this.crawlerSourceMapper = crawlerSourceMapper;
        this.newsAiService = newsAiService;
        this.textChunker = textChunker;
        this.vectorStore = vectorStore;
        this.documentMapper = documentMapper;
        this.kbMapper = kbMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 手动/定时入口:校验无 RUNNING 任务后建任务(并发保护),再异步执行。 */
    public void startCrawl() {
        Long running = crawlTaskMapper.selectCount(
            new LambdaQueryWrapper<CrawlTask>()
                .eq(CrawlTask::getType, "DAILY_NEWS")
                .eq(CrawlTask::getStatus, "RUNNING"));
        if (running > 0) {
            throw new IllegalArgumentException("已有爬取任务进行中");
        }
        CrawlTask task = new CrawlTask();
        task.setType("DAILY_NEWS");
        task.setStatus("RUNNING");
        task.setProgress(0);
        task.setCreatedAt(OffsetDateTime.now());
        crawlTaskMapper.insert(task);
    }

    @Async
    public void crawlAsync() {
        runCrawlNow();
    }

    /** 同步核心流程(测试直接调用;crawlAsync 仅包异步壳)。 */
    public void runCrawlNow() {
        CrawlTask task = latestRunning();
        try {
            for (NewsFetcher fetcher : fetchers) {
                List<NewsItem> items;
                try {
                    items = fetcher.fetch();
                } catch (Exception e) {
                    log.warn("新闻源爬取失败: {}", e.getMessage());
                    continue; // 单源失败不影响整轮
                }
                for (NewsItem item : items) {
                    if (exists(item.url())) continue; // 去重
                    DailyNews n = new DailyNews();
                    n.setSourceId(sourceIdOf(item.sourceName()));
                    n.setUrl(item.url());
                    n.setTitle(item.title());
                    n.setIsRead(false);
                    dailyNewsMapper.insert(n);
                }
            }
            updateTask(task.getId(), "RUNNING", 50, null);

            // 汇总:补齐所有 summary 为空的条目(天然支持重试),单条失败保留 null
            List<DailyNews> pending = dailyNewsMapper.selectList(
                new LambdaQueryWrapper<DailyNews>().isNull(DailyNews::getSummary));
            int done = 0;
            for (DailyNews n : pending) {
                try {
                    var r = newsAiService.summarize(n.getTitle());
                    n.setSummary(r.summary());
                    n.setCategory(r.category());
                } catch (Exception e) {
                    log.warn("新闻摘要失败 id={}: {}", n.getId(), e.getMessage());
                }
                dailyNewsMapper.updateById(n);
                done++;
            }
            updateTask(task.getId(), "SUCCESS", 100, null);
            log.info("新闻爬取完成:{} 条,{} 条汇总", pending.size(), done);
            touchSources();
        } catch (Exception e) {
            if (task != null) {
                updateTask(task.getId(), "FAILED", 0, e.getMessage());
            }
            log.error("新闻爬取失败", e);
        }
    }

    public List<DailyNews> list(String category, Boolean unreadOnly) {
        return dailyNewsMapper.selectList(
            new LambdaQueryWrapper<DailyNews>()
                .eq(category != null && !category.isBlank(), DailyNews::getCategory, category)
                .eq(Boolean.TRUE.equals(unreadOnly), DailyNews::getIsRead, false)
                .orderByDesc(DailyNews::getCreatedAt)
                .last("LIMIT 200"));
    }

    public void markRead(Long id, boolean isRead) {
        DailyNews n = dailyNewsMapper.selectById(id);
        if (n == null) throw new IllegalArgumentException("新闻不存在: " + id);
        n.setIsRead(isRead);
        dailyNewsMapper.updateById(n);
    }

    public void markAllRead() {
        jdbcTemplate.update("UPDATE daily_news SET is_read = TRUE WHERE is_read = FALSE");
    }

    /** 收藏进知识库:标题+摘要拼接 → chunk → 向量,复用 Document 管线(fileType=NEWS)。 */
    public void favorite(Long id, Long kbId) {
        DailyNews n = dailyNewsMapper.selectById(id);
        if (n == null) throw new IllegalArgumentException("新闻不存在: " + id);
        Kb kb = kbMapper.selectById(kbId);
        if (kb == null) throw new IllegalArgumentException("知识库不存在: " + kbId);
        String text = n.getTitle() + "\n" + (n.getSummary() == null ? "" : n.getSummary());
        List<String> chunks = textChunker.chunk(text);
        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setTitle(n.getTitle());
        doc.setFileType("NEWS");
        doc.setSourceUrl(n.getUrl());
        doc.setStatus("READY");
        doc.setChunkCount(chunks.size());
        doc.setContent(text);
        documentMapper.insert(doc);
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("docId", doc.getId());
            metadata.put("kbId", kbId);
            metadata.put("chunkIndex", i);
            vectorStore.add(List.of(new org.springframework.ai.document.Document(chunks.get(i), metadata)));
        }
    }

    public CrawlTask latestTask() {
        return crawlTaskMapper.selectList(
            new LambdaQueryWrapper<CrawlTask>().orderByDesc(CrawlTask::getId).last("LIMIT 1"))
            .stream().findFirst().orElse(null);
    }

    // ---------- 私有 ----------

    private CrawlTask latestRunning() {
        return crawlTaskMapper.selectList(
            new LambdaQueryWrapper<CrawlTask>()
                .eq(CrawlTask::getType, "DAILY_NEWS")
                .eq(CrawlTask::getStatus, "RUNNING")
                .orderByDesc(CrawlTask::getId).last("LIMIT 1"))
            .stream().findFirst().orElse(null);
    }

    private boolean exists(String url) {
        return dailyNewsMapper.selectCount(
            new LambdaQueryWrapper<DailyNews>().eq(DailyNews::getUrl, url)) > 0;
    }

    private Long sourceIdOf(String name) {
        CrawlerSource s = crawlerSourceMapper.selectOne(
            new LambdaQueryWrapper<CrawlerSource>().eq(CrawlerSource::getName, name));
        return s == null ? null : s.getId();
    }

    private void updateTask(Long id, String status, int progress, String error) {
        CrawlTask t = crawlTaskMapper.selectById(id);
        if (t == null) return;
        t.setStatus(status);
        t.setProgress(progress);
        t.setError(error);
        crawlTaskMapper.updateById(t);
    }

    private void touchSources() {
        jdbcTemplate.update("UPDATE crawler_source SET last_crawl = now()");
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=DailyNewsServiceTest -q`
Expected: 3 个测试全 PASS(注意:MockChatConfig 的假 ChatModel 返回固定文本,NewsAiService 解析大概率抛异常 → 单篇失败仍入库路径被真实覆盖)

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/neuralbase/entity/DailyNews.java backend/src/main/java/com/neuralbase/entity/CrawlTask.java backend/src/main/java/com/neuralbase/mapper/DailyNewsMapper.java backend/src/main/java/com/neuralbase/mapper/CrawlTaskMapper.java backend/src/main/java/com/neuralbase/service/DailyNewsService.java backend/src/test/java/com/neuralbase/news/DailyNewsServiceTest.java
git commit -m "feat: daily news crawl pipeline with dedup, task state and failure tolerance"
```

---

### Task 4: Controller + 定时任务(TDD)

**Files:**
- Create: `backend/src/main/java/com/neuralbase/controller/DailyNewsController.java`
- Create: `backend/src/main/java/com/neuralbase/scheduler/DailyNewsJob.java`
- Modify: `backend/src/main/resources/application.yml`(cron 配置)
- Test: `backend/src/test/java/com/neuralbase/news/DailyNewsControllerTest.java`

**Interfaces:**
- Produces: `GET /api/news`、`POST /api/news/crawl`、`PUT /api/news/{id}/read`、`PUT /api/news/read-all`、`POST /api/news/{id}/favorite`、`GET /api/news/task/latest` — Task 6 前端消费

- [ ] **Step 1: 写失败测试**

新建 `backend/src/test/java/com/neuralbase/news/DailyNewsControllerTest.java`:

```java
package com.neuralbase.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.config.MockEmbeddingConfig;
import com.neuralbase.config.MockNewsConfig;
import com.neuralbase.service.DailyNewsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({MockEmbeddingConfig.class, MockNewsConfig.class})
class DailyNewsControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DailyNewsService service;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asText();
    }

    @Test
    void crawlListReadFavoriteFlow() throws Exception {
        String tk = token();
        service.startCrawl();
        service.runCrawlNow();

        mvc.perform(get("/api/news").header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(3));

        long id = objectMapper.readTree(mvc.perform(get("/api/news")
                .header("Authorization", "Bearer " + tk)).andReturn().getResponse().getContentAsString())
            .get("data").get(0).get("id").asLong();

        mvc.perform(put("/api/news/" + id + "/read")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isRead\":true}"))
            .andExpect(status().isOk());

        mvc.perform(put("/api/news/read-all").header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk());

        mvc.perform(post("/api/news/" + id + "/favorite")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kbId\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/news/task/latest").header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        mvc.perform(post("/api/news/crawl").header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk());
    }

    @Test
    void newsEndpointsRequireAuth() throws Exception {
        mvc.perform(get("/api/news")).andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=DailyNewsControllerTest -q`
Expected: FAIL — 端点 404

- [ ] **Step 3: 写实现**

新建 `backend/src/main/java/com/neuralbase/controller/DailyNewsController.java`:

```java
package com.neuralbase.controller;

import com.neuralbase.common.Result;
import com.neuralbase.entity.CrawlTask;
import com.neuralbase.entity.DailyNews;
import com.neuralbase.service.DailyNewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/news")
public class DailyNewsController {

    private final DailyNewsService dailyNewsService;

    public DailyNewsController(DailyNewsService dailyNewsService) {
        this.dailyNewsService = dailyNewsService;
    }

    @GetMapping
    public Result<List<DailyNews>> list(@RequestParam(required = false) String category,
                                        @RequestParam(required = false) Boolean unreadOnly) {
        return Result.ok(dailyNewsService.list(category, unreadOnly));
    }

    @PostMapping("/crawl")
    public Result<Void> crawl() {
        dailyNewsService.startCrawl();
        dailyNewsService.crawlAsync();
        return Result.ok(null);
    }

    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id, @RequestBody ReadRequest req) {
        dailyNewsService.markRead(id, req == null || Boolean.TRUE.equals(req.isRead()));
        return Result.ok(null);
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        dailyNewsService.markAllRead();
        return Result.ok(null);
    }

    @PostMapping("/{id}/favorite")
    public Result<Void> favorite(@PathVariable Long id, @RequestBody FavoriteRequest req) {
        dailyNewsService.favorite(id, req == null ? null : req.kbId());
        return Result.ok(null);
    }

    @GetMapping("/task/latest")
    public Result<CrawlTask> latestTask() {
        return Result.ok(dailyNewsService.latestTask());
    }

    public record ReadRequest(Boolean isRead) {
    }

    public record FavoriteRequest(Long kbId) {
    }
}
```

新建 `backend/src/main/java/com/neuralbase/scheduler/DailyNewsJob.java`:

```java
package com.neuralbase.scheduler;

import com.neuralbase.service.DailyNewsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyNewsJob {

    private static final Logger log = LoggerFactory.getLogger(DailyNewsJob.class);

    private final DailyNewsService dailyNewsService;

    public DailyNewsJob(DailyNewsService dailyNewsService) {
        this.dailyNewsService = dailyNewsService;
    }

    @Scheduled(cron = "${neuralbase.news.cron:0 0 8 * * *}")
    public void run() {
        try {
            dailyNewsService.startCrawl(); // 已有 RUNNING 会抛异常,定时任务静默跳过
            dailyNewsService.crawlAsync();
        } catch (Exception e) {
            log.info("跳过定时爬取: {}", e.getMessage());
        }
    }
}
```

`application.yml` 的 `neuralbase:` 块加:

```yaml
  news:
    cron: ${NEWS_CRON:0 0 8 * * *}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=DailyNewsControllerTest -q`
Expected: 2 个测试全 PASS

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/neuralbase/controller/DailyNewsController.java backend/src/main/java/com/neuralbase/scheduler/DailyNewsJob.java backend/src/main/resources/application.yml backend/src/test/java/com/neuralbase/news/DailyNewsControllerTest.java
git commit -m "feat: daily news rest api and scheduled job"
```

---

### Task 5: 前端 api/store + DailyNewsView 重写(TDD)

**Files:**
- Modify: `frontend/src/types/index.ts`(NewsItem/NewsTask)
- Create: `frontend/src/api/news.ts`
- Create: `frontend/src/stores/news.ts`
- Modify: `frontend/src/views/DailyNewsView.vue`(重写)
- Test: `frontend/src/stores/__tests__/news.test.ts`

**Interfaces:**
- Consumes: Task 4 接口
- Produces: store `{items, task, categories, filter, unreadOnly, load, triggerCrawl, markRead, markAllRead, favorite, startPolling, stopPolling}` — 视图消费

- [ ] **Step 1: 写失败测试**

新建 `frontend/src/stores/__tests__/news.test.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useNewsStore } from '@/stores/news'

vi.mock('@/api/news', () => ({
  listNews: vi.fn(),
  triggerCrawl: vi.fn(),
  latestTask: vi.fn(),
  markRead: vi.fn(),
  markAllRead: vi.fn(),
  favoriteNews: vi.fn(),
}))

import { listNews, latestTask, markRead, triggerCrawl } from '@/api/news'

const item = { id: 1, title: 'cool', url: 'https://x', category: '工具', summary: 's', isRead: false, createdAt: '' }

describe('news store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  it('load 填充列表并派生分类', async () => {
    vi.mocked(listNews).mockResolvedValue([item])
    const store = useNewsStore()
    await store.load()
    expect(store.items).toHaveLength(1)
    expect(store.categories).toContain('工具')
  })

  it('triggerCrawl 后轮询任务直到非 RUNNING 自动停止', async () => {
    vi.mocked(listNews).mockResolvedValue([item])
    vi.mocked(triggerCrawl).mockResolvedValue(null)
    vi.mocked(latestTask)
      .mockResolvedValueOnce({ id: 1, type: 'DAILY_NEWS', status: 'RUNNING', progress: 10, createdAt: '' })
      .mockResolvedValue({ id: 1, type: 'DAILY_NEWS', status: 'SUCCESS', progress: 100, createdAt: '' })
    const store = useNewsStore()
    await store.triggerCrawl()
    expect(triggerCrawl).toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(2000)
    expect(store.task?.status).toBe('SUCCESS')
    await vi.advanceTimersByTimeAsync(2000)
    expect(latestTask).toHaveBeenCalledTimes(2) // 自动停止
    store.stopPolling()
  })

  it('markRead 更新本地并调接口', async () => {
    vi.mocked(listNews).mockResolvedValue([item])
    vi.mocked(markRead).mockResolvedValue(null)
    const store = useNewsStore()
    await store.load()
    await store.markRead(1, true)
    expect(markRead).toHaveBeenCalledWith(1, true)
    expect(store.items[0].isRead).toBe(true)
  })
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/stores/__tests__/news.test.ts`
Expected: FAIL — 模块不存在

- [ ] **Step 3: 写实现**

`types/index.ts` 加:

```ts
export interface NewsItem {
  id: number
  sourceId?: number
  url?: string
  title: string
  category?: string
  summary?: string
  isRead: boolean
  createdAt: string
}

export interface NewsTask {
  id: number
  type: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
  progress: number
  error?: string
  createdAt: string
}
```

新建 `frontend/src/api/news.ts`:

```ts
import http from './request'
import type { NewsItem, NewsTask } from '@/types'

export const listNews = (params?: { category?: string; unreadOnly?: boolean }) =>
  http.get<NewsItem[]>('/news', params)

export const triggerCrawl = () => http.post<null>('/news/crawl')

export const latestTask = () => http.get<NewsTask | null>('/news/task/latest')

export const markRead = (id: number, isRead: boolean) =>
  http.put<null>(`/news/${id}/read`, { isRead })

export const markAllRead = () => http.put<null>('/news/read-all')

export const favoriteNews = (id: number, kbId: number) =>
  http.post<null>(`/news/${id}/favorite`, { kbId })
```

新建 `frontend/src/stores/news.ts`:

```ts
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as newsApi from '@/api/news'
import type { NewsItem, NewsTask } from '@/types'

export const useNewsStore = defineStore('news', () => {
  const items = ref<NewsItem[]>([])
  const task = ref<NewsTask | null>(null)
  const filterCategory = ref<string>('')
  const unreadOnly = ref(false)

  const categories = computed(() =>
    Array.from(new Set(items.value.map((n) => n.category).filter((c): c is string => !!c))),
  )
  const filtered = computed(() =>
    items.value.filter(
      (n) =>
        (!filterCategory.value || n.category === filterCategory.value) &&
        (!unreadOnly.value || !n.isRead),
    ),
  )

  let pollTimer: number | null = null

  async function load() {
    items.value = await newsApi.listNews({ unreadOnly: unreadOnly.value || undefined })
  }

  async function refreshTask() {
    task.value = await newsApi.latestTask()
    if (task.value?.status === 'RUNNING') startPolling()
    else stopPolling()
  }

  async function triggerCrawl() {
    await newsApi.triggerCrawl()
    await refreshTask()
  }

  async function markRead(id: number, isRead: boolean) {
    await newsApi.markRead(id, isRead)
    const target = items.value.find((n) => n.id === id)
    if (target) target.isRead = isRead
  }

  async function markAllRead() {
    await newsApi.markAllRead()
    items.value.forEach((n) => (n.isRead = true))
  }

  async function favorite(id: number, kbId: number) {
    await newsApi.favoriteNews(id, kbId)
  }

  function startPolling() {
    stopPolling()
    pollTimer = window.setInterval(() => void refreshTask(), 2000)
  }

  function stopPolling() {
    if (pollTimer != null) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  return {
    items, task, filterCategory, unreadOnly, categories, filtered,
    load, refreshTask, triggerCrawl, markRead, markAllRead, favorite,
    startPolling, stopPolling,
  }
})
```

`DailyNewsView.vue` 全文重写(工具栏 + 卡片 + 收藏弹窗):

```vue
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { NButton, NModal, NSelect, NSwitch, NTag } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { useNewsStore } from '@/stores/news'
import { listKbs } from '@/api/kb'
import { message } from '@/utils/feedback'
import type { Kb } from '@/types'

const newsStore = useNewsStore()
const crawling = ref(false)
const favoriteModal = ref(false)
const favoriteNewsId = ref<number | null>(null)
const favoriteKbId = ref<number | null>(null)
const kbs = ref<Kb[]>([])

const categoryMeta: Record<string, string> = {
  大模型: 'text-violet-600 dark:text-violet-300',
  框架: 'text-cyan-600 dark:text-cyan-300',
  工具: 'text-emerald-600 dark:text-emerald-300',
  论文: 'text-amber-600 dark:text-amber-300',
  其他: 'text-gray-500',
}

onMounted(async () => {
  await newsStore.load()
  await newsStore.refreshTask()
})

onBeforeUnmount(() => newsStore.stopPolling())

async function onCrawl() {
  crawling.value = true
  try {
    await newsStore.triggerCrawl()
    message.success('爬取任务已启动')
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    crawling.value = false
  }
}

function openUrl(n: { id: number; url?: string }) {
  if (n.url) window.open(n.url, '_blank', 'noopener')
  void newsStore.markRead(n.id, true)
}

async function openFavorite(id: number) {
  favoriteNewsId.value = id
  favoriteKbId.value = null
  favoriteModal.value = true
  if (!kbs.value.length) {
    try {
      kbs.value = await listKbs()
    } catch {
      kbs.value = []
    }
  }
}

async function onFavoriteConfirm() {
  if (!favoriteNewsId.value || !favoriteKbId.value) {
    message.warning('请选择知识库')
    return
  }
  try {
    await newsStore.favorite(favoriteNewsId.value, favoriteKbId.value)
    favoriteModal.value = false
    message.success('已收藏进知识库')
  } catch (e) {
    message.error((e as Error).message)
  }
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-center gap-2">
      <n-button type="primary" size="small" :loading="crawling || newsStore.task?.status === 'RUNNING'" @click="onCrawl">
        <template #icon><Icon icon="mdi:cloud-download-outline" class="h-4 w-4" /></template>
        立即爬取
      </n-button>
      <n-tag v-if="newsStore.task" size="small" :bordered="false"
             :type="newsStore.task.status === 'SUCCESS' ? 'success' : newsStore.task.status === 'FAILED' ? 'error' : 'info'">
        {{ newsStore.task.status === 'RUNNING' ? `爬取中 ${newsStore.task.progress}%` : newsStore.task.status }}
      </n-tag>
      <div class="flex flex-wrap gap-1.5">
        <n-tag v-for="c in newsStore.categories" :key="c" size="small" :bordered="false"
               :type="newsStore.filterCategory === c ? 'warning' : 'default'" class="cursor-pointer"
               @click="newsStore.filterCategory = newsStore.filterCategory === c ? '' : c">
          {{ c }}
        </n-tag>
      </div>
      <div class="ml-auto flex items-center gap-2">
        <span class="text-xs text-gray-500">仅未读</span>
        <n-switch v-model:value="newsStore.unreadOnly" size="small" />
        <n-button size="small" quaternary @click="newsStore.markAllRead()">全部已读</n-button>
      </div>
    </div>

    <div class="space-y-2">
      <div v-for="n in newsStore.filtered" :key="n.id"
           class="glass-card !p-4 transition-colors" :class="!n.isRead ? 'glass-card--selected' : ''">
        <div class="flex items-start justify-between gap-2">
          <button class="text-left text-sm font-medium text-gray-900 hover:text-cyan-600 dark:text-gray-100 dark:hover:text-cyan-300"
                  @click="openUrl(n)">
            {{ n.title }}
          </button>
          <div class="flex shrink-0 items-center gap-2">
            <span v-if="n.category" class="text-xs" :class="categoryMeta[n.category] ?? categoryMeta['其他']">
              {{ n.category }}
            </span>
            <span v-if="!n.isRead" class="h-2 w-2 rounded-full bg-cyan-400" title="未读" />
            <span class="btn-press cursor-pointer rounded p-1 text-gray-400 hover:text-amber-400 dark:text-gray-600"
                  title="收藏到知识库" @click="openFavorite(n.id)">
              <Icon icon="mdi:bookmark-plus-outline" class="h-4 w-4" />
            </span>
          </div>
        </div>
        <p v-if="n.summary" class="mt-1 text-xs leading-relaxed text-gray-500">{{ n.summary }}</p>
        <p v-else class="mt-1 text-xs text-gray-400 dark:text-gray-600">暂无摘要</p>
      </div>
      <div v-if="!newsStore.filtered.length" class="py-16 text-center text-sm text-gray-400 dark:text-gray-600">
        还没有日报,点击右上角「立即爬取」
      </div>
    </div>

    <n-modal v-model:show="favoriteModal" preset="dialog" title="收藏到知识库"
             positive-text="收藏" negative-text="取消" @positive-click="onFavoriteConfirm">
      <div class="py-2">
        <n-select v-model:value="favoriteKbId" placeholder="选择知识库"
                  :options="kbs.map((k) => ({ label: k.name, value: k.id }))" />
      </div>
    </n-modal>
  </div>
</template>
```

- [ ] **Step 4: 运行确认通过**

Run: `cd frontend && npx vitest run src/stores/__tests__/news.test.ts && npx vue-tsc --noEmit && npm run build`
Expected: news 测试 PASS,typecheck 无输出,build 成功

- [ ] **Step 5: 提交**

```bash
git add frontend/src/types/index.ts frontend/src/api/news.ts frontend/src/stores/news.ts frontend/src/stores/__tests__/news.test.ts frontend/src/views/DailyNewsView.vue
git commit -m "feat: daily news timeline page with crawl trigger, filters, read and favorite"
```

---

### Task 6: 全量回归 + CDP/真实冒烟 + 文档

- [ ] **Step 1: 双端全量**

Run: `cd backend && DB_PASSWORD=root mvn test -q`(预期 94 + 3 + 2 + 3 + 2 = 104 绿)
Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run build`(预期 64 + 3 = 67 绿)

- [ ] **Step 2: 真实冒烟(重启后端后)**

1. `POST /api/news/crawl`(curl,带 token)→ 轮询 `/api/news/task/latest` 至 SUCCESS(GitHub + HN 真抓 + DeepSeek 真实摘要)
2. CDP:日报页时间线渲染(分类标签/摘要/未读圆点)、点标题新标签 + 自动已读、分类 chips 筛选、仅未读开关、全部已读、收藏进 KB 后 RAG 检索可命中
3. 亮暗两态巡检

- [ ] **Step 3: 验收修复(如有)提交**

```bash
git add -A
git commit -m "fix: phase5 acceptance findings"
```

- [ ] **Step 4: PROGRESS.md 更新并提交**

Phase 5 状态 ✅、测试数更新、下一步 Phase 7/8:

```bash
git add docs/PROGRESS.md
git commit -m "docs: record phase5 daily news completion"
```

---

## Self-Review 记录

- **规格覆盖**:§2.2 组件 → Task 1/2/3/4/5;§2.3 测试 → 各任务;§三 前端 → Task 5;§四 验收 → Task 6
- **占位符**:无 TBD;测试假件(MockNewsConfig)与真实 fetcher 的 profile 划分明确
- **类型一致**:`NewsItem(title,url,sourceName)`、`SummaryResult(summary,category)`、`DailyNews` 实体字段、前端 `NewsItem`/`NewsTask` 类型、store 动作名全计划一致
