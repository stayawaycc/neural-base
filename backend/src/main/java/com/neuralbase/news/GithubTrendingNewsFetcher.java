package com.neuralbase.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * GitHub 热门 AI 仓库:Search API `topic:ai` + 近 30 天有推送,按 stars 排序 top 20。
 * 源名保留 "GitHub Trending"(口径由全站热门改为 AI 主题热门,与 GitHub Star AI 的「近 7 天新建」区分)。
 * 本机证书链受安全软件干扰,经 GithubSearchClient 内置的 WindowsTrustSslContext 直连。
 */
@Component
@Profile("!test")
public class GithubTrendingNewsFetcher implements NewsFetcher {

    private static final int TOP_N = 20;
    private static final int MAX_DESC = 200;

    @Override
    public List<NewsItem> fetch() {
        String query = "topic:ai pushed:>" + LocalDate.now().minusDays(30);
        return parseResponse(GithubSearchClient.searchJson(query, TOP_N));
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
