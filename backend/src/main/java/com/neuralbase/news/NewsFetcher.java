package com.neuralbase.news;

import java.util.List;

public interface NewsFetcher {
    List<NewsItem> fetch();

    /** 内置源标识(与 crawler_source.name 对应);Generic 抓取器返回 null。 */
    default String sourceName() {
        return null;
    }
}
