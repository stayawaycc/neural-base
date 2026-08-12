package com.neuralbase.config;

import com.neuralbase.crawler.ArticleCrawler;
import com.neuralbase.crawler.CrawlException;
import com.neuralbase.crawler.CrawledArticle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MockBaguwenConfig {

    /** 假爬虫：URL 含 "fail" 抛异常，否则返回固定正文 HTML。测试 @BeforeEach 无需清静态状态，天然确定。 */
    @Bean
    @Primary
    public ArticleCrawler mockArticleCrawler() {
        return url -> {
            if (url.contains("fail")) {
                throw new CrawlException("mock 爬取失败: " + url);
            }
            return new CrawledArticle("Mock 文章", url,
                "<html><body><article><h1>Mock 文章</h1><p>" + "Mock 正文内容。".repeat(200)
                + "</p></article></body></html>");
        };
    }
}
