package com.neuralbase.crawler;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.Playwright;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 真实爬虫组件仅在非 test 环境注册，测试由 MockBaguwenConfig 的 @Primary mock 独占注入。 */
@Component
@Profile("!test")
public class PlaywrightCrawler implements ArticleCrawler {

    private static final int TIMEOUT_MS = 15_000;

    @Override
    public CrawledArticle fetch(String url) throws CrawlException {
        try (Playwright pw = Playwright.create();
             Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();
            page.setDefaultTimeout(TIMEOUT_MS);
            page.navigate(url);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            return new CrawledArticle(page.title().strip(), url, page.content());
        } catch (Exception e) {
            throw new CrawlException("Playwright 渲染失败: " + url + " -> " + e.getMessage()
                + "（若未安装浏览器，请先执行 playwright install chromium）", e);
        }
    }
}
