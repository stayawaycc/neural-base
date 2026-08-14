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
    void deleteTaskRejectsNonFailed() throws Exception {
        String tk = token();
        BaguwenImportTask task = baguwenService.importUrls(List.of("http://ok/1")).get(0);
        task.setStatus("SUCCESS");
        taskMapper.updateById(task);

        mvc.perform(delete("/api/baguwen/tasks/" + task.getId())
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
        assertThat(taskMapper.selectById(task.getId())).isNotNull();
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
