package com.neuralbase.news;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Papers with Code 已 302 迁移至 HuggingFace papers/trending;源名保留。响应式布局同 URL 重复出现,按 URL 去重。 */
@Component
@Profile("!test")
public class PapersWithCodeNewsFetcher implements NewsFetcher {

    private static final String BASE_URL = "https://huggingface.co/papers/trending";

    @Override
    public List<NewsItem> fetch() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
            String html = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .sslContext(WindowsTrustSslContext.create())
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString())
                .body();
            return parseHtml(html, BASE_URL);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Papers with Code 爬取失败", e);
        }
    }

    @Override
    public String sourceName() {
        return "Papers with Code";
    }

    static List<NewsItem> parseHtml(String html, String baseUri) {
        Document doc = Jsoup.parse(html, baseUri);
        List<NewsItem> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        // 限定 article 内:页面其它区域(导航等)也可能出现 /papers/ 链接
        for (var a : doc.select("article a[href^=\"/papers/\"]")) {
            String title = a.text().strip();
            if (title.isEmpty()) continue;
            String url = a.absUrl("href");
            if (!seen.add(url)) continue;
            items.add(new NewsItem(title, url, "Papers with Code"));
        }
        return items;
    }
}
