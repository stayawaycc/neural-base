package com.neuralbase.controller;

import com.neuralbase.common.Result;
import com.neuralbase.entity.Kb;
import com.neuralbase.service.KbService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/kb")
public class KbController {

    private final KbService kbService;

    public KbController(KbService kbService) {
        this.kbService = kbService;
    }

    @GetMapping
    public Result<List<Kb>> list() {
        return Result.ok(kbService.list());
    }

    @GetMapping("/{id}")
    public Result<Kb> get(@PathVariable Long id) {
        return Result.ok(kbService.getById(id));
    }

    @PostMapping
    public Result<Kb> create(@RequestBody Kb kb) {
        kbService.save(kb);
        return Result.ok(kb);
    }

    @PutMapping("/{id}")
    public Result<Kb> update(@PathVariable Long id, @RequestBody Kb kb) {
        kb.setId(id);
        kbService.updateById(kb);
        return Result.ok(kbService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        kbService.removeById(id);
        return Result.ok(null);
    }
}
