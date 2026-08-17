# 八股文任务「失败可删 + 分页」实施计划

**Goal:** 修复删除任务触发退出登录(错误响应被 401 劫持)→ 任务删除放宽到 FAILED+SUCCESS + 批量清理失败任务 → 任务列表分页。

**规格:** `docs/superpowers/specs/2026-08-17-baguwen-tasks-delete-pagination-design.md`

**约定:** TDD;`cd backend && DB_PASSWORD=root mvn test`;前端 `node_modules\.bin\vitest.cmd run`。

---

## Task 1:SecurityConfig /error permitAll(根因修复,TDD)

- Modify: `backend/src/main/java/com/neuralbase/config/SecurityConfig.java`
- Test: `backend/src/test/java/com/neuralbase/security/ErrorDispatchSecurityTest.java`(新,`@SpringBootTest(webEnvironment = RANDOM_PORT)` + test profile + JDK HttpClient)

```java
.authorizeHttpRequests(a -> a
    .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/health", "/error").permitAll()
    .anyRequest().authenticated())
```

测试(真实端口,走真实 Tomcat 的 ERROR 二次分发):
- 登录拿 token → GET `/api/not-exist-xyz` → **404**(MockMvc 无法复现此 bug,必须真实端口)
- 无 token → GET `/api/news` → 401(行为不变)

Run: `DB_PASSWORD=root mvn test -Dtest=ErrorDispatchSecurityTest -q`
Commit: `fix(security): permit /error so container error dispatch is not hijacked into 401`

## Task 2:任务删除放宽 + 批量清理 + 分页(TDD)

- Modify: `BaguwenService`(deleteTask/deleteTasksByStatus/tasks)+ `BaguwenController`
- Test: `BaguwenControllerTest` / `BaguwenServiceTest`(扩展)

`tasks(String status, long page, long size)`:

```java
public Page<BaguwenImportTask> tasks(String status, long page, long size) {
    long safePage = Math.max(page, 1);
    long safeSize = Math.min(Math.max(size, 1), 100);
    LambdaQueryWrapper<BaguwenImportTask> w = new LambdaQueryWrapper<>();
    if (status != null && !status.isBlank()) {
        w.in(BaguwenImportTask::getStatus, Arrays.stream(status.split(","))
            .map(String::strip).filter(s -> !s.isEmpty()).toList());
    }
    w.orderByDesc(BaguwenImportTask::getId);
    return taskMapper.selectPage(new Page<>(safePage, safeSize), w);
}
```

`deleteTask`:`if (!List.of("FAILED","SUCCESS").contains(task.getStatus())) throw new IllegalArgumentException("仅 FAILED/SUCCESS 任务可删除,当前状态: " + ...)`

`deleteTasksByStatus(String status)`:仅 FAILED/SUCCESS;`taskMapper.delete(wrapper.eq(status))` 返回 count。

Controller:`@GetMapping("/tasks")` 加 page/size;`@DeleteMapping("/tasks")` `deleteTasksByStatus(@RequestParam String status)` → `Result<Long>`。

测试:删除 SUCCESS 任务 200、删除 PENDING 400、批量 FAILED 计数、批量非法 status 400、15 条任务 page=2/size=10 → 5 条 + total=15、多状态 `PENDING,PROCESSING` 过滤。

Run: `DB_PASSWORD=root mvn test -Dtest=BaguwenControllerTest,BaguwenServiceTest -q`
Commit: `feat(baguwen): task delete for FAILED/SUCCESS, batch clear failed, paginated task list`

## Task 3:前端(TDD)

- Modify: `frontend/src/api/baguwen.ts`、`frontend/src/stores/baguwen.ts`、`frontend/src/views/BaguwenView.vue`、`frontend/src/stores/__tests__/baguwen.test.ts`

store 核心:

```ts
const taskFilter = reactive({ page: 1, size: 10 })
const taskTotal = ref(0)
async function loadTasksPage() {
  const page = await baguwenApi.listTasks({ page: taskFilter.page, size: taskFilter.size })
  tasks.value = page.records
  taskTotal.value = page.total
}
// 轮询:多状态 total 判停
const { start, stop } = usePolling(async () => {
  const pending = await baguwenApi.listTasks({ status: 'PENDING,PROCESSING', page: 1, size: 1 })
  await loadTasksPage()
  if (!pending.total) { stopPolling(); await loadArticles(); await loadTags() }
})
```

View:任务行删除图标常显(去掉 `opacity-0 group-hover:`),`v-if` 改为 `FAILED || SUCCESS`;面板头部「清理失败任务」NPopconfirm 按钮;面板底部 `n-pagination`(taskTotal > 10 显示)。

Run: vitest + typecheck + build
Commit: `feat(baguwen): task list pagination, always-visible delete, clear failed button`

## Task 4:全量回归 + 真机冒烟 + 文档

- 后端全量 + 前端全量;8082 新实例重启后矩阵复测(404/405/400 真实状态码,401 仅未认证时出现)
- 文档:PROGRESS/已完成事项/待优化事项补记(含「重启旧实例」提示)
