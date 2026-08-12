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
        // favorite 测试用 kb_id=1；共享测试库可能为空，需保证该 KB 存在
        jdbcTemplate.update("INSERT INTO kb (id, name, description, color) VALUES "
            + "(1, '测试知识库', null, null) ON CONFLICT (id) DO NOTHING");
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

        // 模拟"修正 URL 后重跑"：mock 爬虫对含 fail 的 URL 永远抛异常，需换成可成功的 URL
        BaguwenImportTask t = taskMapper.selectById(failId);
        t.setUrl("http://ok/1");
        taskMapper.updateById(t);
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
