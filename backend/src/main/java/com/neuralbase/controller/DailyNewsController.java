package com.neuralbase.controller;

import com.neuralbase.common.Result;
import com.neuralbase.entity.CrawlTask;
import com.neuralbase.entity.CrawlerSource;
import com.neuralbase.service.DailyNewsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/news")
public class DailyNewsController {

    private final DailyNewsService dailyNewsService;

    public DailyNewsController(DailyNewsService dailyNewsService) {
        this.dailyNewsService = dailyNewsService;
    }

    @GetMapping
    public Result<DailyNewsService.NewsPage> list(@RequestParam(required = false) String category,
                                                  @RequestParam(required = false) Boolean unreadOnly,
                                                  @RequestParam(required = false) Long sourceId,
                                                  @RequestParam(required = false) String sort,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "20") long size) {
        return Result.ok(dailyNewsService.list(category, unreadOnly, sourceId, sort, page, size, startDate, endDate));
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

    @PostMapping("/batch-delete")
    public Result<Integer> batchDelete(@RequestBody BatchDeleteRequest req) {
        return Result.ok(dailyNewsService.deleteByIds(req == null ? null : req.ids()));
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

    @PostMapping("/cleanup")
    public Result<Long> cleanup() {
        return Result.ok(dailyNewsService.cleanupNonAi());
    }

    @GetMapping("/source")
    public Result<List<CrawlerSource>> sources() {
        return Result.ok(dailyNewsService.sources());
    }

    @PostMapping("/source")
    public Result<CrawlerSource> createSource(@RequestBody SourceRequest req) {
        return Result.ok(dailyNewsService.createSource(
            req == null ? null : req.name(), req == null ? null : req.url(),
            req == null ? null : req.selector(), req == null ? null : req.enabled()));
    }

    @PutMapping("/source/{id}")
    public Result<CrawlerSource> updateSource(@PathVariable Long id, @RequestBody SourceRequest req) {
        return Result.ok(dailyNewsService.updateSource(id,
            req == null ? null : req.name(), req == null ? null : req.url(),
            req == null ? null : req.selector(), req == null ? null : req.enabled()));
    }

    @DeleteMapping("/source/{id}")
    public Result<Void> deleteSource(@PathVariable Long id) {
        dailyNewsService.deleteSource(id);
        return Result.ok(null);
    }

    public record ReadRequest(Boolean isRead) {
    }

    public record FavoriteRequest(Long kbId) {
    }

    public record SourceRequest(String name, String url, String selector, Boolean enabled) {
    }

    public record BatchDeleteRequest(List<Long> ids) {
    }
}
