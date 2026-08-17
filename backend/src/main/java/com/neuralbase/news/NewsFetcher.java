package com.neuralbase.news;

import java.util.List;

public interface NewsFetcher {
    List<NewsItem> fetch();

    /** 内置源标识(与 crawler_source.name 对应);Generic 抓取器返回 null。 */
    default String sourceName() {
        return null;
    }

    /** 该源上游未保证 AI 主题时返回 true,入库前按标题关键词兜底过滤。 */
    default boolean needsAiFilter() {
        return false;
    }
}
