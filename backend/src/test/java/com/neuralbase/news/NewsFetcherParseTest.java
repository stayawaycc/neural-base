package com.neuralbase.news;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsFetcherParseTest {

    @Test
    void githubTrendingParsesRows() {
        String html = """
            <html><body>
            <article class="Box-row"><h2><a href="/owner/repo">repo-name</a></h2><p>  A cool description </p></article>
            <article class="Box-row"><h2><a href="/o2/r2">repo2</a></h2><p>desc2</p></article>
            </body></html>
            """;
        List<NewsItem> items = GithubTrendingNewsFetcher.parseHtml(html);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("repo-name");
        assertThat(items.get(0).url()).isEqualTo("https://github.com/owner/repo");
    }

    @Test
    void hackerNewsParsesItem() {
        String json = "{\"id\":1,\"title\":\"Show HN: Foo\",\"url\":\"https://foo.example\"}";
        NewsItem item = new HackerNewsNewsFetcher().parseItemJson(json, 1L);
        assertThat(item.title()).isEqualTo("Show HN: Foo");
        assertThat(item.url()).isEqualTo("https://foo.example");
    }

    @Test
    void hackerNewsFallsBackToCommentsUrl() {
        String json = "{\"id\":7,\"title\":\"Ask HN: Bar\"}";
        NewsItem item = new HackerNewsNewsFetcher().parseItemJson(json, 7L);
        assertThat(item.url()).isEqualTo("https://news.ycombinator.com/item?id=7");
    }
}
