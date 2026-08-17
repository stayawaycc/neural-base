# 八股文导入任务「失败可删 + 分页」设计规格

> 日期:2026-08-17　状态:已敲定(经 grilling 两轮 + 真机差分定位)
> 所属:Phase 4 八股文修正与增强

---

## 一、问题与根因(已实测定位)

| # | 问题 | 实测根因 |
|---|------|----------|
| 1 | 点击删除任务的确认后**直接退出登录** | 双层根因:① 用户运行中的后端是**旧构建**(无 `DELETE /api/baguwen/tasks/{id}` 路由)→ 404;② **Spring Security 6(STATELESS)下,容器级错误(404/405/415…)触发 ERROR 二次分发,二次分发重新进入安全链但无认证上下文 → 401 入口点把原始错误响应篡改成 401**(真机证据:401 响应携带 `Allow` 头,是 405 机器遗留)→ 前端 axios 拦截器见 401 即清登录态跳登录页。磁盘当前代码的删除端点本身正常(真机差分:新实例 DELETE 不存在任务 → 400「任务不存在」) |
| 2 | 失败任务删除不可用/入口隐蔽 | 删除图标 hover 才显示;且仅 FAILED 可删,SUCCESS 历史任务无法从面板移除 |
| 3 | 任务列表无分页 | `GET /api/baguwen/tasks` 返回全量,前端渲染全部历史任务 |

## 二、已敲定决策(Grilling 记录)

| # | 决策点 | 结论 |
|---|--------|------|
| 1 | 退出登录 bug | 修根因②:`/error` 加入 permitAll,容器级错误按真实状态码返回;用户旧构建经重启即得新路由 |
| 2 | 删除范围 | FAILED + SUCCESS 均可删(**只删任务行,已导入文章保留**);PENDING/PROCESSING 仍禁删(与异步线程竞争) |
| 3 | 批量清理 | 任务面板「清理失败任务」按钮 → `DELETE /api/baguwen/tasks?status=FAILED` → 返回删除条数,toast 提示;status 仅允许 FAILED/SUCCESS |
| 4 | 任务分页 | 后端 page/size(默认 10)+ total;前端面板底部 n-pagination;任务页最新在前;不加状态筛选 |
| 5 | 轮询适配 | 轮询期改为查 `status=PENDING,PROCESSING`(多状态)取 total 判断是否继续;导入/重试后任务页回第 1 页 |
| 6 | 删除入口 | 删除图标**常显**(去掉 hover 隐藏);FAILED 与 SUCCESS 行都显示 |

## 三、后端设计

### 3.1 SecurityConfig(根因②修复)

- `authorizeHttpRequests` 增 `.requestMatchers("/error").permitAll()`(置于 anyRequest 之前)
- 真实端口(RANDOM_PORT)回归测试:带 token 访问不存在的路径 → **404**(而非 401);无 token 访问受保护路径 → 401 不变

### 3.2 BaguwenService / Controller

| 接口 | 变更 |
|------|------|
| `GET /api/baguwen/tasks` | 增 `page`(默认 1)/`size`(默认 10,上限 100);`status` 支持逗号分隔多状态(如 `PENDING,PROCESSING`);返回 `Page<BaguwenImportTask>` |
| `DELETE /api/baguwen/tasks/{id}` | 允许 FAILED/SUCCESS;PENDING/PROCESSING 抛 400「仅 FAILED/SUCCESS 任务可删除」 |
| `DELETE /api/baguwen/tasks?status=`(新) | 批量删除指定终态(仅 FAILED/SUCCESS)任务,返回删除条数 |

## 四、前端设计

- `api/baguwen.ts`:`listTasks({status?,page?,size?})` 返回 `Page<ImportTask>`;新增 `clearTasks(status)` → `number`
- `stores/baguwen.ts`:任务分页状态 `taskPage/taskSize=10/taskTotal`;轮询回调改查多状态 total(`status: 'PENDING,PROCESSING', size: 1`)判断继续;导入/重试 → `taskPage=1`;`removeTask`/`batchClearFailed` 后重载当前页
- `BaguwenView.vue`:任务面板底部 n-pagination(总任务数 > 10 显示);面板头部「清理失败任务」按钮(NPopconfirm);任务行删除图标常显(FAILED/SUCCESS 行,重试按钮仍仅 FAILED)

## 五、测试与验收

- 后端(TDD):错误分发回归(真实端口 404/405 不被篡改)、删除 SUCCESS、批量清理(计数/非法 status 400/多状态分页)、任务分页 page/size/total;既有 126 不回归
- 前端(Vitest):store 分页/轮询/批量清理
- 真实冒烟(8082 新实例):矩阵复测 —— GET 不存在路径 404、DELETE 不存在任务 400、POST import 空 urls 400(全部真实状态码);前端 CDP 验收任务分页与清理按钮
