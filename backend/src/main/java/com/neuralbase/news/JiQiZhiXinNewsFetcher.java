package com.neuralbase.news;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 机器之心文章列表爬取。注意:该站为 JS 渲染壳,静态抓取可能拿不到条目——失败由上层容忍并记日志。 */
@Component
@Profile("!test")
public class JiQiZhiXinNewsFetcher implements NewsFetcher {

    private static final String ARTICLES_URL = "https://www.jiqizhixin.com/articles";

    @Override
    public List<NewsItem> fetch() {
        try {
            Document doc = Jsoup.connect(ARTICLES_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                .timeout(15_000)
                .get();
            return parseHtml(doc.html(), ARTICLES_URL);
        } catch (IOException e) {
            throw new RuntimeException("机器之心爬取失败", e);
        }
    }


    @Override
    public String sourceName() {
        return "机器之心";
    }

    static List<NewsItem> parseHtml(String html, String baseUri) {
        Document doc = Jsoup.parse(html, baseUri);
        List<NewsItem> items = new ArrayList<>();
        for (var a : doc.select("a[href*=/articles/]")) {
            String title = a.text().strip();
            if (title.isEmpty() || !a.absUrl("href").contains("/articles/")) continue;
            items.add(new NewsItem(title, a.absUrl("href"), "机器之心"));
        }
        return items;
    }
}
