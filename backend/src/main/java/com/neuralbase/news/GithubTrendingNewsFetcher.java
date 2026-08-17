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

/**
 * GitHub 热门 AI 仓库:Search API `topic:ai` + 近 30 天有推送,按 stars 排序 top 20。
 * 源名保留 "GitHub Trending"(口径由全站热门改为 AI 主题热门,与 GitHub Star AI 的「近 7 天新建」区分)。
 * 本机证书链受安全软件干扰,使用 WindowsTrustSslContext 直连。
 */
@Component
@Profile("!test")
public class GithubTrendingNewsFetcher implements NewsFetcher {

    private static final String API = "https://api.github.com/search/repositories";
    private static final int TOP_N = 20;
    private static final int MAX_DESC = 200;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<NewsItem> fetch() {
        try {
            String since = LocalDate.now().minusDays(30).toString();
            String query = "topic:ai pushed:>" + since;
            String url = API + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&sort=stars&order=desc&per_page=" + TOP_N;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "NeuralBase")
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
            String json = HttpClient.newBuilder()
                .sslContext(WindowsTrustSslContext.create())
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString())
                .body();
            return parseResponse(json);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("GitHub Trending 爬取失败", e);
        }
    }

    @Override
    public String sourceName() {
        return "GitHub Trending";
    }

    static List<NewsItem> parseResponse(String json) {
        List<NewsItem> items = new ArrayList<>();
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            for (JsonNode item : root.path("items")) {
                String name = item.path("full_name").asText(null);
                String url = item.path("html_url").asText(null);
                if (name == null || url == null) continue;
                String desc = item.path("description").asText(null);
                String title = name;
                if (desc != null && !desc.isBlank()) {
                    title = name + " — " + desc.substring(0, Math.min(MAX_DESC, desc.length())).strip();
                }
                items.add(new NewsItem(title, url, "GitHub Trending"));
            }
        } catch (Exception ignored) {
            // 解析失败返回空,由上层告警
        }
        return items;
    }
}
