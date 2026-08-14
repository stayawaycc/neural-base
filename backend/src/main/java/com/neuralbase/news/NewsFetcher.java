package com.neuralbase.news;

import java.util.List;

public interface NewsFetcher {
    List<NewsItem> fetch();
}
