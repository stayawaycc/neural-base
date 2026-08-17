package com.neuralbase.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.config.MockEmbeddingConfig;
import com.neuralbase.config.MockNewsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 容器级错误(404 等)的 ERROR 二次分发会被安全链以 401 劫持(MockMvc 无真实容器复现不了,必须真实端口)。
 * 回归:带 token 访问不存在路径应得到真实状态码 404,而非 401 导致前端误登出。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({MockEmbeddingConfig.class, MockNewsConfig.class})
class ErrorDispatchSecurityTest {

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM crawl_task");
        jdbcTemplate.update("DELETE FROM daily_news");
    }

    private String token() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .build();
        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        String data = objectMapper.readTree(resp.body()).path("data").asText(null);
        assertThat(data).isNotBlank();
        return data;
    }

    @Test
    void unknownPathWithAuthReturns404Not401() throws Exception {
        String tk = token();
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/not-exist-xyz"))
            .header("Authorization", "Bearer " + tk)
            .GET().build();
        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(404);
    }

    @Test
    void methodMismatchWithAuthReturns405Not401() throws Exception {
        String tk = token();
        // /tasks/{id} 无 POST 映射,应 405;曾被 ERROR 分发篡改为 401(响应携带 Allow 头即其证据)
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/baguwen/tasks/999999"))
            .header("Authorization", "Bearer " + tk)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(405);
    }

    @Test
    void protectedPathWithoutAuthStillReturns401() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/news"))
            .GET().build();
        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(401);
    }
}
