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
import java.util.List;

/** Papers with Code 首页论文卡爬取。与 InfoQ 同理用 JDK HttpClient 拉取(指纹更接近真实浏览器),Jsoup 解析。 */
@Component
@Profile("!test")
public class PapersWithCodeNewsFetcher implements NewsFetcher {

    private static final String BASE_URL = "https://paperswithcode.com";

    @Override
    public List<NewsItem> fetch() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL))
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
        for (var card : doc.select("div.paper-card")) {
            var a = card.selectFirst("h1 a");
            if (a == null) continue;
            String title = a.text().strip();
            if (title.isEmpty()) continue;
            items.add(new NewsItem(title, a.absUrl("href"), "Papers with Code"));
        }
        return items;
    }
}
