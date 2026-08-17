package com.neuralbase.news;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GithubSearchClientTest {

    @Test
    void buildUrlEncodesQueryWithSpaceSeparator() {
        // 回归:限定符分隔用空格(编码后为 +);若查询里自带 "+" 会变 %2B,GitHub 将日期解析为 "YYYY-MM-DD+topic:ai" 返回 422
        String url = GithubSearchClient.buildUrl("created:>2026-08-10 topic:ai", 20);
        assertThat(url).contains("q=created%3A%3E2026-08-10+topic%3Aai");
        assertThat(url).doesNotContain("%2B");
        assertThat(url).contains("sort=stars&order=desc&per_page=20");
    }
}
