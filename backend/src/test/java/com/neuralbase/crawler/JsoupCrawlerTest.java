package com.neuralbase.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsoupCrawlerTest {

    private final JsoupCrawler crawler = new JsoupCrawler();

    @Test
    void parseExtractsTitleAndKeepsHtml() {
        String html = """
            <html><head><title>Spring 面试题</title></head>
            <body><article><p>正文内容。</p></article></body></html>
            """;

        CrawledArticle article = crawler.parse(html, "http://example.com/a");

        assertThat(article.title()).isEqualTo("Spring 面试题");
        assertThat(article.url()).isEqualTo("http://example.com/a");
        assertThat(article.html()).contains("Spring 面试题");
    }

    @Test
    void parseFallsBackToH1WhenNoTitleTag() {
        String html = "<html><body><h1>没有 title 的页面</h1><p>内容</p></body></html>";

        CrawledArticle article = crawler.parse(html, "http://example.com/b");

        assertThat(article.title()).isEqualTo("没有 title 的页面");
    }
}
