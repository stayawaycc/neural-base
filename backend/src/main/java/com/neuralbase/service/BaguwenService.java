package com.neuralbase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.crawler.ArticleCrawler;
import com.neuralbase.crawler.ContentExtractor;
import com.neuralbase.crawler.CrawledArticle;
import com.neuralbase.dto.FavoriteResult;
import com.neuralbase.entity.BaguwenArticle;
import com.neuralbase.entity.BaguwenImportTask;
import com.neuralbase.entity.BaguwenQaPair;
import com.neuralbase.entity.Document;
import com.neuralbase.mapper.BaguwenArticleMapper;
import com.neuralbase.mapper.BaguwenImportTaskMapper;
import com.neuralbase.mapper.BaguwenQaPairMapper;
import com.neuralbase.mapper.DocumentMapper;
import com.neuralbase.mapper.KbMapper;
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

    // ---------- 收藏到知识库 ----------

    /** 把文章的问答对按 Q/A 文本切片后 embedding 进指定 KB（复用现有 VectorStore 管线）。 */
    public FavoriteResult favorite(Long articleId, Long kbId) {
        if (kbMapper.selectById(kbId) == null) {
            throw new IllegalArgumentException("知识库不存在: " + kbId);
        }
        BaguwenArticle article = getArticle(articleId);
        List<BaguwenQaPair> pairs = qaPairs(articleId);
        if (pairs.isEmpty()) {
            throw new IllegalArgumentException("文章没有问答对，无法收藏: " + articleId);
        }
        StringBuilder sb = new StringBuilder();
        for (BaguwenQaPair p : pairs) {
            sb.append("Q: ").append(p.getQuestion()).append("\nA: ").append(p.getAnswer()).append("\n");
        }
        List<String> chunks = textChunker.chunk(sb.toString());
        if (chunks.isEmpty()) {
            throw new IllegalStateException("问答对文本切片为空: " + articleId);
        }

        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setTitle(article.getTitle());
        doc.setFileType("BAGUWEN");
        doc.setSourceUrl(article.getUrl());
        doc.setStatus("READY");
        doc.setChunkCount(chunks.size());
        documentMapper.insert(doc);

        List<org.springframework.ai.document.Document> aiDocs = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("docId", doc.getId());
            metadata.put("kbId", kbId);
            metadata.put("chunkIndex", i);
            aiDocs.add(new org.springframework.ai.document.Document(chunks.get(i), metadata));
        }
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'docId' = ?",
            String.valueOf(doc.getId()));
        vectorStore.add(aiDocs);
        return new FavoriteResult(chunks.size());
    }

    // ---------- 辅助 ----------

    private BaguwenImportTask requireTask(Long id) {
        BaguwenImportTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }
        return task;
    }

    private BaguwenArticle getArticle(Long id) {
        BaguwenArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new IllegalArgumentException("文章不存在: " + id);
        }
        return article;
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
