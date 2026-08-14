package com.neuralbase.news;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Papers with Code 首页论文卡爬取;解析逻辑独立成静态函数以便离线测试。 */
@Component
@Profile("!test")
public class PapersWithCodeNewsFetcher implements NewsFetcher {

    private static final String BASE_URL = "https://paperswithcode.com";

    @Override
    public List<NewsItem> fetch() {
        try {
            Document doc = Jsoup.connect(BASE_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                .timeout(15_000)
                .get();
            return parseHtml(doc.html(), BASE_URL);
        } catch (IOException e) {
            throw new RuntimeException("Papers with Code 爬取失败", e);
        }
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
