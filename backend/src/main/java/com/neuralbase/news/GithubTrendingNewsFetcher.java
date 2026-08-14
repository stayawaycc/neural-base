package com.neuralbase.news;

import org.jsoup.Jsoup;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** GitHub Trending 列表页爬取;解析逻辑独立成静态函数以便离线测试。 */
@Component
@Profile("!test")
public class GithubTrendingNewsFetcher implements NewsFetcher {

    private static final String TRENDING_URL = "https://github.com/trending";

    @Override
    public List<NewsItem> fetch() {
        try {
            var doc = Jsoup.connect(TRENDING_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15_000)
                .get();
            return parseDoc(doc);
        } catch (IOException e) {
            throw new RuntimeException("GitHub Trending 爬取失败", e);
        }
    }

    static List<NewsItem> parseHtml(String html) {
        return parseDoc(Jsoup.parse(html));
    }

    private static List<NewsItem> parseDoc(org.jsoup.nodes.Document doc) {
        List<NewsItem> items = new ArrayList<>();
        for (var row : doc.select("article.Box-row")) {
            var a = row.selectFirst("h2 a");
            if (a == null) continue;
            String href = a.attr("href");
            String title = a.text().replaceAll("\\s+", " ").strip();
            if (title.isEmpty() || !href.startsWith("/")) continue;
            items.add(new NewsItem(title, "https://github.com" + href, "GitHub Trending"));
        }
        return items;
    }
}
