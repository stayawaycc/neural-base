package com.neuralbase.news;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoreNewsFetcherParseTest {

    @Test
    void papersWithCodeParsesHuggingFaceCards() {
        String html = """
            <html><body>
            <article><a href="/papers/2608.09888" class="line-clamp-2">BDH-CQ: In-Context Learning with Recurrent Latent Reasoning</a></article>
            <article><a href="/papers/2608.09888" class="line-clamp-2">BDH-CQ duplicate (responsive)</a></article>
            <article><h3><a href="/papers/2605.31264">COLLEAGUE.SKILL</a></h3></article>
            <article><a href="/papers/2608.09888"><span>thumbnail</span></a></article>
            <nav><a href="/papers/9999.00001">Trending Papers 导航</a></nav>
            </body></html>
            """;
        List<NewsItem> items = PapersWithCodeNewsFetcher.parseHtml(html, "https://huggingface.co");
        assertThat(items).hasSize(2); // 同 URL 去重;article 外导航链接不取
        assertThat(items.get(0).title()).isEqualTo("BDH-CQ: In-Context Learning with Recurrent Latent Reasoning");
        assertThat(items.get(0).url()).isEqualTo("https://huggingface.co/papers/2608.09888");
        assertThat(items.get(0).sourceName()).isEqualTo("Papers with Code");
        assertThat(items.get(1).title()).isEqualTo("COLLEAGUE.SKILL");
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
