# 文档阅读器设计规格

> 日期:2026-08-13　状态:已敲定(经 brainstorming + grilling 两轮压力测试)
> 所属:前端收尾 · 子项目 1/4(文档阅读器 → 亮色模式 → 动画打磨 → 延后项补做)
> 基准:系统设计文档「知识库 · 文档阅读器(左侧文件树导航,右侧渲染阅读,支持大纲跳转)」

---

## 一、背景与目标

知识库目前只有上传/列表/语义搜索,上传的文档无法阅读。本规格实现:从知识库页点击文档进入独立阅读页,左侧文件树(知识库→文档)导航,右侧按格式渲染正文,md 文档支持大纲跳转与当前章节高亮。

## 二、范围

**做**:

- 后端:全文落库 + 「取内容」「取原文件」两个接口
- 前端:阅读器路由与页面、两级文件树、分格式渲染、md 大纲抽屉(点击跳转 + 滚动高亮)、下载原文、收藏文档阅读
- 测试:后端接口测试 + 前端单测/冒烟;验收标准见第九节

**不做(YAGNI,有意排除)**:

- 阅读进度记忆、语义搜索命中定位跳转
- 文档编辑、重命名、阅读器内删除/重传(删除在知识库页)
- 多文档对比、txt/Word 大纲提取
- 前端 e2e(Playwright 未引入,维持现状)

## 三、已敲定决策(Grilling 记录)

| # | 决策点 | 结论 |
|---|--------|------|
| 1 | 渲染策略 | 分格式:md→MarkdownRenderer;pdf→iframe 原生查看器;txt/doc/docx→提取文本;收藏文档(BAGUWEN)→纯文本 |
| 2 | 大纲 | 仅 md;顶部悬浮「目录」按钮 → 右侧抽屉;点击平滑滚动;**首版即做滚动高亮当前章节** |
| 3 | 入口布局 | 独立路由 `/kb/:kbId/reader/:docId?`;左侧两级树(全部知识库→文档列表),右侧阅读区 |
| 4 | 存量文档 content 缺失 | 留空,阅读器提示「内容缺失,请重新上传」,不做回填 |
| 5 | /file 端点 | 对所有有原文件的文档开放;阅读器头部加「下载原文」;BAGUWEN 无原文件自动隐藏 |
| 6 | 树中非 READY 文档 | 显示但置灰禁用,带 PENDING/FAILED 状态徽标 |
| 7 | 阅读器操作面 | 只读,不提供删除/重新处理 |
| 8 | 测试深度 | vitest 单测 + 视图冒烟,不引入 e2e |

## 四、后端设计

### 4.1 迁移

`V5__document_content.sql`:`ALTER TABLE document ADD COLUMN content TEXT;`

`Document` 实体加 `content` 字段。

### 4.2 落库点

1. `DocumentService.process()`:Tika 提取出 `text` 后 `doc.setContent(text)`,与 `READY`/`chunkCount` 一并 `updateById`(现有删除竞态复查逻辑不变)。
2. `BaguwenService.favorite()`:构造的 Document 补 `doc.setContent(sb.toString())`(Q/A 拼接文本),使收藏文档可读。

### 4.3 新接口

**`GET /api/document/{id}/content`** → `Result<DocumentContentView>{id, title, fileType, content}`

- 文档不存在 → 抛 `IllegalArgumentException("文档不存在: " + id)` → 400(沿用全局异常处理)
- 非 READY → 400,message 区分:「文档尚未处理完成」/「文档处理失败: {error}」
- content 为 null/blank → 400「文档内容缺失,请重新上传」

**`GET /api/document/{id}/file`** → `ResponseEntity<Resource>` 流式返回原文件

- Content-Type 映射:md/markdown→`text/markdown`;txt→`text/plain`;pdf→`application/pdf`;doc/docx→`application/octet-stream`;未知→`application/octet-stream`
- `Content-Disposition`:`ContentDisposition.builder("inline").filename(title, UTF_8)`(内嵌预览)
- filePath 为 null/blank(BAGUWEN)→ 400「该文档无原文件」
- 磁盘文件缺失 → 404(无包装)

安全:两接口均在既有 cookie 认证过滤器 `/api/**` 覆盖内;`filePath` 取自 DB,无用户输入,无路径穿越面。

### 4.4 后端测试

新 `DocumentContentTest`(复用现有 test fixture 模式,假 EmbeddingModel/ChatModel):

1. process 后 content 落库且与提取文本一致
2. GET content:READY→200 含全文;PENDING→400;FAILED→400(含 error);不存在→400;content 空→400
3. GET file:200 + Content-Type + inline;filePath null→400;磁盘文件被删→404
4. 现有 favorite 测试补断言:content 非空且含 Q/A 文本

## 五、前端设计

### 5.1 路由与入口

- 新路由 `{ path: '/kb/:kbId/reader/:docId?', name: 'kb-reader' }`(懒加载,登录 guard 已覆盖)
- 知识库页 READY 文档点击进入阅读器;PENDING/FAILED 点击无效果(与树内行为一致)

