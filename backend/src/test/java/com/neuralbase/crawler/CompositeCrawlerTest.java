package com.neuralbase.crawler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeCrawlerTest {

    private final ContentExtractor extractor = new ContentExtractor();

    @Test
    void usesStaticWhenItHasSubstantialContent() {
        AtomicBoolean renderCalled = new AtomicBoolean(false);
        ArticleCrawler jsoup = url -> new CrawledArticle("文章", url,
            "<html><body><article><p>" + "正文".repeat(200) + "</p></article></body></html>");
        ArticleCrawler playwright = url -> {
            renderCalled.set(true);
            throw new CrawlException("不应被调用");
        };
        CompositeCrawler composite = new CompositeCrawler(jsoup, playwright, extractor);

        CrawledArticle article = composite.fetch("http://x/1");

        assertThat(article.title()).isEqualTo("文章");
        assertThat(renderCalled).isFalse();
    }

    @Test
    void fallsBackToPlaywrightWhenStaticHasNoContent() {
        AtomicBoolean renderCalled = new AtomicBoolean(false);
        ArticleCrawler jsoup = url -> new CrawledArticle("壳页面", url,
            "<html><body><div id='app'></div></body></html>");
        ArticleCrawler playwright = url -> {
            renderCalled.set(true);
            return new CrawledArticle("渲染后", url,
                "<html><body><article><p>" + "真实内容".repeat(200) + "</p></article></body></html>");
        };
        CompositeCrawler composite = new CompositeCrawler(jsoup, playwright, extractor);

        CrawledArticle article = composite.fetch("http://x/2");

        assertThat(article.title()).isEqualTo("渲染后");
        assertThat(renderCalled).isTrue();
    }

    @Test
    void fallsBackToPlaywrightWhenStaticThrows() {
        ArticleCrawler jsoup = url -> { throw new CrawlException("连接超时"); };
        ArticleCrawler playwright = url -> new CrawledArticle("兜底", url,
            "<html><body><article><p>" + "内容".repeat(200) + "</p></article></body></html>");
        CompositeCrawler composite = new CompositeCrawler(jsoup, playwright, extractor);

        assertThat(composite.fetch("http://x/3").title()).isEqualTo("兜底");
    }

    @Test
    void throwsWhenBothFail() {
        ArticleCrawler jsoup = url -> { throw new CrawlException("静态失败"); };
        ArticleCrawler playwright = url -> { throw new CrawlException("渲染失败"); };
        CompositeCrawler composite = new CompositeCrawler(jsoup, playwright, extractor);

        assertThatThrownBy(() -> composite.fetch("http://x/4"))
            .isInstanceOf(CrawlException.class);
    }
}
