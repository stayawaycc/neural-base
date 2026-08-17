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
        // favorite 测试用 kb_id=1；共享测试库可能为空，需保证该 KB 存在
        jdbcTemplate.update("INSERT INTO kb (id, name, description, color) VALUES "
            + "(1, '测试知识库', null, null) ON CONFLICT (id) DO NOTHING");
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

    @Test
    void deleteTaskRemovesFailedTask() throws Exception {
        String tk = token();
        BaguwenImportTask task = baguwenService.importUrls(List.of("http://fail/1")).get(0);
        baguwenService.processTask(task.getId());
        awaitStatus(task.getId(), "FAILED");

        mvc.perform(delete("/api/baguwen/tasks/" + task.getId())
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk());
        assertThat(taskMapper.selectById(task.getId())).isNull();
    }

    @Test
    void deleteTaskRemovesSuccessTask() throws Exception {
        String tk = token();
        BaguwenImportTask task = baguwenService.importUrls(List.of("http://ok/1")).get(0);
        task.setStatus("SUCCESS");
        taskMapper.updateById(task);

        mvc.perform(delete("/api/baguwen/tasks/" + task.getId())
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk());
        assertThat(taskMapper.selectById(task.getId())).isNull();
    }

    @Test
    void deleteTaskRejectsPending() throws Exception {
        String tk = token();
        BaguwenImportTask task = baguwenService.importUrls(List.of("http://ok/1")).get(0); // PENDING

        mvc.perform(delete("/api/baguwen/tasks/" + task.getId())
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
        assertThat(taskMapper.selectById(task.getId())).isNotNull();
    }

    @Test
    void deleteTasksByStatusClearsFailedOnly() throws Exception {
        String tk = token();
        BaguwenImportTask f1 = baguwenService.importUrls(List.of("http://fail/1")).get(0);
        BaguwenImportTask f2 = baguwenService.importUrls(List.of("http://fail/2")).get(0);
        BaguwenImportTask s1 = baguwenService.importUrls(List.of("http://ok/1")).get(0);
        f1.setStatus("FAILED"); f2.setStatus("FAILED"); s1.setStatus("SUCCESS");
        taskMapper.updateById(f1); taskMapper.updateById(f2); taskMapper.updateById(s1);

        mvc.perform(delete("/api/baguwen/tasks").param("status", "FAILED")
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(2));
        assertThat(taskMapper.selectById(f1.getId())).isNull();
        assertThat(taskMapper.selectById(f2.getId())).isNull();
        assertThat(taskMapper.selectById(s1.getId())).isNotNull();
    }

    @Test
    void deleteTasksByStatusRejectsNonTerminalStatus() throws Exception {
        String tk = token();
        mvc.perform(delete("/api/baguwen/tasks").param("status", "PENDING")
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void tasksListPaginates() throws Exception {
        String tk = token();
        for (int i = 1; i <= 15; i++) {
            BaguwenImportTask t = new BaguwenImportTask();
            t.setUrl("http://p/" + i);
            t.setStatus("FAILED");
            t.setAttempt(0);
            taskMapper.insert(t);
        }
        mvc.perform(get("/api/baguwen/tasks").param("page", "2").param("size", "10")
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records.length()").value(5))
            .andExpect(jsonPath("$.data.total").value(15))
            .andExpect(jsonPath("$.data.current").value(2));
    }

    @Test
    void tasksMultiStatusFilter() throws Exception {
        String tk = token();
        BaguwenImportTask p = baguwenService.importUrls(List.of("http://ok/1")).get(0); // PENDING
        BaguwenImportTask f = baguwenService.importUrls(List.of("http://fail/1")).get(0);
        f.setStatus("FAILED");
        taskMapper.updateById(f);

        mvc.perform(get("/api/baguwen/tasks").param("status", "PENDING,PROCESSING")
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].id").value(p.getId()));
    }

    @Test
    void tasksMultiStatusFilterWithPagination() throws Exception {
        String tk = token();
        for (int i = 1; i <= 5; i++) {
            BaguwenImportTask t = new BaguwenImportTask();
            t.setUrl("http://mp/" + i);
            t.setStatus(i % 2 == 1 ? "PENDING" : "PROCESSING");
            t.setAttempt(0);
            taskMapper.insert(t);
        }
        BaguwenImportTask f = baguwenService.importUrls(List.of("http://fail/1")).get(0);
        f.setStatus("FAILED");
        taskMapper.updateById(f);

        mvc.perform(get("/api/baguwen/tasks")
                .param("status", "PENDING,PROCESSING")
                .param("page", "2").param("size", "2")
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(5)) // FAILED 不计入多状态过滤
            .andExpect(jsonPath("$.data.records.length()").value(2))
            .andExpect(jsonPath("$.data.current").value(2));
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