### 5.2 API

`api/document.ts` 新增:

- `getDocumentContent(id): Promise<DocumentContent>`
- `documentFileUrl(id): string` — 返回相对路径 `/api/document/${id}/file`(同源,cookie 自动携带,iframe 直用;dev 走现有 vite 代理)

### 5.3 视图结构(`DocumentReaderView.vue`)

- **数据**:复用现有 `useDocumentStore`(kbs/documents/selectKb/loadDocuments,含轮询能力),不新建 store;挂载时 load,`route.params.kbId` 变化时切换
- **左树面板**(桌面 w-64 侧栏;移动端折叠为顶部一行,与桌面同构):
  - 顶部知识库 Select(现有 KB 列表)切换当前库
  - 文档列表:title + 状态徽标(READY 无徽标;PENDING/PROCESSING「处理中」置灰;FAILED「失败」置灰);当前选中高亮;READY 点击 `router.replace` 切换 docId(URL 可分享);切库后若当前 docId 不属于新库则清空选择
- **头部**:返回知识库、标题、格式徽标、chunk 数;右侧操作:「目录」按钮(仅 md)、「下载原文」(判定 `fileType !== 'BAGUWEN'`,即存在原文件)、「查看原文」外链(仅 BAGUWEN,sourceUrl)
- **内容区**按 fileType 分发:
  - `md/markdown` → `<MarkdownRenderer :content anchor-headings />`
  - `pdf` → `<iframe :src="documentFileUrl(id)">` 全高(原生查看器自带书签)
  - `txt` / `doc` / `docx` / 其他 → `<pre>` 提取文本,`whitespace-pre-wrap`
  - `BAGUWEN` → `<pre>` Q/A 文本
- **空态**:未选文档(提示从左侧选择)/ 库无文档(引导去上传)/ content 缺失(提示重新上传)
- **错误处理**:getContent 400 → 按 message 提示;「文档不存在」→ 清空 docId 并刷新树;网络失败 → message 提示

### 5.4 大纲(md 专属)

- 新增纯函数模块 `utils/outline.ts`:`slugify(text)`(重复标题加 `-2` 后缀)、`buildOutline(content): {text, level, id}[]`(markdown-it token 流提取 h1~h3)
- `MarkdownRenderer` 加**可选** prop `anchor-headings`(默认 false,Chat 现有用法零影响):开启时用同一 `slugify` 给标题注入 id(DOMPurify 保留 id)
- 顶部悬浮「目录」按钮(阅读区右上)→ Naive UI `Drawer` 右侧抽屉:标题列表按层级缩进,当前章节高亮
- 点击标题:`scrollIntoView({behavior:'smooth'})` + 关闭抽屉
- **滚动高亮**:滚动监听(rAF 节流)+ `computeActiveId` 纯函数:取 top 不超过基准线(容器顶 +120px)的最后一个标题,更新抽屉高亮

## 六、数据流

```
进入 /kb/:kbId/reader[/:docId]
  → store.loadKbs + selectKb(kbId)      # 树数据
  → 选中 docId → getDocumentContent(id) # 内容数据
      → 分发渲染(md / pdf / text / baguwen)
      → md: buildOutline(content) → 抽屉列表 + anchor 渲染
  → 切换文档/知识库 → router.replace / selectKb → 重复以上
```

## 七、错误处理汇总

| 场景 | 行为 |
|------|------|
| 文档不存在(已删/竞态) | 400 message → 提示 + 清空 docId + 刷新树 |
| 非 READY 文档 | 树内已禁用,兜底 400 → 提示等待/查看失败原因 |
| 存量文档 content 缺失 | 提示「内容缺失,请重新上传」 |
| pdf 原文件缺失(404) | iframe 显示浏览器默认错误页,不特殊处理(极罕见) |
| 网络失败 | message 提示,可重试(重新选择) |

## 八、文件清单

**后端**:`V5__document_content.sql`、`Document`(+content)、`DocumentService`(+setContent)、`BaguwenService`(+setContent)、`DocumentController`(+2 接口)、新 DTO `DocumentContentView`、新测试 `DocumentContentTest`、`BaguwenServiceTest`(+1 断言)

**前端**:`router/index.ts`(+1 路由)、`api/document.ts`(+2)、`utils/outline.ts`(新)、`utils/__tests__/outline.test.ts`(新)、`components/MarkdownRenderer.vue`(+可选 prop)、`views/DocumentReaderView.vue`(新)、`views/KnowledgeBaseView.vue`(+点击入口)

## 九、验收标准

1. **自动化**:后端新增测试全绿,现有 74 个不回归;前端新单测/冒烟全绿,现有 30 个不回归;typecheck 无输出;build 成功
2. **真机冒烟**:① 上传 md → 阅读器渲染 + 目录点击跳转 + 滚动高亮 ② 上传 pdf → iframe 预览 ③ 上传 docx → 提取文本阅读 ④ 八股文收藏 → 阅读器可读 + 原文外链
3. **回归**:Chat 页 MarkdownRenderer 行为不变(anchor-headings 默认关)
