package com.neuralbase.news;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neuralbase.config.MockEmbeddingConfig;
import com.neuralbase.config.MockNewsConfig;
import com.neuralbase.entity.CrawlTask;
import com.neuralbase.mapper.CrawlTaskMapper;
import com.neuralbase.mapper.DailyNewsMapper;
import com.neuralbase.service.DailyNewsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        // 隔离:兄弟测试会遗留 RUNNING 任务、新闻行与收藏文档,先清空
        crawlTaskMapper.delete(null);
        dailyNewsMapper.delete(null);
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'docId' IN "
            + "(SELECT id::text FROM document WHERE file_type = 'NEWS')");
        jdbcTemplate.update("DELETE FROM document WHERE file_type = 'NEWS'");
        jdbcTemplate.update("UPDATE crawler_source SET enabled = TRUE");
    }

    @Test
    void disabledSourceIsSkipped() {
        jdbcTemplate.update("UPDATE crawler_source SET enabled = FALSE WHERE name = 'GitHub Trending'");
        jdbcTemplate.update("UPDATE crawler_source SET enabled = FALSE WHERE name = 'Hacker News'");
        service.startCrawl();
        service.runCrawlNow();
        assertThat(dailyNewsMapper.selectList(null)).isEmpty();
    }

    @Test
    void nonAiItemsFromFilteredSourcesAreDropped() {
        service.startCrawl();
        service.runCrawlNow();
        var news = dailyNewsMapper.selectList(null);
        // 2 条 GitHub 假件(上游已 AI,不参与过滤) + 1 条 HN AI 条目;HN 非 AI 假件被丢弃
        assertThat(news).hasSize(3);
        assertThat(news).noneMatch(n -> n.getTitle().equals("Plain weather forecast"));
    }

    @Test
    void crawlCreatesDeduplicatedNewsAndTaskSucceeds() {
        service.startCrawl();
        service.runCrawlNow();
        assertThat(dailyNewsMapper.selectList(null)).hasSize(3); // 2 + 1 假件
        // 假 ChatModel 返回非 JSON → 单篇摘要失败仍入库(summary null),任务仍 SUCCESS
        assertThat(dailyNewsMapper.selectList(null)).allMatch(n -> n.getSummary() == null);

        service.startCrawl();
        service.runCrawlNow(); // 第二轮去重:不新增
        assertThat(dailyNewsMapper.selectList(null)).hasSize(3);

        CrawlTask latest = crawlTaskMapper.selectList(
            new LambdaQueryWrapper<CrawlTask>().orderByDesc(CrawlTask::getId).last("LIMIT 1")).get(0);
        assertThat(latest.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void favoriteEmbedsAndRejectsDuplicate() {
        service.startCrawl();
        service.runCrawlNow();
        var news = dailyNewsMapper.selectList(null);
        long id = news.get(0).getId();
        String url = news.get(0).getUrl();

        service.favorite(id, 1L);
        Long docId = jdbcTemplate.queryForObject(
            "SELECT id FROM document WHERE file_type = 'NEWS' AND source_url = ? LIMIT 1",
            Long.class, url);
        assertThat(docId).isNotNull();
        Integer vectors = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vector_store WHERE metadata->>'docId' = ?",
            Integer.class, String.valueOf(docId));
        assertThat(vectors).isGreaterThan(0);

        assertThatThrownBy(() -> service.favorite(id, 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("已收藏");
    }

    @Test
    void secondCrawlRejectedWhileRunning() {
        service.startCrawl();
        assertThatThrownBy(() -> service.startCrawl())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("进行中");
    }

    @Test
    void cleanupRemovesNonAiRowsOnlyFromGeneralSources() {
        Long trendingId = sourceId("GitHub Trending");
        Long hnId = sourceId("Hacker News");
        Long infoqId = sourceId("InfoQ");
        Long jqzxId = sourceId("机器之心");
        jdbcTemplate.update("INSERT INTO daily_news (source_id, url, title, is_read) VALUES (?, 'https://t/plain', 'Plain 维护笔记', FALSE)", trendingId);
        jdbcTemplate.update("INSERT INTO daily_news (source_id, url, title, is_read) VALUES (?, 'https://h/plain', 'Weather forecast', FALSE)", hnId);
        jdbcTemplate.update("INSERT INTO daily_news (source_id, url, title, is_read) VALUES (?, 'https://i/ai', 'LLM 评测', FALSE)", infoqId);
        jdbcTemplate.update("INSERT INTO daily_news (source_id, url, title, is_read) VALUES (?, 'https://i/plain', 'How to grow tomatoes', FALSE)", infoqId);
        jdbcTemplate.update("INSERT INTO daily_news (source_id, url, title, is_read) VALUES (?, 'https://j/plain', '本周招聘汇总', FALSE)", jqzxId);

        long deleted = service.cleanupNonAi();
        assertThat(deleted).isEqualTo(2); // HN + InfoQ 的非 AI 条目
        var remaining = dailyNewsMapper.selectList(null);
        assertThat(remaining).hasSize(3);
        // GitHub Trending 已为 topic:ai 上游(AI 原生),不在清理范围,避免误删 ComfyUI 类标题
        assertThat(remaining).anyMatch(n -> n.getTitle().equals("Plain 维护笔记"));
        assertThat(service.cleanupNonAi()).isZero(); // 幂等
    }

    private Long sourceId(String name) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM crawler_source WHERE name = ?", Long.class, name);
    }

    @Test
    void markReadAndListFilters() {
        service.startCrawl();
        service.runCrawlNow();
        var news = dailyNewsMapper.selectList(null);
        service.markRead(news.get(0).getId(), true);
        // 3 条中 1 条已读 → 未读筛选返回 2 条,且全部未读
        var unread = service.list(null, true, null, null, 1, 20);
        assertThat(unread.items()).hasSize(2);
        assertThat(unread.items()).allMatch(n -> !Boolean.TRUE.equals(n.getIsRead()));
    }

    @Test
    void listPaginatesAndReportsTotal() {
        for (int i = 1; i <= 25; i++) {
            jdbcTemplate.update(
                "INSERT INTO daily_news (source_id, url, title, is_read) VALUES (NULL, ?, ?, FALSE)",
                "https://example.com/p" + i, "AI 新闻 " + i);
        }
        var page = service.list(null, null, null, null, 2, 10);
        assertThat(page.items()).hasSize(10);
        assertThat(page.total()).isEqualTo(25);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
    }

    @Test
    void listClampsPageAndSize() {
        jdbcTemplate.update(
            "INSERT INTO daily_news (source_id, url, title, is_read) VALUES (NULL, 'https://x/1', 'AI 1', FALSE)");
        var page = service.list(null, null, null, null, 0, 999);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(100);
    }
}
