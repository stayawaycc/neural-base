package com.neuralbase.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class ContentExtractor {

    private static final int MIN_CONTENT_CHARS = 100;
    private static final String NOISE_SELECTOR =
        "script, style, noscript, iframe, nav, aside, footer, header, form, button, " +
        ".nav, .navbar, .menu, .breadcrumb, .pagination, .ad, .ads, .advert, .advertisement, " +
        ".banner, .recommend, .related, .share, .like, .comment, .comments, .reply, " +
        "[class*=ad-], [id*=ad-], [class*=banner], [class*=recommend], " +
        "div[id*=comment], div[class*=comment]";

    /** 从原始 HTML 提取正文纯文本：先剥噪音标签，再对候选容器做文本密度评分选最优。 */
    public String extract(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parse(html);
        doc.select(NOISE_SELECTOR).remove();

        Element best = null;
        int bestScore = -1;
        for (Element cand : doc.select("article, main, [class*=article], [class*=content], "
            + "[class*=post], [id*=content], [id*=article], body")) {
            int s = score(cand);
            if (s > bestScore) {
                bestScore = s;
                best = cand;
            }
        }
        Element container = best == null ? doc.body() : best;
        if (container == null) {
            return "";
        }
        String text = container.text().strip();
        if (text.length() < MIN_CONTENT_CHARS && container != doc.body() && doc.body() != null) {
            text = doc.body().text().strip();
        }
        return text;
    }

    private int score(Element el) {
        int score = 0;
        for (Element p : el.select("p, li, pre, h1, h2, h3, h4")) {
            score += Math.min(p.text().length(), 200);
            if (p.tagName().startsWith("h")) {
                score += 10;
            }
            if (p.tagName().equals("pre")) {
                score += 5;
            }
        }
        return score;
    }
}
