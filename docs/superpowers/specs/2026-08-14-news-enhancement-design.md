# AI 日报增强设计规格

> 日期:2026-08-14　状态:已敲定(经 grilling 两轮压测)
> 所属:Phase 5 后续增强

---

## 一、已敲定决策(Grilling 记录)

| # | 决策点 | 结论 |
|---|--------|------|
| 1 | 排序 | 多维单键下拉:最新(默认)/ 最早 / 按来源 / 按分类;后端 `sort` 参数 |
| 2 | 时间展示 | 绝对时间 `YYYY-MM-DD HH:mm` |
| 3 | 来源筛选 | 后端 `sourceId` 参数 + 前端来源 chips(与 unreadOnly 一致,避 LIMIT 200 不一致) |
| 4 | GitHub 新源口径 | Search API `created:>{近7天}+topic:ai`,`sort=stars`,`top 20`(对「star 新增量」最接近的可实现口径) |
| 5 | 可配置爬取页面 | 后端 `/api/news/source` CRUD + 前端「来源管理」弹窗;通用 HTML fetcher 抓自建静态源 |
| 6 | 机器之心 | Playwright 渲染 articles 页(复用现有依赖与 PlaywrightCrawler 模式);浏览器缺失→失败容忍 |
| 7 | 权限边界 | 内置 5 源可停用不可删;自建源全权限(增删改启停) |
| 8 | 分页 | limit 200 不分页(YAGNI) |

## 二、后端设计

### 2.1 接口扩展

`GET /api/news` 新增参数:`sort` ∈ `created_desc`(默认)/`created_asc`/`source`/`category`;`sourceId`(按源筛选)。与 category/unreadOnly 可组合。

### 2.2 新 fetcher

- `GithubStarAiNewsFetcher`:GET `https://api.github.com/search/repositories?q=created:>{T-7天}+topic:ai&sort=stars&order=desc&per_page=20`(JDK HttpClient + ObjectMapper);NewsItem{full_name, html_url, "GitHub Star AI"};日期动态计算(本地日期减 7 天,格式 yyyy-MM-dd);fixture JSON 解析测试;真实验证受本机 TLS 环境限制(CRYPT_E_NO_REVOCATION_CHECK),如实注明
- `JiQiZhiXinNewsFetcher` 改造:fetch 用 Playwright(launch headless chromium → navigate articles 页 → page.content())→ 既有 `parseHtml` 解析;PlaywrightException 包装为 RuntimeException → 失败容忍;fixture 测试不变
- `GenericHtmlNewsFetcher`:读取 crawler_source 中 `enabled=true AND type='html'` 且**不在内置名单**的源;JDK HttpClient 抓 url → Jsoup 按 selector 提取 `a[href]`(title=text、url=absUrl);单源失败跳过;无条目记 warn

### 2.3 启停生效

- `NewsFetcher` 接口加 `default String sourceName() { return null; }`;7 个内置 fetcher 覆写
- `DailyNewsService.runCrawlNow()`:遍历注入的 fetcher 时,按 `sourceName()` 查 crawler_source,`enabled=false` 则跳过;`GenericHtmlNewsFetcher` 无内置名(返回 null)恒执行,内部自行过滤 enabled
- 种子源补齐「GitHub Star AI」行(name 幂等插入)

### 2.4 来源 CRUD

`DailyNewsController` 扩展(或新 `NewsSourceController`):
- `GET /api/news/source` → 全量源列表
- `POST /api/news/source` `{name,url,selector,enabled}` → 新建(type=html)
- `PUT /api/news/source/{id}` `{name,url,selector,enabled}` → 更新
- `DELETE /api/news/source/{id}` → 内置名单(name ∈ 内置 5 + GitHub Star AI)拒绝 400「内置源不可删除,可停用」
- 校验:name/url 非空;重复 name 400

## 三、前端设计

- `types/index.ts`:`NewsSort` 联合类型;`NewsSource` 类型(含 enabled/type/selector)
- `api/news.ts`:`listNews` 加 `sort`/`sourceId` 参数;`listSources/createSource/updateSource/deleteSource`
- `stores/news.ts`:加 `sort`、`sourceFilter` 状态;筛选/排序变化 `watch` 重新 `load()`;`sources` 状态与 CRUD 动作
- `DailyNewsView.vue`:
  - 工具栏:排序下拉(n-select:最新/最早/按来源/按分类)、来源 chips(动态 sources 名 + 全部)、「来源管理」按钮 → 弹窗
  - 卡片:绝对时间 `YYYY-MM-DD HH:mm`(createdAt 格式化)
  - 来源管理弹窗:列表(名称/URL/启用 n-switch/删除[仅自建,内置隐藏删除钮])+ 新增表单(名称/URL/selector 可选)
- 亮暗两态沿用既有映射

## 四、测试与验收

- 后端:`GithubStarAiNewsFetcher` fixture JSON 测试;`GenericHtmlNewsFetcher` fixture HTML + 假源行测试;source CRUD 测试(含内置不可删 400、重复名 400、启停过滤);`list` 排序/来源筛选测试;既有 110 不回归
- 前端:news store 扩展测试(sort/sourceFilter 触发 load、来源 CRUD);CDP 验收(排序切换、来源 chips、时间显示、来源管理弹窗增删改停)
- 真实冒烟:InfoQ/HN 排序筛选生效;新增自建源(用 InfoQ 页面实测 Generic 链路)后爬取入列表;GitHubStar/机器之心环境受限如实记录
