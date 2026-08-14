package com.neuralbase.news;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** InfoQ 中文首页资讯爬取(SSR,`a.com-article-title` 结构,2026-08 实测可静态抓取)。 */
@Component
@Profile("!test")
public class InfoQNewsFetcher implements NewsFetcher {

    private static final String BASE_URL = "https://www.infoq.cn/";

    @Override
    public List<NewsItem> fetch() {
        try {
            Document doc = Jsoup.connect(BASE_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                .timeout(15_000)
                .get();
            return parseHtml(doc.html(), BASE_URL);
        } catch (IOException e) {
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
