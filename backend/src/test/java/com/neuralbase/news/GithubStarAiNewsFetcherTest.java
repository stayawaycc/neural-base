package com.neuralbase.news;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GithubStarAiNewsFetcherTest {

    @Test
    void parsesSearchResponse() {
        String json = """
            {"total_count":2,"items":[
              {"full_name":"org/repo1","html_url":"https://github.com/org/repo1","stargazers_count":1234},
              {"full_name":"org/repo2","html_url":"https://github.com/org/repo2","stargazers_count":567}
            ]}
            """;
        var items = GithubStarAiNewsFetcher.parseResponse(json);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("org/repo1");
        assertThat(items.get(0).url()).isEqualTo("https://github.com/org/repo1");
        assertThat(items.get(0).sourceName()).isEqualTo("GitHub Star AI");
    }

    @Test
    void invalidJsonYieldsEmpty() {
        assertThat(GithubStarAiNewsFetcher.parseResponse("not json")).isEmpty();
    }
}
