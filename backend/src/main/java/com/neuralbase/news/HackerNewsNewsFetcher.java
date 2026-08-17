package com.neuralbase.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Hacker News 热门 AI 故事:Algolia 检索(query=AI、tags=story,按相关度/热度排序)。
 *  前缀匹配可能混入 "Airfoil" 之类非 AI 标题,needsAiFilter=true 由入库前关键词兜底过滤。 */
@Component
@Profile("!test")
public class HackerNewsNewsFetcher implements NewsFetcher {

    private static final String API = "https://hn.algolia.com/api/v1/search?query=AI&tags=story&hitsPerPage=30";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<NewsItem> fetch() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API))
                .header("User-Agent", "NeuralBase")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
            String json = HttpClient.newBuilder().build()
                .send(request, HttpResponse.BodyHandlers.ofString()).body();
            return parseResponse(json);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Hacker News 爬取失败", e);
        }
    }

    @Override
    public String sourceName() {
        return "Hacker News";
    }

    @Override
    public boolean needsAiFilter() {
        return true;
    }

    static List<NewsItem> parseResponse(String json) {
        List<NewsItem> items = new ArrayList<>();
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            for (JsonNode hit : root.path("hits")) {
                String title = hit.path("title").asText(null);
                if (title == null || title.isBlank()) continue;
                String url = hit.path("url").asText(null);
                if (url == null || url.isBlank()) {
                    url = "https://news.ycombinator.com/item?id=" + hit.path("objectID").asText("");
                }
                items.add(new NewsItem(title, url, "Hacker News"));
            }
        } catch (Exception ignored) {
            // 解析失败返回空,由上层告警
        }
        return items;
    }
}
