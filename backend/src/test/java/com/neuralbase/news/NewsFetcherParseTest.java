package com.neuralbase.news;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsFetcherParseTest {

    @Test
    void githubTrendingParsesSearchResponse() {
        String json = """
            {"items":[
              {"full_name":"openclaw/openclaw","html_url":"https://github.com/openclaw/openclaw",
               "description":"Your own personal AI assistant."},
              {"full_name":"comfyanonymous/ComfyUI","html_url":"https://github.com/comfyanonymous/ComfyUI",
               "description":null}
            ]}
            """;
        List<NewsItem> items = GithubTrendingNewsFetcher.parseResponse(json);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("openclaw/openclaw — Your own personal AI assistant.");
        assertThat(items.get(0).url()).isEqualTo("https://github.com/openclaw/openclaw");
        assertThat(items.get(0).sourceName()).isEqualTo("GitHub Trending");
        assertThat(items.get(1).title()).isEqualTo("comfyanonymous/ComfyUI");
    }

    @Test
    void githubTrendingSkipsMissingFields() {
        String json = """
            {"items":[
              {"full_name":"a/b","html_url":"https://github.com/a/b"},
              {"description":"no full_name","html_url":"https://example.com/x"}
            ]}
            """;
        assertThat(GithubTrendingNewsFetcher.parseResponse(json)).hasSize(1);
    }

    @Test
    void githubTrendingTruncatesLongDescription() {
        String longDesc = "d".repeat(300);
        String json = "{\"items\":[{\"full_name\":\"a/b\",\"html_url\":\"https://github.com/a/b\","
            + "\"description\":\"" + longDesc + "\"}]}";
        NewsItem item = GithubTrendingNewsFetcher.parseResponse(json).get(0);
        assertThat(item.title()).hasSize("a/b — ".length() + 200);
        assertThat(item.title()).startsWith("a/b — ");
    }

    @Test
    void hackerNewsParsesAlgoliaResponse() {
        String json = """
            {"hits":[
              {"title":"AI agents are coming","url":"https://x.example/1","objectID":"1"},
              {"title":"Ask HN: AI in 2026?","url":null,"objectID":"7"}
            ]}
            """;
        List<NewsItem> items = HackerNewsNewsFetcher.parseResponse(json);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("AI agents are coming");
        assertThat(items.get(0).url()).isEqualTo("https://x.example/1");
        assertThat(items.get(0).sourceName()).isEqualTo("Hacker News");
        assertThat(items.get(1).url()).isEqualTo("https://news.ycombinator.com/item?id=7");
    }

    @Test
    void hackerNewsSkipsBlankTitles() {
        String json = """
            {"hits":[
              {"title":"","url":"https://x.example/1","objectID":"1"},
              {"title":"Show HN: LLM tool","url":"https://x.example/2","objectID":"2"}
            ]}
            """;
        assertThat(HackerNewsNewsFetcher.parseResponse(json)).hasSize(1);
    }
}
