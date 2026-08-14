package com.neuralbase.news;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoreNewsFetcherParseTest {

    @Test
    void papersWithCodeParsesPaperCards() {
        String html = """
            <html><body>
            <div class="paper-card"><h1><a href="/paper/attention-is-all-you-need">Attention Is All You Need</a></h1></div>
            <div class="paper-card"><h1><a href="/paper/resnet">Deep Residual Learning</a></h1></div>
            </body></html>
            """;
        List<NewsItem> items = PapersWithCodeNewsFetcher.parseHtml(html, "https://paperswithcode.com");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("Attention Is All You Need");
        assertThat(items.get(0).url()).isEqualTo("https://paperswithcode.com/paper/attention-is-all-you-need");
        assertThat(items.get(0).sourceName()).isEqualTo("Papers with Code");
    }

    @Test
    void jiQiZhiXinParsesArticleLinks() {
        String html = """
            <html><body>
            <div class="article-item"><a href="https://www.jiqizhixin.com/articles/2024-01-01-1">文章甲</a></div>
            <div class="article-item"><a href="https://www.jiqizhixin.com/articles/2024-01-02-2">文章乙</a></div>
            </body></html>
            """;
        List<NewsItem> items = JiQiZhiXinNewsFetcher.parseHtml(html, "https://www.jiqizhixin.com/articles");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("文章甲");
        assertThat(items.get(1).sourceName()).isEqualTo("机器之心");
    }

    @Test
    void infoqParsesArticleTitles() {
        String html = """
            <html><body>
            <a class="com-article-title" href="https://www.infoq.cn/article/KwZX2bLcaHRjuLG95vHI">某技术深度文</a>
            <a class="com-article-title" href="https://www.infoq.cn/news/9sy33cA91Fp8z5mlOvNu">某资讯</a>
            </body></html>
            """;
        List<NewsItem> items = InfoQNewsFetcher.parseHtml(html, "https://www.infoq.cn");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("某技术深度文");
        assertThat(items.get(0).url()).isEqualTo("https://www.infoq.cn/article/KwZX2bLcaHRjuLG95vHI");
        assertThat(items.get(1).sourceName()).isEqualTo("InfoQ");
    }
}
