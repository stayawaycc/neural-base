package com.neuralbase.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.config.MockEmbeddingConfig;
import com.neuralbase.config.MockNewsConfig;
import com.neuralbase.service.DailyNewsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({MockEmbeddingConfig.class, MockNewsConfig.class})
class DailyNewsControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DailyNewsService service;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM crawl_task");
        jdbcTemplate.update("DELETE FROM daily_news");
        jdbcTemplate.update("UPDATE crawler_source SET enabled = TRUE");
        // 清自建源,保证来源计数断言幂等(内置 6 个保留)
        jdbcTemplate.update("DELETE FROM crawler_source WHERE name NOT IN "
            + "('GitHub Trending','Hacker News','Papers with Code','机器之心','InfoQ','GitHub Star AI')");
    }

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asText();
    }

    @Test
    void crawlListReadFavoriteFlow() throws Exception {
        String tk = token();
        service.startCrawl();
        service.runCrawlNow();

        mvc.perform(get("/api/news").header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(3));

        long id = objectMapper.readTree(mvc.perform(get("/api/news")
                .header("Authorization", "Bearer " + tk)).andReturn().getResponse().getContentAsString())
            .get("data").get("items").get(0).get("id").asLong();

        mvc.perform(put("/api/news/" + id + "/read")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isRead\":true}"))
            .andExpect(status().isOk());

        mvc.perform(put("/api/news/read-all").header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk());

        mvc.perform(post("/api/news/" + id + "/favorite")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kbId\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/news/task/latest").header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        mvc.perform(post("/api/news/crawl").header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk());

        // 等异步爬取结束,避免其假件插入泄漏到后续测试(分页 total 断言被污染)
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline
            && !jdbcTemplate.queryForList("SELECT 1 FROM crawl_task WHERE status = 'RUNNING'").isEmpty()) {
            Thread.sleep(100);
        }
    }

    @Test
    void listSupportsSortAndSourceFilter() throws Exception {
        String tk = token();
        service.startCrawl();
        service.runCrawlNow();

        mvc.perform(get("/api/news").header("Authorization", "Bearer " + tk)
                .param("sort", "source"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(3));

        mvc.perform(get("/api/news").header("Authorization", "Bearer " + tk)
                .param("sort", "bogus"))
            .andExpect(status().isOk()); // 非法 sort 回退默认,不 400

        Long githubId = jdbcTemplate.queryForObject(
            "SELECT id FROM crawler_source WHERE name = 'GitHub Trending'", Long.class);
        mvc.perform(get("/api/news").header("Authorization", "Bearer " + tk)
                .param("sourceId", String.valueOf(githubId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(2)); // 假件 GitHub 源 2 条
    }

    @Test
    void listSupportsPagination() throws Exception {
        String tk = token();
        for (int i = 1; i <= 25; i++) {
            jdbcTemplate.update(
                "INSERT INTO daily_news (source_id, url, title, is_read) VALUES (NULL, ?, ?, FALSE)",
                "https://example.com/p" + i, "AI 新闻 " + i);
        }
        mvc.perform(get("/api/news").header("Authorization", "Bearer " + tk)
                .param("page", "2").param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(10))
            .andExpect(jsonPath("$.data.total").value(25))
            .andExpect(jsonPath("$.data.page").value(2))
            .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    void sourceCrudRespectsBuiltinProtection() throws Exception {
        String tk = token();

        mvc.perform(post("/api/news/source")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"我的技术站\",\"url\":\"https://example.com\",\"selector\":\"ul.news a\",\"enabled\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").isNumber());

        long builtinId = jdbcTemplate.queryForObject(
            "SELECT id FROM crawler_source WHERE name = 'InfoQ'", Long.class);
        mvc.perform(delete("/api/news/source/" + builtinId)
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("内置")));

        mvc.perform(get("/api/news/source").header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(7)); // 6 内置 + 1 自建
    }

    @Test
    void cleanupEndpointReturnsDeletedCount() throws Exception {
        String tk = token();
        Long trendingId = jdbcTemplate.queryForObject(
            "SELECT id FROM crawler_source WHERE name = 'GitHub Trending'", Long.class);
        jdbcTemplate.update(
            "INSERT INTO daily_news (source_id, url, title, is_read) VALUES (?, 'https://c/plain', 'Repair bicycle chain', FALSE)",
            trendingId);
        mvc.perform(post("/api/news/cleanup").header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void crawlRejectedWhileRunning() throws Exception {
        String tk = token();
        service.startCrawl(); // 制造 RUNNING 任务
        mvc.perform(post("/api/news/crawl").header("Authorization", "Bearer " + tk))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("进行中")));
    }

    @Test
    void newsEndpointsRequireAuth() throws Exception {
        mvc.perform(get("/api/news")).andExpect(status().isUnauthorized());
    }
}
