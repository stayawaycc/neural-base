package com.neuralbase.news;

import com.neuralbase.entity.CrawlerSource;
import com.neuralbase.mapper.CrawlerSourceMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** 用户自建静态源:读 crawler_source(enabled、type=html、非内置名单),按 selector 提取链接列表。 */
@Component
public class GenericHtmlNewsFetcher implements NewsFetcher {

    private static final Logger log = LoggerFactory.getLogger(GenericHtmlNewsFetcher.class);

    private static final List<String> BUILTIN = List.of(
        "GitHub Trending", "Hacker News", "Papers with Code", "机器之心", "InfoQ", "GitHub Star AI");

    private final CrawlerSourceMapper crawlerSourceMapper;

    public GenericHtmlNewsFetcher(CrawlerSourceMapper crawlerSourceMapper) {
        this.crawlerSourceMapper = crawlerSourceMapper;
    }

    @Override
    public List<NewsItem> fetch() {
        List<NewsItem> items = new ArrayList<>();
        for (CrawlerSource s : crawlerSourceMapper.selectList(null)) {
            if (!Boolean.TRUE.equals(s.getEnabled()) || s.getType() == null
                || !s.getType().equals("html") || BUILTIN.contains(s.getName())) {
                continue;
            }
            if (s.getSelector() == null || s.getSelector().isBlank()) {
                continue;
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(s.getUrl()))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
                String html = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString())
                    .body();
                List<NewsItem> found = parseHtml(html, s.getUrl(), s.getSelector());
                for (NewsItem item : found) {
                    items.add(new NewsItem(item.title(), item.url(), s.getName()));
                }
            } catch (Exception e) {
                log.warn("自建源爬取失败 {}: {}", s.getName(), e.getMessage());
            }
        }
        return items;
    }

    static List<NewsItem> parseHtml(String html, String baseUri, String selector) {
        Document doc = Jsoup.parse(html, baseUri);
        List<NewsItem> items = new ArrayList<>();
        for (var a : doc.select(selector)) {
            String title = a.text().strip();
            if (title.isEmpty()) continue;
            items.add(new NewsItem(title, a.absUrl("href"), null));
        }
        return items;
    }
}
