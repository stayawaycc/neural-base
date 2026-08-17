package com.neuralbase.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** GitHub Search API:近 7 天新建的 AI 主题仓库,按 stars 排序 top 20(「star 新增量」的可实现口径)。 */
@Component
@Profile("!test")
public class GithubStarAiNewsFetcher implements NewsFetcher {

    private static final String API = "https://api.github.com/search/repositories";
    private static final int TOP_N = 20;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<NewsItem> fetch() {
        try {
            String url = buildUrl(LocalDate.now());
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "NeuralBase")
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
            String json = HttpClient.newBuilder()
                .sslContext(WindowsTrustSslContext.create())
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString()).body();
            return parseResponse(json);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("GitHub Star AI 爬取失败", e);
        }
    }

    /** 分隔符必须是空格(编码后为 +):若原样用 "+" 会被编码成 %2B,GitHub 将日期解析为 "YYYY-MM-DD+topic:ai" 返回 422。 */
    static String buildUrl(LocalDate today) {
        String since = today.minusDays(7).toString();
        String query = "created:>" + since + " topic:ai";
        return API + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&sort=stars&order=desc&per_page=" + TOP_N;
    }

    @Override
    public String sourceName() {
        return "GitHub Star AI";
    }

    static List<NewsItem> parseResponse(String json) {
        List<NewsItem> items = new ArrayList<>();
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            for (JsonNode item : root.path("items")) {
                String name = item.path("full_name").asText(null);
                String url = item.path("html_url").asText(null);
                if (name == null || url == null) continue;
                items.add(new NewsItem(name, url, "GitHub Star AI"));
            }
        } catch (Exception ignored) {
            // 解析失败返回空,由上层告警
        }
        return items;
    }
}
