# NeuralBase Phase 4 八股文库 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 八股文自动爬取整理——URL 提交→Jsoup/Playwright 爬取→DeepSeek 提取问答对与标签→可筛选/搜索/收藏进知识库，带任务进度表与断点续跑。

**Architecture:** 后端新增 `crawler`（ArticleCrawler 接口 + Jsoup/Playwright 实现 + 正文提取）+ `BaguwenService`（导入/处理/查询/收藏）+ 三张表（article/import_task/qa_pair）。处理走 @Async + 任务状态机，测试全用假 Crawler + 假 ChatModel 离线。

**Tech Stack:** Spring Boot 3.5.16 + MyBatis-Plus 3.5.17 + Spring AI 1.1.8（复用 DeepSeek ChatModel + VectorStore）+ Jsoup 1.22.2 + Playwright Java 1.60.0。

## Global Constraints

- 代码路径：`D:\neural-base\backend`，`src/main/java/com/neuralbase/`（实体/`entity`、Mapper/`mapper`、服务/`service`、控制器/`controller`、DTO/`dto`，新包 `crawler`）
- 分层模式：entity → `BaseMapper`（接口，非 `IService`）→ `@Service` 构造注入 → `@RestController`，返回统一 `Result<T>(code, message, data)`，`Result.ok(data)` 成功
- jsonb 列处理：entity 字段为 `String`（JSON 文本），插入必须 `CAST(? AS jsonb)`（参照 `MessageMapper.insertWithSources`）；`tags` 查询用 `tags @> {0}::jsonb`
- 测试 mock：测试源码 `com.neuralbase.config` 下的普通 `@Configuration`+`@Primary` 会被主应用组件扫描自动纳入（test-classes 在 classpath）；集成测试 `@BeforeEach` 清表（共享 test 库累积数据）
- 异常：`IllegalArgumentException` → `GlobalExceptionHandler` → 400；不要在 Controller 抛 `CrawlException`（由 service 内部吞掉标 FAILED）
- 不改动既有行为：V1 遗留 `baguwen`/`chat_session`/`chat_message` 等表保留不动；不改 `ChatService`/`DocumentService` 现有逻辑
- 新依赖：`org.jsoup:jsoup:1.22.2`、`com.microsoft.playwright:playwright:1.60.0`。Playwright 浏览器二进制不在 CI/`mvn test` 需要（测试全用假 Crawler），本机冒烟需先 `playwright install chromium`
- 本机运行用 `--server.port=8081`；测试库 `neuralbase_test`；本地 PG 密码 `export DB_PASSWORD='root'`
- 每次提交只 `git add` 显式文件（勿 `git add -A`，避免卷入无关改动）

---

