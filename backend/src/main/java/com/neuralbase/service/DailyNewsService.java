package com.neuralbase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neuralbase.entity.CrawlTask;
import com.neuralbase.entity.CrawlerSource;
import com.neuralbase.entity.DailyNews;
import com.neuralbase.entity.Kb;
import com.neuralbase.mapper.CrawlTaskMapper;
import com.neuralbase.mapper.CrawlerSourceMapper;
import com.neuralbase.mapper.DailyNewsMapper;
import com.neuralbase.mapper.KbMapper;
import com.neuralbase.news.NewsFetcher;
import com.neuralbase.news.NewsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DailyNewsService {

    private static final Logger log = LoggerFactory.getLogger(DailyNewsService.class);
    private static final String TASK_TYPE = "DAILY_NEWS";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final List<NewsFetcher> fetchers;
    private final DailyNewsMapper dailyNewsMapper;
    private final CrawlTaskMapper crawlTaskMapper;
    private final CrawlerSourceMapper crawlerSourceMapper;
    private final NewsAiService newsAiService;
    private final DocumentService documentService;
    private final KbMapper kbMapper;
    private final JdbcTemplate jdbcTemplate;

    public DailyNewsService(List<NewsFetcher> fetchers, DailyNewsMapper dailyNewsMapper,
                            CrawlTaskMapper crawlTaskMapper, CrawlerSourceMapper crawlerSourceMapper,
                            NewsAiService newsAiService, DocumentService documentService,
                            KbMapper kbMapper, JdbcTemplate jdbcTemplate) {
        this.fetchers = fetchers;
        this.dailyNewsMapper = dailyNewsMapper;
        this.crawlTaskMapper = crawlTaskMapper;
        this.crawlerSourceMapper = crawlerSourceMapper;
        this.newsAiService = newsAiService;
        this.documentService = documentService;
        this.kbMapper = kbMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 手动/定时入口:原子创建 RUNNING 任务(WHERE NOT EXISTS),无 RUNNING 任务时插入并返回,否则抛异常。 */
    public void startCrawl() {
        int inserted = jdbcTemplate.update(
            "INSERT INTO crawl_task (type, status, progress, created_at) "
            + "SELECT ?, ?, 0, now() WHERE NOT EXISTS "
            + "(SELECT 1 FROM crawl_task WHERE type = ? AND status = ?)",
            TASK_TYPE, STATUS_RUNNING, TASK_TYPE, STATUS_RUNNING);
        if (inserted == 0) {
            throw new IllegalArgumentException("已有爬取任务进行中");
        }
    }

    @Async
    public void crawlAsync() {
        runCrawlNow();
    }

    /** 同步核心流程(测试直接调用;crawlAsync 仅包异步壳)。 */
    public void runCrawlNow() {
        CrawlTask task = latestTaskByStatus(STATUS_RUNNING);
        try {
            for (NewsFetcher fetcher : fetchers) {
                List<NewsItem> items;
                try {
                    items = fetcher.fetch();
                } catch (Exception e) {
                    log.warn("新闻源爬取失败: {}", e.getMessage());
                    continue; // 单源失败不影响整轮
                }
                Long sourceId = sourceIdOf(items);
                for (NewsItem item : items) {
                    if (exists(item.url())) continue; // 去重
                    DailyNews n = new DailyNews();
                    n.setSourceId(sourceId);
                    n.setUrl(item.url());
                    n.setTitle(item.title());
                    n.setIsRead(false);
                    dailyNewsMapper.insert(n);
                }
                // 仅该源实际成功才更新 last_crawl(语义准确)
                if (!items.isEmpty()) {
                    touchSource(sourceId);
                }
            }
            if (task != null) {
                updateTask(task.getId(), STATUS_RUNNING, 50, null);
            }

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
            if (task != null) {
                updateTask(task.getId(), STATUS_SUCCESS, 100, null);
            }
            log.info("新闻爬取完成:{} 条待汇总,{} 条处理", pending.size(), done);
        } catch (Exception e) {
            if (task != null) {
                updateTask(task.getId(), STATUS_FAILED, 0, e.getMessage());
            }
            log.error("新闻爬取失败", e);
        }
    }

    public List<DailyNews> list(String category, Boolean unreadOnly) {
        List<DailyNews> news = dailyNewsMapper.selectList(
            new LambdaQueryWrapper<DailyNews>()
                .eq(category != null && !category.isBlank(), DailyNews::getCategory, category)
                .eq(Boolean.TRUE.equals(unreadOnly), DailyNews::getIsRead, false)
                .orderByDesc(DailyNews::getCreatedAt)
                .last("LIMIT 200"));
        // 透出来源名(前端卡片展示)
        Map<Long, String> sourceNames = new HashMap<>();
        for (CrawlerSource s : crawlerSourceMapper.selectList(null)) {
            sourceNames.put(s.getId(), s.getName());
        }
        for (DailyNews n : news) {
            n.setSourceName(n.getSourceId() == null ? null : sourceNames.get(n.getSourceId()));
        }
        return news;
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

    /** 收藏进知识库:标题+摘要拼接 → 通用嵌入管线(fileType=NEWS);按 sourceUrl 去重,重复收藏报 400。 */
    public void favorite(Long id, Long kbId) {
        DailyNews n = dailyNewsMapper.selectById(id);
        if (n == null) throw new IllegalArgumentException("新闻不存在: " + id);
        Kb kb = kbMapper.selectById(kbId);
        if (kb == null) throw new IllegalArgumentException("知识库不存在: " + kbId);
        List<Long> dups = jdbcTemplate.queryForList(
            "SELECT id FROM document WHERE file_type = 'NEWS' AND source_url = ? LIMIT 1",
            Long.class, n.getUrl());
        if (!dups.isEmpty()) {
            throw new IllegalArgumentException("该新闻已收藏过");
        }
        String text = n.getTitle() + "\n" + (n.getSummary() == null ? "" : n.getSummary());
        documentService.createEmbeddedDocument(kbId, n.getTitle(), "NEWS", n.getUrl(), text);
    }

    public CrawlTask latestTask() {
        return latestTaskByStatus(null);
    }

    // ---------- 私有 ----------

    private CrawlTask latestTaskByStatus(String status) {
        return crawlTaskMapper.selectList(
            new LambdaQueryWrapper<CrawlTask>()
                .eq(status != null, CrawlTask::getStatus, status)
                .orderByDesc(CrawlTask::getId).last("LIMIT 1"))
            .stream().findFirst().orElse(null);
    }

    private boolean exists(String url) {
        return dailyNewsMapper.selectCount(
            new LambdaQueryWrapper<DailyNews>().eq(DailyNews::getUrl, url)) > 0;
    }

    private Long sourceIdOf(List<NewsItem> items) {
        if (items.isEmpty()) return null;
        CrawlerSource s = crawlerSourceMapper.selectOne(
            new LambdaQueryWrapper<CrawlerSource>().eq(CrawlerSource::getName, items.get(0).sourceName()));
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

    private void touchSource(Long sourceId) {
        if (sourceId == null) return;
        jdbcTemplate.update("UPDATE crawler_source SET last_crawl = now() WHERE id = ?", sourceId);
    }
}
