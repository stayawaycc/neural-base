package com.neuralbase.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** GitHub Search API:近 7 天新建的 AI 主题仓库,按 stars 排序 top 20(「star 新增量」的可实现口径)。 */
@Component
@Profile("!test")
public class GithubStarAiNewsFetcher implements NewsFetcher {

    private static final int TOP_N = 20;

    @Override
    public List<NewsItem> fetch() {
        return parseResponse(GithubSearchClient.searchJson(buildQuery(LocalDate.now()), TOP_N));
    }

    @Override
    public String sourceName() {
        return "GitHub Star AI";
    }

    /** 分隔符必须是空格(编码后为 +):若原样用 "+" 会被编码成 %2B,GitHub 将日期解析为 "YYYY-MM-DD+topic:ai" 返回 422。 */
    static String buildQuery(LocalDate today) {
        return "created:>" + today.minusDays(7) + " topic:ai";
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
