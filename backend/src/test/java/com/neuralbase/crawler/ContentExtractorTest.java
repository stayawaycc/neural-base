package com.neuralbase.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentExtractorTest {

    private final ContentExtractor extractor = new ContentExtractor();

    @Test
    void extractsArticleBodyAndDropsNavigationAdsAndScripts() {
        String html = """
            <html><head><title>JVM 原理</title></head><body>
            <nav><a href="/">首页</a><a href="/tags">标签导航</a></nav>
            <script>alert('广告代码')</script>
            <div class="ad"><p>这里是广告内容</p></div>
            <div class="comment"><p>网友评论：写得不错</p></div>
            <article>
              <h1>JVM 内存模型详解</h1>
              <p>堆（Heap）是运行时数据区，存放对象实例。</p>
              <p>虚拟机栈描述 Java 方法执行的内存模型。</p>
              <p>方法区存储类信息、常量、静态变量。</p>
            </article>
            <footer>版权所有</footer>
            </body></html>
            """;

        String text = extractor.extract(html);

        assertThat(text).contains("JVM 内存模型详解");
        assertThat(text).contains("堆（Heap）是运行时数据区");
        assertThat(text).contains("方法区存储类信息");
        assertThat(text).doesNotContain("标签导航");
        assertThat(text).doesNotContain("广告代码");
        assertThat(text).doesNotContain("这里是广告内容");
        assertThat(text).doesNotContain("网友评论");
        assertThat(text).doesNotContain("版权所有");
    }

    @Test
    void returnsBlankForNullOrBlankHtml() {
        assertThat(extractor.extract(null)).isEmpty();
        assertThat(extractor.extract("   ")).isEmpty();
    }
}
