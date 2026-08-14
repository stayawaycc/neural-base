package com.neuralbase.news;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenericHtmlNewsFetcherTest {

    @Test
    void parsesLinksBySelector() {
        String html = """
            <html><body>
            <ul class="news-list"><li><a href="/a/1">标题一</a></li><li><a href="/a/2">标题二</a></li></ul>
            </body></html>
            """;
        var items = GenericHtmlNewsFetcher.parseHtml(html, "https://example.com", "ul.news-list a");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("标题一");
        assertThat(items.get(0).url()).isEqualTo("https://example.com/a/1");
    }
}
