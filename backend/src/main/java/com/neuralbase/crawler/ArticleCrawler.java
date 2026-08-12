package com.neuralbase.crawler;

public interface ArticleCrawler {
    CrawledArticle fetch(String url) throws CrawlException;
}
