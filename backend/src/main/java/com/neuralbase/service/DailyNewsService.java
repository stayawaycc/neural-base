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
            if (task != null) {
                updateTask(task.getId(), "RUNNING", 50, null);
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
                updateTask(task.getId(), "SUCCESS", 100, null);
            }
            log.info("新闻爬取完成:{} 条待汇总,{} 条处理", pending.size(), done);
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
