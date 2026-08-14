package com.neuralbase.config;

import com.neuralbase.news.NewsFetcher;
import com.neuralbase.news.NewsItem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
public class MockNewsConfig {

    @Bean
    @Primary
    public NewsFetcher mockGithubFetcher() {
        return () -> List.of(
            new NewsItem("repo-1", "https://github.com/a/b", "GitHub Trending"),
            new NewsItem("repo-2", "https://github.com/c/d", "GitHub Trending"));
    }

    @Bean
    @Primary
    public NewsFetcher mockHnFetcher() {
        return () -> List.of(new NewsItem("HN story", "https://news.ycombinator.com/item?id=9", "Hacker News"));
    }
}
