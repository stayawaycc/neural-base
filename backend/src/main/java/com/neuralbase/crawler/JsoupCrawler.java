package com.neuralbase.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JsoupCrawler implements ArticleCrawler {

    private static final int TIMEOUT_MS = 10_000;
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    @Override
    public CrawledArticle fetch(String url) throws CrawlException {
        try {
            String html = Jsoup.connect(url)
                .userAgent(UA)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get()
                .html();
            return parse(html, url);
        } catch (IOException e) {
            throw new CrawlException("Jsoup 抓取失败: " + url + " -> " + e.getMessage(), e);
        }
    }

    /** 从 HTML 解析标题与原始 HTML，离线可测（网络请求只发生在 fetch 内）。 */
    public CrawledArticle parse(String html, String url) {
        Document doc = Jsoup.parse(html);
        String title = doc.title();
        if (title == null || title.isBlank()) {
            Element h1 = doc.selectFirst("h1");
            title = h1 == null ? "未命名" : h1.text();
        }
        return new CrawledArticle(title.strip(), url, html);
    }
}
