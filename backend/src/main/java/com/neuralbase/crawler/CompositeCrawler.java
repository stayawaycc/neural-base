package com.neuralbase.crawler;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class CompositeCrawler implements ArticleCrawler {

    private static final int MIN_TEXT_CHARS = 100;

    private final ArticleCrawler jsoupCrawler;
    private final ArticleCrawler playwrightCrawler;
    private final ContentExtractor contentExtractor;

    public CompositeCrawler(@Qualifier("jsoupCrawler") ArticleCrawler jsoupCrawler,
                            @Qualifier("playwrightCrawler") ArticleCrawler playwrightCrawler,
                            ContentExtractor contentExtractor) {
        this.jsoupCrawler = jsoupCrawler;
        this.playwrightCrawler = playwrightCrawler;
        this.contentExtractor = contentExtractor;
    }

    @Override
    public CrawledArticle fetch(String url) throws CrawlException {
        CrawlException firstError = null;
        try {
            CrawledArticle staticResult = jsoupCrawler.fetch(url);
            if (hasSubstantialContent(staticResult)) {
                return staticResult;
            }
        } catch (CrawlException e) {
            firstError = e;
        }
        try {
            return playwrightCrawler.fetch(url);
        } catch (CrawlException e) {
            if (firstError != null) {
                e.addSuppressed(firstError);
            }
            throw e;
        }
    }

    private boolean hasSubstantialContent(CrawledArticle article) {
        return article != null && article.html() != null
            && contentExtractor.extract(article.html()).length() >= MIN_TEXT_CHARS;
    }
}
