package com.neuralbase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neuralbase.entity.BaguwenImportTask;
import com.neuralbase.mapper.BaguwenImportTaskMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/** 启动断点续跑：把上次崩溃遗留的 PENDING/PROCESSING 任务重新排队（@Async 进程内重启丢失的补偿）。测试排除，避免共享库干扰。 */
@Component
@Profile("!test")
public class BaguwenStartupRunner implements ApplicationRunner {

    private final BaguwenImportTaskMapper taskMapper;
    private final BaguwenService baguwenService;

    public BaguwenStartupRunner(BaguwenImportTaskMapper taskMapper, BaguwenService baguwenService) {
        this.taskMapper = taskMapper;
        this.baguwenService = baguwenService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<BaguwenImportTask> pending = taskMapper.selectList(new LambdaQueryWrapper<BaguwenImportTask>()
            .in(BaguwenImportTask::getStatus, "PENDING", "PROCESSING"));
        for (BaguwenImportTask task : pending) {
            baguwenService.processTask(task.getId());   // 跨 Bean 调用走代理，@Async 生效
        }
    }
}