### Task 1: V4 迁移 + 分页拦截器 + Schema 测试

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__baguwen_tables.sql`
- Create: `backend/src/main/java/com/neuralbase/config/MybatisPlusConfig.java`
- Create: `backend/src/main/java/com/neuralbase/entity/BaguwenArticle.java`
- Create: `backend/src/main/java/com/neuralbase/entity/BaguwenImportTask.java`
- Create: `backend/src/main/java/com/neuralbase/entity/BaguwenQaPair.java`
- Create: `backend/src/main/java/com/neuralbase/mapper/BaguwenArticleMapper.java`
- Create: `backend/src/main/java/com/neuralbase/mapper/BaguwenImportTaskMapper.java`
- Create: `backend/src/main/java/com/neuralbase/mapper/BaguwenQaPairMapper.java`
- Modify: `backend/src/test/java/com/neuralbase/schema/SchemaMigrationTest.java`（expected 集合加入新表）

**Interfaces:**
- Produces: 实体 `BaguwenArticle`(id/title/url/content/tags:String/createdAt/updatedAt)、`BaguwenImportTask`(id/url/status/errorMsg/articleId/attempt/createdAt/updatedAt)、`BaguwenQaPair`(id/articleId/question/answer/difficulty/sortOrder/createdAt)
- Produces: `BaguwenArticleMapper.insertWithTags(article)`（jsonb CAST 插入，回填 id）

- [ ] **Step 1: 写 V4 迁移**

```sql
-- Phase 4 八股文库
CREATE TABLE baguwen_article (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(255) NOT NULL,
    url        TEXT,
    content    TEXT NOT NULL,
    tags       JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_baguwen_article_title ON baguwen_article(title);

CREATE TABLE baguwen_import_task (
    id         BIGSERIAL PRIMARY KEY,
    url        TEXT NOT NULL,
    status     VARCHAR(16) NOT NULL,
    error_msg  TEXT,
    article_id BIGINT REFERENCES baguwen_article(id) ON DELETE SET NULL,
    attempt    INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_baguwen_task_status ON baguwen_import_task(status);

CREATE TABLE baguwen_qa_pair (
    id         BIGSERIAL PRIMARY KEY,
    article_id BIGINT NOT NULL REFERENCES baguwen_article(id) ON DELETE CASCADE,
    question   TEXT NOT NULL,
    answer     TEXT NOT NULL,
    difficulty VARCHAR(8) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_baguwen_qa_article ON baguwen_qa_pair(article_id, sort_order);
```

- [ ] **Step 2: 更新 SchemaMigrationTest 的 expected 集合**

把 `SchemaMigrationTest.allBusinessTablesExist` 的 expected 改为：
```java
Set.of("users", "kb", "document", "chat_session", "chat_message", "baguwen", "daily_news",
    "crawl_task", "crawler_source", "vector_store", "baguwen_article",
    "baguwen_import_task", "baguwen_qa_pair");
```

- [ ] **Step 3: 创建三个实体**

`entity/BaguwenArticle.java`：
```java
package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("baguwen_article")
public class BaguwenArticle {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String url;
    private String content;
    private String tags;   // JSONB：JSON 数组文本，如 ["Java","JVM"]；插入必须 CAST(? AS jsonb)
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
```

`entity/BaguwenImportTask.java`：
```java
package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("baguwen_import_task")
public class BaguwenImportTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String url;
    private String status;    // PENDING / PROCESSING / SUCCESS / FAILED
    private String errorMsg;
    private Long articleId;
    private Integer attempt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
```

`entity/BaguwenQaPair.java`：
```java
package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("baguwen_qa_pair")
public class BaguwenQaPair {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private String question;
    private String answer;
    private String difficulty;   // 简单 / 中等 / 困难
    private Integer sortOrder;
    private OffsetDateTime createdAt;
}
```

- [ ] **Step 4: 创建三个 Mapper**

`mapper/BaguwenArticleMapper.java`：
```java
package com.neuralbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neuralbase.entity.BaguwenArticle;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;

public interface BaguwenArticleMapper extends BaseMapper<BaguwenArticle> {

    /** tags 列是 jsonb，MyBatis-Plus 默认按 varchar 绑定会报类型不匹配，显式 CAST 为 jsonb（同 MessageMapper.insertWithSources 模式）。 */
    @Insert("""
        INSERT INTO baguwen_article (title, url, content, tags, created_at, updated_at)
        VALUES (#{title}, #{url}, #{content}, CAST(#{tags} AS jsonb), now(), now())
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertWithTags(BaguwenArticle article);
}
```

`mapper/BaguwenImportTaskMapper.java`：
```java
package com.neuralbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neuralbase.entity.BaguwenImportTask;

public interface BaguwenImportTaskMapper extends BaseMapper<BaguwenImportTask> {
}
```

`mapper/BaguwenQaPairMapper.java`：
```java
package com.neuralbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neuralbase.entity.BaguwenQaPair;

public interface BaguwenQaPairMapper extends BaseMapper<BaguwenQaPair> {
}
```

- [ ] **Step 5: 创建分页拦截器配置**

`config/MybatisPlusConfig.java`：
```java
package com.neuralbase.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
```

- [ ] **Step 6: 跑 SchemaMigrationTest 验证**

Run: `cd /d/neural-base/backend && mvn test -Dtest=SchemaMigrationTest -q`（需 `export DB_PASSWORD='root'`，PG 本机跑着 `neuralbase_test` 库）
Expected: BUILD SUCCESS，`allBusinessTablesExist` 断言通过（含新三表）

- [ ] **Step 7: 提交**

```bash
cd /d/neural-base && git add backend/src/main/resources/db/migration/V4__baguwen_tables.sql \
  backend/src/main/java/com/neuralbase/config/MybatisPlusConfig.java \
  backend/src/main/java/com/neuralbase/entity/BaguwenArticle.java \
  backend/src/main/java/com/neuralbase/entity/BaguwenImportTask.java \
  backend/src/main/java/com/neuralbase/entity/BaguwenQaPair.java \
  backend/src/main/java/com/neuralbase/mapper/BaguwenArticleMapper.java \
  backend/src/main/java/com/neuralbase/mapper/BaguwenImportTaskMapper.java \
  backend/src/main/java/com/neuralbase/mapper/BaguwenQaPairMapper.java \
  backend/src/test/java/com/neuralbase/schema/SchemaMigrationTest.java \
  && git commit -m "feat: phase4 baguwen schema and pagination interceptor"
```

---

### Task 2: 爬虫核心 — 依赖 + ContentExtractor

**Files:**
- Modify: `backend/pom.xml`（加 jsoup 依赖）
- Create: `backend/src/main/java/com/neuralbase/crawler/ArticleCrawler.java`
- Create: `backend/src/main/java/com/neuralbase/crawler/CrawledArticle.java`
- Create: `backend/src/main/java/com/neuralbase/crawler/CrawlException.java`
- Create: `backend/src/main/java/com/neuralbase/crawler/ContentExtractor.java`
- Test: `backend/src/test/java/com/neuralbase/crawler/ContentExtractorTest.java`

**Interfaces:**
- Consumes: —
- Produces: `interface ArticleCrawler { CrawledArticle fetch(String url) throws CrawlException; }`
- Produces: `record CrawledArticle(String title, String url, String html)`
- Produces: `class CrawlException extends RuntimeException`（两个构造器：`(String)`、`(String, Throwable)`）
- Produces: `class ContentExtractor { String extract(String html); }`——通用正文提取，噪音内容剥离

- [ ] **Step 1: pom.xml 加 jsoup 依赖**

在 `</dependencies>` 前的 `tika-parsers-standard-package` 依赖块后加：
```xml
        <dependency>
            <groupId>org.jsoup</groupId>
            <artifactId>jsoup</artifactId>
            <version>1.22.2</version>
        </dependency>
```

- [ ] **Step 2: 创建接口、载体、异常**

`crawler/ArticleCrawler.java`：
```java
package com.neuralbase.crawler;

public interface ArticleCrawler {
    CrawledArticle fetch(String url) throws CrawlException;
}
```

`crawler/CrawledArticle.java`：
```java
package com.neuralbase.crawler;

public record CrawledArticle(String title, String url, String html) {
}
```

`crawler/CrawlException.java`：
```java
package com.neuralbase.crawler;

public class CrawlException extends RuntimeException {
    public CrawlException(String message) {
        super(message);
    }
    public CrawlException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 3: 写失败测试（ContentExtractorTest）**

`crawler/ContentExtractorTest.java`：
```java
package com.neuralbase.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentExtractorTest {

    private final ContentExtractor extractor = new ContentExtractor();

    @Test
    void extractsArticleBodyAndDropsNavigationAdsAndScripts() {
        String html = """
            <html><head><title>JVM 原理</title></head><body>
            <nav><a href="/">首页</a><a href="/tags">标签导航</a></nav>
            <script>alert('广告代码')</script>
            <div class="ad"><p>这里是广告内容</p></div>
            <div class="comment"><p>网友评论：写得不错</p></div>
            <article>
              <h1>JVM 内存模型详解</h1>
              <p>堆（Heap）是运行时数据区，存放对象实例。</p>
              <p>虚拟机栈描述 Java 方法执行的内存模型。</p>
              <p>方法区存储类信息、常量、静态变量。</p>
            </article>
            <footer>版权所有</footer>
            </body></html>
            """;

        String text = extractor.extract(html);

        assertThat(text).contains("JVM 内存模型详解");
        assertThat(text).contains("堆（Heap）是运行时数据区");
        assertThat(text).contains("方法区存储类信息");
        assertThat(text).doesNotContain("标签导航");
        assertThat(text).doesNotContain("广告代码");
        assertThat(text).doesNotContain("这里是广告内容");
        assertThat(text).doesNotContain("网友评论");
        assertThat(text).doesNotContain("版权所有");
    }

    @Test
    void returnsBlankForNullOrBlankHtml() {
        assertThat(extractor.extract(null)).isEmpty();
        assertThat(extractor.extract("   ")).isEmpty();
    }
}
```

- [ ] **Step 4: 跑测试验证失败**

Run: `cd /d/neural-base/backend && mvn test -Dtest=ContentExtractorTest -q`
Expected: 编译错误（ContentExtractor 不存在）→ FAIL

- [ ] **Step 5: 实现 ContentExtractor**

`crawler/ContentExtractor.java`：
```java
package com.neuralbase.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class ContentExtractor {

    private static final int MIN_CONTENT_CHARS = 100;
    private static final String NOISE_SELECTOR =
        "script, style, noscript, iframe, nav, aside, footer, header, form, button, " +
        ".nav, .navbar, .menu, .breadcrumb, .pagination, .ad, .ads, .advert, .advertisement, " +
        ".banner, .recommend, .related, .share, .like, .comment, .comments, .reply, " +
        "[class*=ad-], [id*=ad-], [class*=banner], [class*=recommend], " +
        "div[id*=comment], div[class*=comment]";

    /** 从原始 HTML 提取正文纯文本：先剥噪音标签，再对候选容器做文本密度评分选最优。 */
    public String extract(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parse(html);
        doc.select(NOISE_SELECTOR).remove();

        Element best = null;
        int bestScore = -1;
        for (Element cand : doc.select("article, main, [class*=article], [class*=content], "
            + "[class*=post], [id*=content], [id*=article], body")) {
            int s = score(cand);
            if (s > bestScore) {
                bestScore = s;
                best = cand;
            }
        }
        Element container = best == null ? doc.body() : best;
        if (container == null) {
            return "";
        }
        String text = container.text().strip();
        if (text.length() < MIN_CONTENT_CHARS && container != doc.body() && doc.body() != null) {
            text = doc.body().text().strip();
        }
        return text;
    }

    private int score(Element el) {
        int score = 0;
        for (Element p : el.select("p, li, pre, h1, h2, h3, h4")) {
            score += Math.min(p.text().length(), 200);
            if (p.tagName().startsWith("h")) {
                score += 10;
            }
            if (p.tagName().equals("pre")) {
                score += 5;
            }
        }
        return score;
    }
}
```

- [ ] **Step 6: 跑测试验证通过**

Run: `cd /d/neural-base/backend && mvn test -Dtest=ContentExtractorTest -q`
Expected: 2 tests PASS

- [ ] **Step 7: 提交**

```bash
cd /d/neural-base && git add backend/pom.xml \
  backend/src/main/java/com/neuralbase/crawler/ArticleCrawler.java \
  backend/src/main/java/com/neuralbase/crawler/CrawledArticle.java \
  backend/src/main/java/com/neuralbase/crawler/CrawlException.java \
  backend/src/main/java/com/neuralbase/crawler/ContentExtractor.java \
  backend/src/test/java/com/neuralbase/crawler/ContentExtractorTest.java \
  && git commit -m "feat: baguwen crawler core and content extractor"
```

---

### Task 3: Jsoup / Playwright / CompositeCrawler

**Files:**
- Modify: `backend/pom.xml`（加 playwright 依赖）
- Create: `backend/src/main/java/com/neuralbase/crawler/JsoupCrawler.java`
- Create: `backend/src/main/java/com/neuralbase/crawler/PlaywrightCrawler.java`
- Create: `backend/src/main/java/com/neuralbase/crawler/CompositeCrawler.java`
- Test: `backend/src/test/java/com/neuralbase/crawler/JsoupCrawlerTest.java`
- Test: `backend/src/test/java/com/neuralbase/crawler/CompositeCrawlerTest.java`

**Interfaces:**
- Consumes: `ArticleCrawler`、`CrawledArticle`、`CrawlException`、`ContentExtractor`（Task 2）
- Produces: `@Component JsoupCrawler implements ArticleCrawler`（Bean 名 `jsoupCrawler`）+ 公开方法 `CrawledArticle parse(String html, String url)`（离线可测）
- Produces: `@Component PlaywrightCrawler implements ArticleCrawler`（Bean 名 `playwrightCrawler`，无单测）
- Produces: `@Component CompositeCrawler implements ArticleCrawler`——构造器 `(@Qualifier("jsoupCrawler") ArticleCrawler jsoup, @Qualifier("playwrightCrawler") ArticleCrawler playwright, ContentExtractor extractor)`；静态正文不足 100 字或抛异常时降级 Playwright

- [ ] **Step 1: pom.xml 加 playwright 依赖**

在 Task 2 的 jsoup 依赖块后加：
```xml
        <dependency>
            <groupId>com.microsoft.playwright</groupId>
            <artifactId>playwright</artifactId>
            <version>1.60.0</version>
        </dependency>
```

- [ ] **Step 2: 写失败测试（JsoupCrawlerTest + CompositeCrawlerTest）**

`crawler/JsoupCrawlerTest.java`：
```java
package com.neuralbase.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsoupCrawlerTest {

    private final JsoupCrawler crawler = new JsoupCrawler();

    @Test
    void parseExtractsTitleAndKeepsHtml() {
        String html = """
            <html><head><title>Spring 面试题</title></head>
            <body><article><p>正文内容。</p></article></body></html>
            """;

        CrawledArticle article = crawler.parse(html, "http://example.com/a");

        assertThat(article.title()).isEqualTo("Spring 面试题");
        assertThat(article.url()).isEqualTo("http://example.com/a");
        assertThat(article.html()).contains("Spring 面试题");
    }

    @Test
    void parseFallsBackToH1WhenNoTitleTag() {
        String html = "<html><body><h1>没有 title 的页面</h1><p>内容</p></body></html>";

        CrawledArticle article = crawler.parse(html, "http://example.com/b");

        assertThat(article.title()).isEqualTo("没有 title 的页面");
    }
}
```

`crawler/CompositeCrawlerTest.java`：
```java
package com.neuralbase.crawler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeCrawlerTest {

    private final ContentExtractor extractor = new ContentExtractor();

    @Test
    void usesStaticWhenItHasSubstantialContent() {
        AtomicBoolean renderCalled = new AtomicBoolean(false);
        ArticleCrawler jsoup = url -> new CrawledArticle("文章", url,
            "<html><body><article><p>" + "正文".repeat(200) + "</p></article></body></html>");
        ArticleCrawler playwright = url -> {
            renderCalled.set(true);
            throw new CrawlException("不应被调用");
        };
        CompositeCrawler composite = new CompositeCrawler(jsoup, playwright, extractor);

        CrawledArticle article = composite.fetch("http://x/1");

        assertThat(article.title()).isEqualTo("文章");
        assertThat(renderCalled).isFalse();
    }

    @Test
    void fallsBackToPlaywrightWhenStaticHasNoContent() {
        AtomicBoolean renderCalled = new AtomicBoolean(false);
        ArticleCrawler jsoup = url -> new CrawledArticle("壳页面", url,
            "<html><body><div id='app'></div></body></html>");
        ArticleCrawler playwright = url -> {
            renderCalled.set(true);
            return new CrawledArticle("渲染后", url,
                "<html><body><article><p>" + "真实内容".repeat(200) + "</p></article></body></html>");
        };
        CompositeCrawler composite = new CompositeCrawler(jsoup, playwright, extractor);

        CrawledArticle article = composite.fetch("http://x/2");

        assertThat(article.title()).isEqualTo("渲染后");
        assertThat(renderCalled).isTrue();
    }

    @Test
    void fallsBackToPlaywrightWhenStaticThrows() {
        ArticleCrawler jsoup = url -> { throw new CrawlException("连接超时"); };
        ArticleCrawler playwright = url -> new CrawledArticle("兜底", url,
            "<html><body><article><p>" + "内容".repeat(200) + "</p></article></body></html>");
        CompositeCrawler composite = new CompositeCrawler(jsoup, playwright, extractor);

        assertThat(composite.fetch("http://x/3").title()).isEqualTo("兜底");
    }

    @Test
    void throwsWhenBothFail() {
        ArticleCrawler jsoup = url -> { throw new CrawlException("静态失败"); };
        ArticleCrawler playwright = url -> { throw new CrawlException("渲染失败"); };
        CompositeCrawler composite = new CompositeCrawler(jsoup, playwright, extractor);

        assertThatThrownBy(() -> composite.fetch("http://x/4"))
            .isInstanceOf(CrawlException.class);
    }
}
```

- [ ] **Step 3: 跑测试验证失败**

Run: `cd /d/neural-base/backend && mvn test -Dtest=JsoupCrawlerTest,CompositeCrawlerTest -q`
Expected: 编译错误（三个 crawler 类不存在）→ FAIL

- [ ] **Step 4: 实现 JsoupCrawler**

`crawler/JsoupCrawler.java`：
```java
package com.neuralbase.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JsoupCrawler implements ArticleCrawler {

    private static final int TIMEOUT_MS = 10_000;
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    @Override
    public CrawledArticle fetch(String url) throws CrawlException {
        try {
            String html = Jsoup.connect(url)
                .userAgent(UA)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get()
                .html();
            return parse(html, url);
        } catch (IOException e) {
            throw new CrawlException("Jsoup 抓取失败: " + url + " -> " + e.getMessage(), e);
        }
    }

    /** 从 HTML 解析标题与原始 HTML，离线可测（网络请求只发生在 fetch 内）。 */
    public CrawledArticle parse(String html, String url) {
        Document doc = Jsoup.parse(html);
        String title = doc.title();
        if (title == null || title.isBlank()) {
            Element h1 = doc.selectFirst("h1");
            title = h1 == null ? "未命名" : h1.text();
        }
        return new CrawledArticle(title.strip(), url, html);
    }
}
```

- [ ] **Step 5: 实现 PlaywrightCrawler（无单测，冒烟覆盖）**

`crawler/PlaywrightCrawler.java`：
```java
package com.neuralbase.crawler;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.springframework.stereotype.Component;

@Component
public class PlaywrightCrawler implements ArticleCrawler {

    private static final int TIMEOUT_MS = 15_000;

    @Override
    public CrawledArticle fetch(String url) throws CrawlException {
        try (Playwright pw = Playwright.create();
             Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();
            page.setDefaultTimeout(TIMEOUT_MS);
            page.navigate(url);
            page.waitForLoadState(Page.LoadState.DOMCONTENTLOADED);
            return new CrawledArticle(page.title().strip(), url, page.content());
        } catch (Exception e) {
            throw new CrawlException("Playwright 渲染失败: " + url + " -> " + e.getMessage()
                + "（若未安装浏览器，请先执行 playwright install chromium）", e);
        }
    }
}
```

- [ ] **Step 6: 实现 CompositeCrawler**

`crawler/CompositeCrawler.java`：
```java
package com.neuralbase.crawler;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class CompositeCrawler implements ArticleCrawler {

    private static final int MIN_TEXT_CHARS = 100;

    private final ArticleCrawler jsoupCrawler;
    private final ArticleCrawler playwrightCrawler;
    private final ContentExtractor contentExtractor;

    public CompositeCrawler(@Qualifier("jsoupCrawler") ArticleCrawler jsoupCrawler,
                            @Qualifier("playwrightCrawler") ArticleCrawler playwrightCrawler,
                            ContentExtractor contentExtractor) {
        this.jsoupCrawler = jsoupCrawler;
        this.playwrightCrawler = playwrightCrawler;
        this.contentExtractor = contentExtractor;
    }

    @Override
    public CrawledArticle fetch(String url) throws CrawlException {
        CrawlException firstError = null;
        try {
            CrawledArticle staticResult = jsoupCrawler.fetch(url);
            if (hasSubstantialContent(staticResult)) {
                return staticResult;
            }
        } catch (CrawlException e) {
            firstError = e;
        }
        try {
            return playwrightCrawler.fetch(url);
        } catch (CrawlException e) {
            if (firstError != null) {
                e.addSuppressed(firstError);
            }
            throw e;
        }
    }

    private boolean hasSubstantialContent(CrawledArticle article) {
        return article != null && article.html() != null
            && contentExtractor.extract(article.html()).length() >= MIN_TEXT_CHARS;
    }
}
```

- [ ] **Step 7: 跑测试验证通过**

Run: `cd /d/neural-base/backend && mvn test -Dtest=JsoupCrawlerTest,CompositeCrawlerTest -q`
Expected: 6 tests PASS（无需浏览器二进制；PlaywrightCrawler 未被测试调用）

- [ ] **Step 8: 提交**

```bash
cd /d/neural-base && git add backend/pom.xml \
  backend/src/main/java/com/neuralbase/crawler/JsoupCrawler.java \
  backend/src/main/java/com/neuralbase/crawler/PlaywrightCrawler.java \
  backend/src/main/java/com/neuralbase/crawler/CompositeCrawler.java \
  backend/src/test/java/com/neuralbase/crawler/JsoupCrawlerTest.java \
  backend/src/test/java/com/neuralbase/crawler/CompositeCrawlerTest.java \
  && git commit -m "feat: jsoup + playwright composite crawler"
```

---

### Task 4: BaguwenAiService — DeepSeek 结构化提取

**Files:**
- Create: `backend/src/main/java/com/neuralbase/service/BaguwenAiService.java`
- Modify: `backend/src/test/java/com/neuralbase/config/MockChatConfig.java`
- Test: `backend/src/test/java/com/neuralbase/baguwen/BaguwenAiServiceTest.java`

**Interfaces:**
- Consumes: `ChatModel`（Spring AI，非流式 `call`）、`ObjectMapper`
- Produces: `class BaguwenAiService { ExtractionResult extract(String content, String title); }`
- Produces: 内部 record `ExtractionResult(List<QaPair> qaPairs, List<String> tags)`、`QaPair(String question, String answer, String difficulty)`
- Produces: 常量 `SYSTEM_PROMPT` 必须含字符串「整理为面试题格式」（MockChatConfig 据此分发）

- [ ] **Step 1: 写失败测试（BaguwenAiServiceTest，纯单测无 Spring）**

`baguwen/BaguwenAiServiceTest.java`：
```java
package com.neuralbase.baguwen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.service.BaguwenAiService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaguwenAiServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ChatModel chatModelReturning(String text) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
            }
            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.empty();
            }
        };
    }

    @Test
    void parsesJsonWithMarkdownFence() {
        String raw = "```json\n"
            + "{\"qa_pairs\":[{\"question\":\"什么是 JVM 内存模型？\",\"answer\":\"堆、栈、方法区等。\",\"difficulty\":\"中等\"}],"
            + "\"tags\":[\"Java\",\"JVM\"]}\n```";
        BaguwenAiService service = new BaguwenAiService(chatModelReturning(raw), objectMapper);

        BaguwenAiService.ExtractionResult result = service.extract("正文内容", "标题");

        assertThat(result.tags()).containsExactly("Java", "JVM");
        assertThat(result.qaPairs()).hasSize(1);
        assertThat(result.qaPairs().get(0).question()).contains("JVM 内存模型");
        assertThat(result.qaPairs().get(0).difficulty()).isEqualTo("中等");
    }

    @Test
    void throwsWhenJsonMalformed() {
        BaguwenAiService service = new BaguwenAiService(chatModelReturning("这不是 JSON"), objectMapper);

        assertThatThrownBy(() -> service.extract("正文", "标题"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("无法解析");
    }

    @Test
    void throwsWhenNoQaPairs() {
        BaguwenAiService service = new BaguwenAiService(
            chatModelReturning("{\"qa_pairs\":[],\"tags\":[]}"), objectMapper);

        assertThatThrownBy(() -> service.extract("正文", "标题"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("没有提取到任何问答对");
    }
}
```

- [ ] **Step 2: 跑测试验证失败**

Run: `cd /d/neural-base/backend && mvn test -Dtest=BaguwenAiServiceTest -q`
Expected: 编译错误（BaguwenAiService 不存在）→ FAIL

- [ ] **Step 3: 实现 BaguwenAiService**

`service/BaguwenAiService.java`：
```java
package com.neuralbase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BaguwenAiService {

    private static final int MAX_CONTENT_CHARS = 12_000;
    private static final String SYSTEM_PROMPT =
        "请将以下技术文章整理为面试题格式，提取其中的问题和答案。要求："
        + "1. 每个问题简洁明了；2. 答案要点清晰，分条列出；"
        + "3. 标注难度等级（简单/中等/困难）；"
        + "4. 同时识别文章所属技术领域标签（如 Java/Spring/DB/算法/架构 等），一般 1~5 个；"
        + "5. 只输出 JSON，不要 markdown 代码块，格式："
        + "{\"qa_pairs\":[{\"question\":\"…\",\"answer\":\"…\",\"difficulty\":\"简单\"}],\"tags\":[\"Java\"]}";

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public BaguwenAiService(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    public ExtractionResult extract(String content, String title) {
        String text = (title == null ? "" : title + "\n") + content;
        if (text.length() > MAX_CONTENT_CHARS) {
            text = text.substring(0, MAX_CONTENT_CHARS);
        }
        ChatResponse resp = chatModel.call(new Prompt(
            new SystemMessage(SYSTEM_PROMPT),
            new UserMessage(text)));
        String raw = resp.getResult() == null ? null : resp.getResult().getOutput().getText();
        return parse(raw);
    }

    private ExtractionResult parse(String raw) {
        if (raw == null) {
            throw new IllegalStateException("AI 未返回内容");
        }
        String json = raw.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("```\\s*$", "").strip();
        }
        try {
            ExtractionResult result = objectMapper.readValue(json, ExtractionResult.class);
            if (result.qaPairs() == null || result.qaPairs().isEmpty()) {
                throw new IllegalStateException("AI 没有提取到任何问答对");
            }
            return result;
        } catch (Exception e) {
            if (e instanceof IllegalStateException ise) {
                throw ise;
            }
            throw new IllegalStateException("AI 返回的 JSON 无法解析: " + raw, e);
        }
    }

    public record ExtractionResult(List<QaPair> qaPairs, List<String> tags) {
    }
    public record QaPair(String question, String answer, String difficulty) {
    }
}
```

- [ ] **Step 4: 改造 MockChatConfig，call() 对八股文 prompt 返回 JSON**

修改 `src/test/java/com/neuralbase/config/MockChatConfig.java`：
- import 加 `org.springframework.ai.chat.messages.SystemMessage`
- 类内加常量与 call() 分支：

```java
    private static final String BAGUWEN_JSON =
        "{\"qa_pairs\":[{\"question\":\"什么是 JVM 内存模型？\",\"answer\":\"分为堆、栈、方法区等。\",\"difficulty\":\"中等\"}],"
        + "\"tags\":[\"Java\",\"JVM\"]}";
```

把 `call(Prompt prompt)` 方法体改为：
```java
            @Override
            public ChatResponse call(Prompt prompt) {
                PROMPTS.add(prompt);
                boolean baguwen = prompt.getInstructions().stream().anyMatch(m ->
                    m instanceof SystemMessage sm && sm.getText().contains("整理为面试题格式"));
                String text = baguwen ? BAGUWEN_JSON : "mock 回复";
                return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
            }
```

（`stream()` 方法不动，ChatService 既有测试不受影响。）

- [ ] **Step 5: 跑测试验证通过**

Run: `cd /d/neural-base/backend && mvn test -Dtest=BaguwenAiServiceTest -q`
Expected: 3 tests PASS

- [ ] **Step 6: 提交**

```bash
cd /d/neural-base && git add backend/src/main/java/com/neuralbase/service/BaguwenAiService.java \
  backend/src/test/java/com/neuralbase/config/MockChatConfig.java \
  backend/src/test/java/com/neuralbase/baguwen/BaguwenAiServiceTest.java \
  && git commit -m "feat: baguwen ai structured extraction service"
```

---

### Task 5: BaguwenService — 导入 / 处理 / 重试 / 断点续跑

**Files:**
- Create: `backend/src/main/java/com/neuralbase/service/BaguwenService.java`
- Create: `backend/src/main/java/com/neuralbase/service/BaguwenStartupRunner.java`
- Create: `backend/src/test/java/com/neuralbase/config/MockBaguwenConfig.java`
- Test: `backend/src/test/java/com/neuralbase/baguwen/BaguwenServiceTest.java`

**Interfaces:**
- Consumes: `ArticleCrawler`（Task 2/3）、`ContentExtractor`、`BaguwenAiService`（Task 4）、三个 Baguwen Mapper（Task 1）、`TextChunker`、`VectorStore`、`DocumentMapper`、`KbMapper`、`ObjectMapper`
- Produces: `BaguwenService.importUrls(List<String> urls)` → `List<BaguwenImportTask>`（空列表抛 IAE）
- Produces: `@Async BaguwenService.processTask(Long taskId)`（try/catch → FAILED 标记）；`BaguwenService.process(Long taskId)`（同步核心，失败抛异常）
- Produces: `BaguwenService.retry(Long taskId)` → `BaguwenImportTask`（仅 FAILED 可重试，重置 PENDING）
- Produces: `BaguwenStartupRunner`（`@Profile("!test")`，启动把 PENDING/PROCESSING 重新排队）
- Produces: `mockArticleCrawler`（测试 @Primary `ArticleCrawler`：URL 含 `fail` 抛 CrawlException，否则返回带正文的 HTML）

- [ ] **Step 1: 创建测试假 Crawler 配置**

`src/test/java/com/neuralbase/config/MockBaguwenConfig.java`：
```java
package com.neuralbase.config;

import com.neuralbase.crawler.ArticleCrawler;
import com.neuralbase.crawler.CrawlException;
import com.neuralbase.crawler.CrawledArticle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MockBaguwenConfig {

    /** 假爬虫：URL 含 "fail" 抛异常，否则返回固定正文 HTML。测试 @BeforeEach 无需清静态状态，天然确定。 */
    @Bean
    @Primary
    public ArticleCrawler mockArticleCrawler() {
        return url -> {
            if (url.contains("fail")) {
                throw new CrawlException("mock 爬取失败: " + url);
            }
            return new CrawledArticle("Mock 文章", url,
                "<html><body><article><h1>Mock 文章</h1><p>" + "Mock 正文内容。".repeat(200)
                + "</p></article></body></html>");
        };
    }
}
```

- [ ] **Step 2: 写失败测试（BaguwenServiceTest）**

`baguwen/BaguwenServiceTest.java`：
```java
package com.neuralbase.baguwen;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neuralbase.dto.FavoriteResult;
import com.neuralbase.entity.BaguwenArticle;
import com.neuralbase.entity.BaguwenImportTask;
import com.neuralbase.entity.BaguwenQaPair;
import com.neuralbase.mapper.BaguwenArticleMapper;
import com.neuralbase.mapper.BaguwenImportTaskMapper;
import com.neuralbase.mapper.BaguwenQaPairMapper;
import com.neuralbase.mapper.DocumentMapper;
import com.neuralbase.service.BaguwenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@ActiveProfiles("test")
class BaguwenServiceTest {

    @Autowired private BaguwenService baguwenService;
    @Autowired private BaguwenArticleMapper articleMapper;
    @Autowired private BaguwenImportTaskMapper taskMapper;
    @Autowired private BaguwenQaPairMapper qaPairMapper;
    @Autowired private DocumentMapper documentMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM baguwen_qa_pair");
        jdbcTemplate.update("DELETE FROM baguwen_article");
        jdbcTemplate.update("DELETE FROM baguwen_import_task");
        jdbcTemplate.update("DELETE FROM vector_store");
        jdbcTemplate.update("DELETE FROM document");
    }

    @Test
    void importCreatesPendingTasks() {
        List<BaguwenImportTask> tasks = baguwenService.importUrls(List.of("http://ok/1", "http://ok/2"));

        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).getStatus()).isEqualTo("PENDING");
        assertThat(tasks.get(0).getAttempt()).isZero();
    }

    @Test
    void importRejectsEmptyList() {
        assertThatThrownBy(() -> baguwenService.importUrls(List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void processPersistsArticleAndQaPairs() {
        Long taskId = baguwenService.importUrls(List.of("http://ok/1")).get(0).getId();

        baguwenService.process(taskId);

        BaguwenImportTask task = taskMapper.selectById(taskId);
        assertThat(task.getStatus()).isEqualTo("SUCCESS");
        assertThat(task.getArticleId()).isNotNull();

        BaguwenArticle article = articleMapper.selectById(task.getArticleId());
        assertThat(article.getTitle()).isEqualTo("Mock 文章");
        assertThat(article.getContent()).contains("Mock 正文内容");

        List<BaguwenQaPair> qas = qaPairMapper.selectList(new LambdaQueryWrapper<BaguwenQaPair>()
            .eq(BaguwenQaPair::getArticleId, article.getId()));
        assertThat(qas).hasSize(1);
        assertThat(qas.get(0).getQuestion()).contains("JVM 内存模型");
    }

    @Test
    void failedCrawlMarksTaskFailedAndRetryRequeues() throws InterruptedException {
        Long failId = baguwenService.importUrls(List.of("http://fail/1")).get(0).getId();
        baguwenService.processTask(failId);   // @Async
        awaitStatus(failId, "FAILED");
        BaguwenImportTask failed = taskMapper.selectById(failId);
        assertThat(failed.getErrorMsg()).contains("mock 爬取失败");

        BaguwenImportTask retried = baguwenService.retry(failId);
        assertThat(retried.getStatus()).isEqualTo("PENDING");
        assertThat(retried.getErrorMsg()).isNull();

        // 重试后处理成功
        baguwenService.process(failId);
        assertThat(taskMapper.selectById(failId).getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void retryRejectsNonFailedTask() {
        Long okId = baguwenService.importUrls(List.of("http://ok/1")).get(0).getId();
        baguwenService.process(okId);

        assertThatThrownBy(() -> baguwenService.retry(okId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("仅 FAILED");
    }

    @Test
    void favoriteEmbedsQaTextIntoKb() {
        Long taskId = baguwenService.importUrls(List.of("http://ok/1")).get(0).getId();
        baguwenService.process(taskId);
        Long articleId = taskMapper.selectById(taskId).getArticleId();

        FavoriteResult result = baguwenService.favorite(articleId, 1L);
        assertThat(result.embedded()).isGreaterThan(0);

        Long docId = jdbcTemplate.queryForObject(
            "SELECT id FROM document WHERE kb_id = 1 AND source_url = ?", Long.class, "http://ok/1");
        assertThat(docId).isNotNull();
        Integer vectors = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vector_store WHERE metadata->>'docId' = ?",
            Integer.class, String.valueOf(docId));
        assertThat(vectors).isEqualTo(result.embedded());
    }

    @Test
    void favoriteRejectsMissingKbOrEmptyQa() {
        Long okId = baguwenService.importUrls(List.of("http://ok/1")).get(0).getId();
        baguwenService.process(okId);
        Long articleId = taskMapper.selectById(okId).getArticleId();

        assertThatThrownBy(() -> baguwenService.favorite(articleId, 999L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("知识库不存在");
    }

    private void awaitStatus(Long taskId, String expected) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            BaguwenImportTask t = taskMapper.selectById(taskId);
            if (t != null && expected.equals(t.getStatus())) {
                return;
            }
            Thread.sleep(50);
        }
        fail("任务未在超时内达到 " + expected + "，当前: " + taskMapper.selectById(taskId).getStatus());
    }
}
```

- [ ] **Step 3: 跑测试验证失败**

Run: `cd /d/neural-base/backend && mvn test -Dtest=BaguwenServiceTest -q`
Expected: 编译错误（BaguwenService、FavoriteResult 不存在）→ FAIL

- [ ] **Step 4: 创建 FavoriteResult DTO**

`dto/FavoriteResult.java`：
```java
package com.neuralbase.dto;

public record FavoriteResult(int embedded) {
}
```

- [ ] **Step 5: 实现 BaguwenService**

`service/BaguwenService.java`：
```java
package com.neuralbase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neuralbase.crawler.ArticleCrawler;
import com.neuralbase.crawler.ContentExtractor;
import com.neuralbase.crawler.CrawledArticle;
import com.neuralbase.dto.FavoriteResult;
import com.neuralbase.entity.BaguwenArticle;
import com.neuralbase.entity.BaguwenImportTask;
import com.neuralbase.entity.BaguwenQaPair;
import com.neuralbase.entity.Document;
import com.neuralbase.entity.Kb;
import com.neuralbase.mapper.BaguwenArticleMapper;
import com.neuralbase.mapper.BaguwenImportTaskMapper;
import com.neuralbase.mapper.BaguwenQaPairMapper;
import com.neuralbase.mapper.DocumentMapper;
import com.neuralbase.mapper.KbMapper;
import com.neuralbase.mapper.KbMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BaguwenService {

    private static final int MIN_CONTENT_CHARS = 20;

    private final ArticleCrawler articleCrawler;
    private final ContentExtractor contentExtractor;
    private final BaguwenAiService baguwenAiService;
    private final BaguwenArticleMapper articleMapper;
    private final BaguwenImportTaskMapper taskMapper;
    private final BaguwenQaPairMapper qaPairMapper;
    private final TextChunker textChunker;
    private final VectorStore vectorStore;
    private final DocumentMapper documentMapper;
    private final KbMapper kbMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public BaguwenService(ArticleCrawler articleCrawler, ContentExtractor contentExtractor,
                          BaguwenAiService baguwenAiService, BaguwenArticleMapper articleMapper,
                          BaguwenImportTaskMapper taskMapper, BaguwenQaPairMapper qaPairMapper,
                          TextChunker textChunker, VectorStore vectorStore, DocumentMapper documentMapper,
                          KbMapper kbMapper, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.articleCrawler = articleCrawler;
        this.contentExtractor = contentExtractor;
        this.baguwenAiService = baguwenAiService;
        this.articleMapper = articleMapper;
        this.taskMapper = taskMapper;
        this.qaPairMapper = qaPairMapper;
        this.textChunker = textChunker;
        this.vectorStore = vectorStore;
        this.documentMapper = documentMapper;
        this.kbMapper = kbMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    // ---------- 导入 ----------

    public List<BaguwenImportTask> importUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException("urls 不能为空");
        }
        List<BaguwenImportTask> created = new ArrayList<>();
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            BaguwenImportTask task = new BaguwenImportTask();
            task.setUrl(url.strip());
            task.setStatus("PENDING");
            task.setAttempt(0);
            taskMapper.insert(task);
            created.add(task);
        }
        if (created.isEmpty()) {
            throw new IllegalArgumentException("urls 不能为空");
        }
        return created;
    }

    @Async
    public void processTask(Long taskId) {
        try {
            process(taskId);
        } catch (Exception e) {
            BaguwenImportTask task = taskMapper.selectById(taskId);
            if (task != null) {
                task.setStatus("FAILED");
                task.setErrorMsg(shortMessage(e.getMessage()));
                taskMapper.updateById(task);
            }
        }
    }

    /** 同步核心：成功则任务 SUCCESS 并落库文章+问答对；失败抛异常（由 processTask 标记 FAILED）。 */
    public void process(Long taskId) {
        BaguwenImportTask task = requireTask(taskId);
        if ("SUCCESS".equals(task.getStatus())) {
            return;   // 幂等
        }
        task.setStatus("PROCESSING");
        task.setAttempt(task.getAttempt() + 1);
        task.setErrorMsg(null);
        taskMapper.updateById(task);

        CrawledArticle crawled = articleCrawler.fetch(task.getUrl());
        String content = contentExtractor.extract(crawled.html());
        if (content.length() < MIN_CONTENT_CHARS) {
            throw new IllegalStateException("未能从页面提取到有效正文: " + task.getUrl());
        }
        BaguwenAiService.ExtractionResult result = baguwenAiService.extract(content, crawled.title());

        // 崩溃重跑清理：若之前残留了文章，删掉重建（qa_pair 级联删除）
        if (task.getArticleId() != null) {
            articleMapper.deleteById(task.getArticleId());
        }
        BaguwenArticle article = new BaguwenArticle();
        article.setTitle(limit(crawled.title(), 255));
        article.setUrl(task.getUrl());
        article.setContent(content);
        article.setTags(toJsonArray(result.tags()));
        articleMapper.insertWithTags(article);
        task.setArticleId(article.getId());
        taskMapper.updateById(task);   // 先落 article 链接，崩溃重跑可据此清理

        saveQaPairs(article.getId(), result.qaPairs());
        task.setStatus("SUCCESS");
        taskMapper.updateById(task);
    }

    private void saveQaPairs(Long articleId, List<BaguwenAiService.QaPair> pairs) {
        for (int i = 0; i < pairs.size(); i++) {
            BaguwenAiService.QaPair p = pairs.get(i);
            BaguwenQaPair qa = new BaguwenQaPair();
            qa.setArticleId(articleId);
            qa.setQuestion(p.question());
            qa.setAnswer(p.answer());
            qa.setDifficulty(p.difficulty() == null || p.difficulty().isBlank() ? "简单" : p.difficulty());
            qa.setSortOrder(i);
            qaPairMapper.insert(qa);
        }
    }

    // ---------- 重试 ----------

    public BaguwenImportTask retry(Long taskId) {
        BaguwenImportTask task = requireTask(taskId);
        if (!"FAILED".equals(task.getStatus())) {
            throw new IllegalArgumentException("仅 FAILED 任务可重试，当前状态: " + task.getStatus());
        }
        task.setStatus("PENDING");
        task.setErrorMsg(null);
        taskMapper.updateById(task);
        return task;
    }

    // ---------- 查询 ----------

    public List<BaguwenImportTask> tasks(String status) {
        return taskMapper.selectList(new LambdaQueryWrapper<BaguwenImportTask>()
            .eq(status != null && !status.isBlank(), BaguwenImportTask::getStatus, status)
            .orderByDesc(BaguwenImportTask::getId));
    }

    public List<BaguwenQaPair> qaPairs(Long articleId) {
        return qaPairMapper.selectList(new LambdaQueryWrapper<BaguwenQaPair>()
            .eq(BaguwenQaPair::getArticleId, articleId)
            .orderByAsc(BaguwenQaPair::getSortOrder));
    }

    // ---------- 辅助 ----------

    private BaguwenImportTask requireTask(Long id) {
        BaguwenImportTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }
        return task;
    }

    private String toJsonArray(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags == null ? List.of() : tags);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String shortMessage(String msg) {
        return msg == null ? "未知错误" : (msg.length() > 500 ? msg.substring(0, 500) : msg);
    }

    private String limit(String s, int max) {
        if (s == null) {
            return "未命名";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
```

- [ ] **Step 6: 创建 BaguwenStartupRunner（断点续跑）**

`service/BaguwenStartupRunner.java`：
```java
package com.neuralbase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neuralbase.entity.BaguwenImportTask;
import com.neuralbase.mapper.BaguwenImportTaskMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/** 启动断点续跑：把上次崩溃遗留的 PENDING/PROCESSING 任务重新排队（@Async 进程内重启丢失的补偿）。测试排除，避免共享库干扰。 */
@Component
@Profile("!test")
public class BaguwenStartupRunner implements ApplicationRunner {

    private final BaguwenImportTaskMapper taskMapper;
    private final BaguwenService baguwenService;

    public BaguwenStartupRunner(BaguwenImportTaskMapper taskMapper, BaguwenService baguwenService) {
        this.taskMapper = taskMapper;
        this.baguwenService = baguwenService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<BaguwenImportTask> pending = taskMapper.selectList(new LambdaQueryWrapper<BaguwenImportTask>()
            .in(BaguwenImportTask::getStatus, "PENDING", "PROCESSING"));
        for (BaguwenImportTask task : pending) {
            baguwenService.processTask(task.getId());   // 跨 Bean 调用走代理，@Async 生效
        }
    }
}
```

- [ ] **Step 7: 跑测试验证通过**

Run: `cd /d/neural-base/backend && mvn test -Dtest=BaguwenServiceTest -q`
Expected: 7 tests PASS（`process` 同步调用确定性；`processTask` 异步用 `awaitStatus` 轮询）

- [ ] **Step 8: 提交**

```bash
cd /d/neural-base && git add backend/src/main/java/com/neuralbase/service/BaguwenService.java \
  backend/src/main/java/com/neuralbase/service/BaguwenStartupRunner.java \
  backend/src/main/java/com/neuralbase/dto/FavoriteResult.java \
  backend/src/test/java/com/neuralbase/config/MockBaguwenConfig.java \
  backend/src/test/java/com/neuralbase/baguwen/BaguwenServiceTest.java \
  && git commit -m "feat: baguwen import/process/retry pipeline with startup resume"
```

---

### Task 6: BaguwenService — 查询与收藏

**Files:**
- Modify: `backend/src/main/java/com/neuralbase/service/BaguwenService.java`（加 list/getArticle/detail/delete/tagCounts 方法）
- Create: `backend/src/main/java/com/neuralbase/dto/BaguwenArticleView.java`
- Create: `backend/src/main/java/com/neuralbase/dto/BaguwenDetail.java`
- Create: `backend/src/main/java/com/neuralbase/dto/QaPairView.java`
- Create: `backend/src/main/java/com/neuralbase/dto/BaguwenTagCount.java`
- Modify: `backend/src/test/java/com/neuralbase/baguwen/BaguwenServiceTest.java`（加查询/删除测试）

**Interfaces:**
- Consumes: Task 5 的 `BaguwenService`（构造器已含 `JdbcTemplate`，本任务无需改构造器）
- Produces: `Page<BaguwenArticleView> list(String tag, String keyword, long page, long size)`
- Produces: `BaguwenArticle getArticle(Long id)`（不存在抛 IAE）；`BaguwenDetail detail(Long id)`；`List<BaguwenTagCount> tagCounts()`（JdbcTemplate jsonb 展开聚合，`ORDER BY cnt DESC, tag ASC` 保证并列确定序）；`void delete(Long id)`（FK 级联 qa_pair，任务 article_id 由 FK SET NULL 置空）

- [ ] **Step 1: 确认 JdbcTemplate 已注入**（Task 5 的 BaguwenService 构造器已含 `JdbcTemplate`，本任务直接复用，无需改构造器）

- [ ] **Step 2: 创建查询 DTO**

`dto/BaguwenArticleView.java`：
```java
package com.neuralbase.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BaguwenArticleView(Long id, String title, String url, List<String> tags,
                                 OffsetDateTime createdAt) {
}
```

`dto/QaPairView.java`：
```java
package com.neuralbase.dto;

import com.neuralbase.entity.BaguwenQaPair;

public record QaPairView(Long id, String question, String answer, String difficulty) {
    public static QaPairView from(BaguwenQaPair q) {
        return new QaPairView(q.getId(), q.getQuestion(), q.getAnswer(), q.getDifficulty());
    }
}
```

`dto/BaguwenDetail.java`：
```java
package com.neuralbase.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BaguwenDetail(Long id, String title, String url, String content,
                            List<String> tags, OffsetDateTime createdAt, List<QaPairView> qaPairs) {
}
```

`dto/BaguwenTagCount.java`：
```java
package com.neuralbase.dto;

public record BaguwenTagCount(String tag, long count) {
}
```

- [ ] **Step 3: 在 BaguwenService 加查询与删除方法**

在 Task 5 的 `BaguwenService` 中追加以下方法（`parseTags` 用 `objectMapper.readValue` 解析 tags 字符串，解析失败返回空列表）：

```java
    private static final long MAX_PAGE = 100;

    public Page<BaguwenArticleView> list(String tag, String keyword, long page, long size) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE);
        LambdaQueryWrapper<BaguwenArticle> w = new LambdaQueryWrapper<>();
        if (tag != null && !tag.isBlank()) {
            w.apply("tags @> {0}::jsonb", "[\"" + tag + "\"]");
        }
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(BaguwenArticle::getTitle, keyword)
                .or().like(BaguwenArticle::getContent, keyword)
                .or().apply("EXISTS (SELECT 1 FROM baguwen_qa_pair q WHERE q.article_id = baguwen_article.id "
                    + "AND (q.question LIKE {0} OR q.answer LIKE {1}))",
                    "%" + keyword + "%", "%" + keyword + "%"));
        }
        w.orderByDesc(BaguwenArticle::getCreatedAt);
        Page<BaguwenArticle> p = articleMapper.selectPage(new Page<>(safePage, safeSize), w);
        Page<BaguwenArticleView> view = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        view.setRecords(p.getRecords().stream()
            .map(a -> new BaguwenArticleView(a.getId(), a.getTitle(), a.getUrl(),
                parseTags(a.getTags()), a.getCreatedAt()))
            .toList());
        return view;
    }

    public BaguwenArticle getArticle(Long id) {
        BaguwenArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new IllegalArgumentException("文章不存在: " + id);
        }
        return article;
    }

    public BaguwenDetail detail(Long id) {
        BaguwenArticle article = getArticle(id);
        List<QaPairView> qaViews = qaPairs(id).stream().map(QaPairView::from).toList();
        return new BaguwenDetail(article.getId(), article.getTitle(), article.getUrl(),
            article.getContent(), parseTags(article.getTags()), article.getCreatedAt(), qaViews);
    }

    public List<BaguwenTagCount> tagCounts() {
        return jdbcTemplate.queryForList(
            "SELECT jsonb_array_elements_text(tags) AS tag, count(*) AS cnt "
            + "FROM baguwen_article GROUP BY tag ORDER BY cnt DESC, tag ASC")
            .stream()
            .map(m -> new BaguwenTagCount((String) m.get("tag"), ((Number) m.get("cnt")).longValue()))
            .toList();
    }

    public void delete(Long id) {
        BaguwenArticle article = getArticle(id);
        // qa_pair 由 FK ON DELETE CASCADE 删除；import_task.article_id 由 FK ON DELETE SET NULL 置空
        articleMapper.deleteById(article.getId());
    }

    private List<String> parseTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
```

补充 import：`com.baomidou.mybatisplus.extension.plugins.pagination.Page`、`com.neuralbase.dto.BaguwenArticleView`、`com.neuralbase.dto.BaguwenDetail`、`com.neuralbase.dto.BaguwenTagCount`、`com.neuralbase.dto.QaPairView`、`org.springframework.jdbc.core.JdbcTemplate`。

- [ ] **Step 4: 扩展 BaguwenServiceTest 加查询/删除测试**

在 `BaguwenServiceTest` 中追加：
```java
    @Test
    void listFiltersByKeywordMatchingQaPair() {
        Long taskId = baguwenService.importUrls(List.of("http://ok/1")).get(0).getId();
        baguwenService.process(taskId);
        Long articleId = taskMapper.selectById(taskId).getArticleId();

        // keyword 命中问答对 question（"JVM 内存模型"），但 title/content 不含该词
        var page = baguwenService.list(null, "JVM 内存模型", 1, 10);
        assertThat(page.getRecords()).extracting(BaguwenArticleView::id)
            .contains(articleId);

        // keyword 完全不匹配 → 空
        assertThat(baguwenService.list(null, "不存在的词xyz", 1, 10).getRecords()).isEmpty();
    }

    @Test
    void listFiltersByTag() {
        Long taskId = baguwenService.importUrls(List.of("http://ok/1")).get(0).getId();
        baguwenService.process(taskId);

        assertThat(baguwenService.list("Java", null, 1, 10).getRecords()).hasSize(1);
        assertThat(baguwenService.list("Python", null, 1, 10).getRecords()).isEmpty();
    }

    @Test
    void tagCountsAggregates() {
        Long taskId = baguwenService.importUrls(List.of("http://ok/1")).get(0).getId();
        baguwenService.process(taskId);

        List<BaguwenTagCount> counts = baguwenService.tagCounts();
        assertThat(counts).extracting(BaguwenTagCount::tag).contains("Java", "JVM");
    }

    @Test
    void deleteRemovesArticleAndPairsAndUnlinksTask() {
        Long taskId = baguwenService.importUrls(List.of("http://ok/1")).get(0).getId();
        baguwenService.process(taskId);
        Long articleId = taskMapper.selectById(taskId).getArticleId();

        baguwenService.delete(articleId);

        assertThat(articleMapper.selectById(articleId)).isNull();
        assertThat(qaPairMapper.selectCount(new LambdaQueryWrapper<BaguwenQaPair>()
            .eq(BaguwenQaPair::getArticleId, articleId))).isZero();
        assertThat(taskMapper.selectById(taskId).getArticleId()).isNull();
    }

    @Test
    void detailReturnsArticleWithQaPairs() {
        Long taskId = baguwenService.importUrls(List.of("http://ok/1")).get(0).getId();
        baguwenService.process(taskId);
        Long articleId = taskMapper.selectById(taskId).getArticleId();

        BaguwenDetail detail = baguwenService.detail(articleId);

        assertThat(detail.tags()).contains("Java");
        assertThat(detail.qaPairs()).hasSize(1);
        assertThat(detail.qaPairs().get(0).question()).contains("JVM 内存模型");
    }
```
（需 import `com.neuralbase.dto.BaguwenArticleView`、`com.neuralbase.dto.BaguwenDetail`、`com.neuralbase.dto.BaguwenTagCount`、`com.neuralbase.entity.BaguwenArticle`。）

- [ ] **Step 5: 跑测试验证通过**

Run: `cd /d/neural-base/backend && mvn test -Dtest=BaguwenServiceTest -q`
Expected: 12 tests PASS（原 7 + 新增 5）

- [ ] **Step 6: 提交**

```bash
cd /d/neural-base && git add backend/src/main/java/com/neuralbase/service/BaguwenService.java \
  backend/src/main/java/com/neuralbase/dto/BaguwenArticleView.java \
  backend/src/main/java/com/neuralbase/dto/BaguwenDetail.java \
  backend/src/main/java/com/neuralbase/dto/QaPairView.java \
  backend/src/main/java/com/neuralbase/dto/BaguwenTagCount.java \
  backend/src/test/java/com/neuralbase/baguwen/BaguwenServiceTest.java \
  && git commit -m "feat: baguwen query and favorite endpoints backing"
```

---

### Task 7: BaguwenController — REST API

**Files:**
- Create: `backend/src/main/java/com/neuralbase/dto/BaguwenImportRequest.java`
- Create: `backend/src/main/java/com/neuralbase/dto/BaguwenFavoriteRequest.java`
- Create: `backend/src/main/java/com/neuralbase/controller/BaguwenController.java`
- Test: `backend/src/test/java/com/neuralbase/baguwen/BaguwenControllerTest.java`

**Interfaces:**
- Consumes: `BaguwenService` 全部方法（Task 5/6）
- Produces: REST 端点——`POST /api/baguwen/import`、`GET /api/baguwen/tasks`、`POST /api/baguwen/tasks/{id}/retry`、`GET /api/baguwen`、`GET /api/baguwen/{id}`、`GET /api/baguwen/tags`、`DELETE /api/baguwen/{id}`、`POST /api/baguwen/{id}/favorite`

- [ ] **Step 1: 创建请求 DTO**

`dto/BaguwenImportRequest.java`：
```java
package com.neuralbase.dto;

import java.util.List;

public record BaguwenImportRequest(List<String> urls) {
}
```

`dto/BaguwenFavoriteRequest.java`：
```java
package com.neuralbase.dto;

public record BaguwenFavoriteRequest(Long kbId) {
}
```

- [ ] **Step 2: 实现 Controller**

`controller/BaguwenController.java`：
```java
package com.neuralbase.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.neuralbase.common.Result;
import com.neuralbase.dto.BaguwenArticleView;
import com.neuralbase.dto.BaguwenDetail;
import com.neuralbase.dto.BaguwenFavoriteRequest;
import com.neuralbase.dto.BaguwenImportRequest;
import com.neuralbase.dto.BaguwenTagCount;
import com.neuralbase.dto.FavoriteResult;
import com.neuralbase.entity.BaguwenImportTask;
import com.neuralbase.service.BaguwenService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/baguwen")
public class BaguwenController {

    private final BaguwenService baguwenService;

    public BaguwenController(BaguwenService baguwenService) {
        this.baguwenService = baguwenService;
    }

    @PostMapping("/import")
    public Result<List<BaguwenImportTask>> importUrls(@RequestBody BaguwenImportRequest req) {
        List<BaguwenImportTask> tasks = baguwenService.importUrls(req.urls());
        tasks.forEach(t -> baguwenService.processTask(t.getId()));   // 跨 Bean 调用走代理，@Async 生效
        return Result.ok(tasks);
    }

    @GetMapping("/tasks")
    public Result<List<BaguwenImportTask>> tasks(@RequestParam(required = false) String status) {
        return Result.ok(baguwenService.tasks(status));
    }

    @PostMapping("/tasks/{id}/retry")
    public Result<BaguwenImportTask> retry(@PathVariable Long id) {
        BaguwenImportTask task = baguwenService.retry(id);
        baguwenService.processTask(task.getId());
        return Result.ok(task);
    }

    @GetMapping
    public Result<Page<BaguwenArticleView>> list(@RequestParam(required = false) String tag,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size) {
        return Result.ok(baguwenService.list(tag, keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<BaguwenDetail> detail(@PathVariable Long id) {
        return Result.ok(baguwenService.detail(id));
    }

    @GetMapping("/tags")
    public Result<List<BaguwenTagCount>> tags() {
        return Result.ok(baguwenService.tagCounts());
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        baguwenService.delete(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/favorite")
    public Result<FavoriteResult> favorite(@PathVariable Long id, @RequestBody BaguwenFavoriteRequest req) {
        return Result.ok(baguwenService.favorite(id, req.kbId()));
    }
}
```

- [ ] **Step 3: 写 HTTP 层测试**

`baguwen/BaguwenControllerTest.java`：
```java
package com.neuralbase.baguwen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.entity.BaguwenImportTask;
import com.neuralbase.mapper.BaguwenArticleMapper;
import com.neuralbase.mapper.BaguwenImportTaskMapper;
import com.neuralbase.service.BaguwenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BaguwenControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BaguwenService baguwenService;
    @Autowired private BaguwenImportTaskMapper taskMapper;
    @Autowired private BaguwenArticleMapper articleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM baguwen_qa_pair");
        jdbcTemplate.update("DELETE FROM baguwen_article");
        jdbcTemplate.update("DELETE FROM baguwen_import_task");
        jdbcTemplate.update("DELETE FROM vector_store");
        jdbcTemplate.update("DELETE FROM document");
    }

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asText();
    }

    /** 同步处理出文章，返回 articleId（HTTP 断言用固定数据，避免异步竞态）。 */
    private Long seedArticle() {
        BaguwenImportTask task = baguwenService.importUrls(List.of("http://ok/1")).get(0);
        baguwenService.process(task.getId());
        return taskMapper.selectById(task.getId()).getArticleId();
    }

    @Test
    void importRequiresAuth() throws Exception {
        mvc.perform(post("/api/baguwen/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"urls\":[\"http://ok/1\"]}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void importReturnsPendingTasks() throws Exception {
        String tk = token();
        mvc.perform(post("/api/baguwen/import")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"urls\":[\"http://ok/1\",\"http://ok/2\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    void listReturnsSeededArticles() throws Exception {
        seedArticle();
        String tk = token();
        mvc.perform(get("/api/baguwen")
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[0].title").value("Mock 文章"))
            .andExpect(jsonPath("$.data.records[0].tags[0]").value("Java"));
    }

    @Test
    void detailReturnsArticleWithQaPairs() throws Exception {
        Long articleId = seedArticle();
        String tk = token();
        mvc.perform(get("/api/baguwen/" + articleId)
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.qaPairs[0].question").value("什么是 JVM 内存模型？"))
            .andExpect(jsonPath("$.data.tags[0]").value("Java"));
    }

    @Test
    void tagsEndpointAggregates() throws Exception {
        seedArticle();
        String tk = token();
        mvc.perform(get("/api/baguwen/tags")
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].tag").value("Java"));   // cnt 并列时按 tag ASC 保证确定序
    }

    @Test
    void favoriteEmbedsIntoKb() throws Exception {
        Long articleId = seedArticle();
        String tk = token();
        mvc.perform(post("/api/baguwen/" + articleId + "/favorite")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kbId\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.embedded").isNumber());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM document WHERE kb_id = 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void deleteRemovesArticle() throws Exception {
        Long articleId = seedArticle();
        String tk = token();
        mvc.perform(delete("/api/baguwen/" + articleId)
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk());
        assertThat(articleMapper.selectById(articleId)).isNull();
    }

    @Test
    void retryEndpointRequeuesFailedTask() throws Exception {
        String tk = token();
        BaguwenImportTask task = baguwenService.importUrls(List.of("http://fail/1")).get(0);
        baguwenService.processTask(task.getId());
        awaitStatus(task.getId(), "FAILED");

        mvc.perform(post("/api/baguwen/tasks/" + task.getId() + "/retry")
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    private void awaitStatus(Long taskId, String expected) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (expected.equals(taskMapper.selectById(taskId).getStatus())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("任务未在超时内达到 " + expected);
    }
}
```

- [ ] **Step 4: 跑测试验证通过**

Run: `cd /d/neural-base/backend && mvn test -Dtest=BaguwenControllerTest -q`
Expected: 8 tests PASS

- [ ] **Step 5: 提交**

```bash
cd /d/neural-base && git add backend/src/main/java/com/neuralbase/dto/BaguwenImportRequest.java \
  backend/src/main/java/com/neuralbase/dto/BaguwenFavoriteRequest.java \
  backend/src/main/java/com/neuralbase/controller/BaguwenController.java \
  backend/src/test/java/com/neuralbase/baguwen/BaguwenControllerTest.java \
  && git commit -m "feat: baguwen rest api"
```

---

### Task 8: 全量验证 + 进度记录

**Files:**
- Modify: `backend/docs/PROGRESS.md`（更新 Phase 4 状态与测试数）
- Modify: `docs/neuralbase-project-status.md` 记忆（若适用，会话外）

**Interfaces:**
- Consumes: 全部

- [ ] **Step 1: 全量跑测试**

Run: `cd /d/neural-base/backend && export DB_PASSWORD='root' && mvn test -q`
Expected: BUILD SUCCESS，全绿（既有 35 + 新增约 28+）

- [ ] **Step 2: 可选真机冒烟（需 DeepSeek key + 网络）**

Run: 用本机 PG，`mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`，登录后 `POST /api/baguwen/import` 传一个真实 URL（如 `https://javaguide.cn/java/jvm/jvm-memory-layout.html`），轮询 tasks 至 SUCCESS，`GET /api/baguwen/{id}` 验证问答对，`POST /{id}/favorite` 验证收藏后 `POST /api/document/search` 可检索到。若页面为 SSR，Jsoup 直达；若为 JS 渲染需先 `playwright install chromium`。
Expected: 端到端 SUCCESS，无浏览器时可跳过此步并记录

- [ ] **Step 3: 更新 PROGRESS.md**

更新 `docs/PROGRESS.md`：
- Roadmap 表 Phase 4 状态 `❌` → `✅`（后端；前端仍 ❌）
- 八股文库模块 3️⃣ 各功能点：URL 输入/智能爬取/AI 结构化/分类标签 → ✅；阅读模式/前端 ❌（前端未开始）
- 测试数行更新（35/35 → 66/66，Phase 4 新增 31：ContentExtractor 2 + JsoupCrawler 2 + CompositeCrawler 4 + BaguwenAiService 3 + BaguwenService 12 + BaguwenController 8）
- 架构表：爬虫引擎 Playwright+Jsoup → ✅；任务调度 Quartz 仍 ❌

- [ ] **Step 4: 提交**

```bash
cd /d/neural-base && git add docs/PROGRESS.md && git commit -m "docs: phase4 baguwen progress"
```
