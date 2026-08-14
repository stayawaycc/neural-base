package com.neuralbase.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Hacker News 官方 API:topstories → 逐条 item(标题/链接)。 */
@Component
@Profile("!test")
public class HackerNewsNewsFetcher implements NewsFetcher {

    private static final String API_BASE = "https://hacker-news.firebaseio.com/v0/";
    private static final int TOP_N = 30;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<NewsItem> fetch() {
        try {
            String topJson = Jsoup.connect(API_BASE + "topstories.json")
                .ignoreContentType(true).timeout(15_000).execute().body();
            List<NewsItem> items = new ArrayList<>();
            for (JsonNode idNode : objectMapper.readTree(topJson)) {
                long id = idNode.asLong();
                if (items.size() >= TOP_N) break;
                try {
                    String itemJson = Jsoup.connect(API_BASE + "item/" + id + ".json")
                        .ignoreContentType(true).timeout(10_000).execute().body();
                    NewsItem item = parseItemJson(itemJson, id);
                    if (item != null && item.title() != null && !item.title().isBlank()) {
                        items.add(item);
                    }
                } catch (Exception e) {
                    // 单条失败跳过,不影响整轮
                }
            }
            return items;
        } catch (IOException e) {
            throw new RuntimeException("Hacker News 爬取失败", e);
        }
    }

    static NewsItem parseItemJson(String json, long id) {
        try {
            JsonNode node = new ObjectMapper().readTree(json);
            String title = node.path("title").asText(null);
            if (title == null || title.isBlank()) return null;
            String url = node.path("url").asText(null);
            if (url == null || url.isBlank()) {
                url = "https://news.ycombinator.com/item?id=" + id;
            }
            return new NewsItem(title, url, "Hacker News");
        } catch (Exception e) {
            return null;
        }
    }
}
