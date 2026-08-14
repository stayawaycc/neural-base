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
        // 隔离:兄弟测试会遗留 RUNNING 任务与新闻行,先清空
        crawlTaskMapper.delete(null);
        dailyNewsMapper.delete(null);
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
    void markReadAndListFilters() {
        service.startCrawl();
        service.runCrawlNow();
        var news = dailyNewsMapper.selectList(null);
        service.markRead(news.get(0).getId(), true);
        // 3 条中 1 条已读 → 未读筛选返回 2 条,且全部未读
        var unread = service.list(null, true);
        assertThat(unread).hasSize(2);
        assertThat(unread).allMatch(n -> !Boolean.TRUE.equals(n.getIsRead()));
    }
}
