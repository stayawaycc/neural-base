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

    @BeforeEach
    void cleanUp() {
        // 隔离:兄弟测试会遗留 RUNNING 任务与新闻行,先清空
        crawlTaskMapper.delete(null);
        dailyNewsMapper.delete(null);
    }

    @Test
    void crawlCreatesDeduplicatedNewsAndTaskSucceeds() {
        service.startCrawl();
        service.runCrawlNow();
        assertThat(dailyNewsMapper.selectList(null)).hasSize(3); // 2 + 1 假件

        service.startCrawl();
        service.runCrawlNow(); // 第二轮去重:不新增
        assertThat(dailyNewsMapper.selectList(null)).hasSize(3);

        CrawlTask latest = crawlTaskMapper.selectList(
            new LambdaQueryWrapper<CrawlTask>().orderByDesc(CrawlTask::getId).last("LIMIT 1")).get(0);
        assertThat(latest.getStatus()).isEqualTo("SUCCESS");
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
