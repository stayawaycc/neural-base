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

/** InfoQ 中文首页资讯爬取(SSR,`a.com-article-title`)。Jsoup 的 HTTP 客户端被其反爬拦截,
 *  改用 JDK HttpClient(HTTP/2 指纹不同,curl 同源可通)拉取,解析仍用 Jsoup。 */
@Component
@Profile("!test")
public class InfoQNewsFetcher implements NewsFetcher {

    private static final String BASE_URL = "https://www.infoq.cn/";

    @Override
    public List<NewsItem> fetch() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
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
            throw new RuntimeException("InfoQ 爬取失败", e);
        }
    }

    static List<NewsItem> parseHtml(String html, String baseUri) {
        Document doc = Jsoup.parse(html, baseUri);
        List<NewsItem> items = new ArrayList<>();
        for (var a : doc.select("a.com-article-title")) {
            String title = a.text().strip();
            if (title.isEmpty()) continue;
            items.add(new NewsItem(title, a.absUrl("href"), "InfoQ"));
        }
        return items;
    }
}
