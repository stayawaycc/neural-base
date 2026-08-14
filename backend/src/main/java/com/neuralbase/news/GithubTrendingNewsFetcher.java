package com.neuralbase.news;

import org.jsoup.Jsoup;
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

/** GitHub Trending 列表页爬取;解析逻辑独立成静态函数以便离线测试。 */
@Component
@Profile("!test")
public class GithubTrendingNewsFetcher implements NewsFetcher {

    private static final String TRENDING_URL = "https://github.com/trending";

    @Override
    public List<NewsItem> fetch() {
        try {
            // JDK HttpClient(指纹更接近真实浏览器;连续多轮爬取时 Jsoup 易被限流)
            HttpRequest request = HttpRequest.newBuilder(URI.create(TRENDING_URL))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
            String html = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString())
                .body();
            return parseDoc(Jsoup.parse(html, TRENDING_URL));
        } catch (IOException | InterruptedException e) {
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
