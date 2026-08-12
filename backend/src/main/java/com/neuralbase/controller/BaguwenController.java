package com.neuralbase.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.neuralbase.common.Result;
import com.neuralbase.dto.BaguwenArticleView;
import com.neuralbase.dto.BaguwenDetail;
import com.neuralbase.dto.BaguwenFavoriteRequest;
import com.neuralbase.dto.BaguwenImportRequest;
import com.neuralbase.dto.BaguwenTagCount;
import com.neuralbase.dto.FavoriteResult;
import com.neuralbase.entity.BaguwenImportTask;
import com.neuralbase.service.BaguwenService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/baguwen")
public class BaguwenController {

    private final BaguwenService baguwenService;

    public BaguwenController(BaguwenService baguwenService) {
        this.baguwenService = baguwenService;
    }

    @PostMapping("/import")
    public Result<List<BaguwenImportTask>> importUrls(@RequestBody BaguwenImportRequest req) {
        List<BaguwenImportTask> tasks = baguwenService.importUrls(req.urls());
        tasks.forEach(t -> baguwenService.processTask(t.getId()));   // 跨 Bean 调用走代理，@Async 生效
        return Result.ok(tasks);
    }

    @GetMapping("/tasks")
    public Result<List<BaguwenImportTask>> tasks(@RequestParam(required = false) String status) {
        return Result.ok(baguwenService.tasks(status));
    }

    @PostMapping("/tasks/{id}/retry")
    public Result<BaguwenImportTask> retry(@PathVariable Long id) {
        BaguwenImportTask task = baguwenService.retry(id);
        baguwenService.processTask(task.getId());
        return Result.ok(task);
    }

    @GetMapping
    public Result<Page<BaguwenArticleView>> list(@RequestParam(required = false) String tag,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size) {
        return Result.ok(baguwenService.list(tag, keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<BaguwenDetail> detail(@PathVariable Long id) {
        return Result.ok(baguwenService.detail(id));
    }

    @GetMapping("/tags")
    public Result<List<BaguwenTagCount>> tags() {
        return Result.ok(baguwenService.tagCounts());
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        baguwenService.delete(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/favorite")
    public Result<FavoriteResult> favorite(@PathVariable Long id, @RequestBody BaguwenFavoriteRequest req) {
        return Result.ok(baguwenService.favorite(id, req.kbId()));
    }
}
