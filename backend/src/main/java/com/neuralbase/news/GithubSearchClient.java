package com.neuralbase.news;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** GitHub Search API 共用客户端(GitHub Trending / GitHub Star AI 两源复用)。 */
public final class GithubSearchClient {

    private static final String API = "https://api.github.com/search/repositories";

    /** 查询条件自行拼接;限定符分隔必须用空格(URL 编码后为 +),若原样写 "+" 会被编码为 %2B,GitHub 会返回 422。 */
    public static String searchJson(String query, int topN) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(buildUrl(query, topN)))
                .header("User-Agent", "NeuralBase")
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
            return HttpClient.newBuilder()
                .sslContext(WindowsTrustSslContext.create())
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString())
                .body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("GitHub Search 爬取失败", e);
        }
    }

    static String buildUrl(String query, int topN) {
        return API + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&sort=stars&order=desc&per_page=" + topN;
    }

    private GithubSearchClient() {
    }
}
