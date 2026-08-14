package com.neuralbase.news;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 机器之心文章列表:该站为 JS 渲染壳,用 Playwright 渲染后再解析(复用八股文爬虫同款模式)。 */
@Component
@Profile("!test")
public class JiQiZhiXinNewsFetcher implements NewsFetcher {

    private static final String ARTICLES_URL = "https://www.jiqizhixin.com/articles";

    @Override
    public List<NewsItem> fetch() {
        try (Playwright pw = Playwright.create();
             Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();
            page.setDefaultTimeout(45_000);
            page.navigate(ARTICLES_URL);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            return parseHtml(page.content(), ARTICLES_URL);
        } catch (Exception e) {
            throw new RuntimeException("机器之心渲染失败: " + e.getMessage()
                + "（若未安装浏览器，请先执行 playwright install chromium）", e);
        }
    }


    @Override
    public String sourceName() {
        return "机器之心";
    }

    static List<NewsItem> parseHtml(String html, String baseUri) {
        Document doc = Jsoup.parse(html, baseUri);
        List<NewsItem> items = new ArrayList<>();
        for (var a : doc.select("a[href*=/articles/]")) {
            String title = a.text().strip();
            if (title.isEmpty() || !a.absUrl("href").contains("/articles/")) continue;
            items.add(new NewsItem(title, a.absUrl("href"), "机器之心"));
        }
        return items;
    }
}
