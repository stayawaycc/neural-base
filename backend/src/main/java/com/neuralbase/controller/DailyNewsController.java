package com.neuralbase.controller;

import com.neuralbase.common.Result;
import com.neuralbase.entity.CrawlTask;
import com.neuralbase.entity.DailyNews;
import com.neuralbase.service.DailyNewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/news")
public class DailyNewsController {

    private final DailyNewsService dailyNewsService;

    public DailyNewsController(DailyNewsService dailyNewsService) {
        this.dailyNewsService = dailyNewsService;
    }

    @GetMapping
    public Result<List<DailyNews>> list(@RequestParam(required = false) String category,
                                        @RequestParam(required = false) Boolean unreadOnly,
                                        @RequestParam(required = false) Long sourceId,
                                        @RequestParam(required = false) String sort) {
        return Result.ok(dailyNewsService.list(category, unreadOnly, sourceId, sort));
    }

    @PostMapping("/crawl")
    public Result<Void> crawl() {
        dailyNewsService.startCrawl();
        dailyNewsService.crawlAsync();
        return Result.ok(null);
    }

    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id, @RequestBody ReadRequest req) {
        dailyNewsService.markRead(id, req == null || Boolean.TRUE.equals(req.isRead()));
        return Result.ok(null);
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        dailyNewsService.markAllRead();
        return Result.ok(null);
    }

    @PostMapping("/{id}/favorite")
    public Result<Void> favorite(@PathVariable Long id, @RequestBody FavoriteRequest req) {
        dailyNewsService.favorite(id, req == null ? null : req.kbId());
        return Result.ok(null);
    }

    @GetMapping("/task/latest")
    public Result<CrawlTask> latestTask() {
        return Result.ok(dailyNewsService.latestTask());
    }

    public record ReadRequest(Boolean isRead) {
    }

    public record FavoriteRequest(Long kbId) {
    }
}
