package com.neuralbase.scheduler;

import com.neuralbase.service.DailyNewsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyNewsJob {

    private static final Logger log = LoggerFactory.getLogger(DailyNewsJob.class);

    private final DailyNewsService dailyNewsService;

    public DailyNewsJob(DailyNewsService dailyNewsService) {
        this.dailyNewsService = dailyNewsService;
    }

    @Scheduled(cron = "${neuralbase.news.cron:0 0 8 * * *}")
    public void run() {
        try {
            dailyNewsService.startCrawl(); // 已有 RUNNING 会抛异常,定时任务静默跳过
            dailyNewsService.crawlAsync();
        } catch (Exception e) {
            log.info("跳过定时爬取: {}", e.getMessage());
        }
    }
}
