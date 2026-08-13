# 文档阅读器 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 知识库文档可读:独立阅读器页,左侧两级树(知识库→文档),右侧分格式渲染,md 支持目录抽屉(点击跳转 + 滚动高亮)。

**Architecture:** 后端新增 `content` 列存 Tika 提取全文,两个只读接口(取内容/取原文件);前端新路由 + 新视图 `DocumentReaderView.vue`,复用现有 `useDocumentStore` 与 `MarkdownRenderer`(新增可选 `anchor-headings` prop,默认关闭保证 Chat 零影响);大纲提取与滚动高亮计算放 `utils/outline.ts` 纯函数,可单测。

**Tech Stack:** Spring Boot 3.5.16 + MyBatis-Plus + PostgreSQL/Flyway;Vue 3.5 + TS + Pinia + Naive UI + markdown-it;vitest + happy-dom + @vue/test-utils。

**规格:** `docs/superpowers/specs/2026-08-13-document-reader-design.md`

## Global Constraints

- 不新增任何依赖(前后端 pom/package.json 不动)
- 后端测试类必须:`@SpringBootTest @ActiveProfiles("test") @Import(MockEmbeddingConfig.class)`;HTTP 测试另加 `@AutoConfigureMockMvc`;登录方式 POST `/api/auth/login` `{"username":"admin","password":"admin123"}`,token 取自响应 `data` 字段
- 后端业务错误一律抛 `IllegalArgumentException`(全局处理器转 400),返回统一 `Result<T>` 包裹;唯 `/file` 磁盘文件缺失用裸 `ResponseEntity.notFound()`
- 测试命令:后端 `cd backend && DB_PASSWORD=root mvn test`(bash;测试库 neuralbase_test 密码 root);前端 `cd frontend && npx vitest run`、`npx vue-tsc --noEmit`、`npm run build`
- 前端类型镜像放 `src/types/index.ts`;api 模块不写测试(沿用现有模式);组件测试模式参照 `components/__tests__/MarkdownRenderer.test.ts`
- UI 文案中文;复用 `glass-card` 全局样式与既有深色 token;`MarkdownRenderer` 现有行为不得改变(Chat 回归)
- commit 信息英文 subject,格式 `feat:` / `fix:` / `docs:`;提交时追加 Co-Authored-By 尾注

---

### Task 1: V5 迁移 + Document.content 字段 + process() 全文落库

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__document_content.sql`
- Modify: `backend/src/main/java/com/neuralbase/entity/Document.java`(加字段)
- Modify: `backend/src/main/java/com/neuralbase/service/DocumentService.java:107-109`(setContent)
- Test: `backend/src/test/java/com/neuralbase/document/DocumentProcessTest.java:40-43`(加断言)

**Interfaces:**
- Consumes: 无(任务链起点)
- Produces: `Document.getContent()/setContent(String)` — Task 3/4 与前端依赖;`document` 表 `content TEXT` 列

- [ ] **Step 1: 写失败测试**

修改 `DocumentProcessTest.uploadThenProcessMarksReadyAndStoresChunks`,在 `assertThat(after.getChunkCount()).isGreaterThan(0);` 之后加一行:

```java
assertThat(after.getContent()).contains("Java 虚拟线程详解内容");
```

- [ ] **Step 2: 运行确认失败(编译失败即红)**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=DocumentProcessTest`
Expected: 编译失败 — `Document` 无 `getContent()` 方法

- [ ] **Step 3: 写最小实现**

新建 `backend/src/main/resources/db/migration/V5__document_content.sql`:

```sql
ALTER TABLE document ADD COLUMN content TEXT;
```

`Document.java` 在 `private String error;` 前加:

```java
    private String content;
```

`DocumentService.process()` 中,把:

```java
            doc.setStatus("READY");
            doc.setChunkCount(chunks.size());
            documentMapper.updateById(doc);
```

改为:

```java
            doc.setStatus("READY");
            doc.setChunkCount(chunks.size());
            doc.setContent(text);
            documentMapper.updateById(doc);
```

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=DocumentProcessTest`
Expected: PASS(Flyway 自动把 V5 应用到 neuralbase_test)

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/resources/db/migration/V5__document_content.sql backend/src/main/java/com/neuralbase/entity/Document.java backend/src/main/java/com/neuralbase/service/DocumentService.java backend/src/test/java/com/neuralbase/document/DocumentProcessTest.java
git commit -m "feat: persist extracted full text in document.content"
```

---

### Task 2: favorite() 收藏落库 content

**Files:**
- Modify: `backend/src/main/java/com/neuralbase/service/BaguwenService.java:274-281`(insert 前 setContent)
- Test: `backend/src/test/java/com/neuralbase/baguwen/BaguwenServiceTest.java:126-132`(加断言)

**Interfaces:**
- Consumes: `document.content` 列(Task 1)
- Produces: 收藏文档 content 非空 — Task 3 内容接口对收藏文档返回 200 的前提

- [ ] **Step 1: 写失败测试**

`BaguwenServiceTest.favoriteEmbedsQaTextIntoKb` 中,在 `assertThat(docId).isNotNull();` 之后加:

```java
        String content = jdbcTemplate.queryForObject(
            "SELECT content FROM document WHERE id = ?", String.class, docId);
        assertThat(content).contains("Q: ").contains("A: ");
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=BaguwenServiceTest#favoriteEmbedsQaTextIntoKb`
Expected: FAIL — content 为 NULL

- [ ] **Step 3: 写最小实现**

`BaguwenService.favorite()` 中,`documentMapper.insert(doc);` 前加一行:

```java
        doc.setContent(sb.toString());
```

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=BaguwenServiceTest`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/neuralbase/service/BaguwenService.java backend/src/test/java/com/neuralbase/baguwen/BaguwenServiceTest.java
git commit -m "feat: persist favorite qa text as document content"
```

---

### Task 3: GET /api/document/{id}/content

**Files:**
- Create: `backend/src/main/java/com/neuralbase/dto/DocumentContentView.java`
- Modify: `backend/src/main/java/com/neuralbase/service/DocumentService.java`(加 `content(Long)` 方法)
- Modify: `backend/src/main/java/com/neuralbase/controller/DocumentController.java`(加 GET 端点)
- Test: `backend/src/test/java/com/neuralbase/document/DocumentContentTest.java`(新建)

**Interfaces:**
- Consumes: `Document.content`(Task 1)、既有 `Result`/`IllegalArgumentException` 约定
- Produces: `GET /api/document/{id}/content` → `Result<DocumentContentView>`;`DocumentContentView(Long id, String title, String fileType, String content)` — Task 10 前端 `getDocumentContent` 消费

- [ ] **Step 1: 写失败测试**

新建 `backend/src/test/java/com/neuralbase/document/DocumentContentTest.java`:

```java
package com.neuralbase.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.config.MockEmbeddingConfig;
import com.neuralbase.entity.Document;
import com.neuralbase.mapper.DocumentMapper;
import com.neuralbase.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(MockEmbeddingConfig.class)
class DocumentContentTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DocumentService documentService;
    @Autowired
    private DocumentMapper documentMapper;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asText();
    }

    private Document uploadDoc(String name, String text) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", name, MediaType.TEXT_MARKDOWN_VALUE, text.getBytes());
        return documentService.upload(file, 1L);
    }

    @Test
    void contentReturnsTextForReadyDoc() throws Exception {
        Document doc = uploadDoc("readme.md", "# 文档阅读器\n\n这是正文内容。");
        documentService.process(doc.getId());

        mvc.perform(get("/api/document/" + doc.getId() + "/content")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.id").value(doc.getId()))
            .andExpect(jsonPath("$.data.fileType").value("md"))
            .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("这是正文内容")));
    }

    @Test
    void contentForPendingReturns400() throws Exception {
        Document doc = uploadDoc("pending.md", "x");
        mvc.perform(get("/api/document/" + doc.getId() + "/content")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("尚未处理完成")));
    }

    @Test
    void contentForFailedReturns400WithError() throws Exception {
        Document doc = uploadDoc("bad.md", "x");
        doc.setStatus("FAILED");
        doc.setError("Tika 解析失败");
        documentMapper.updateById(doc);
        mvc.perform(get("/api/document/" + doc.getId() + "/content")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("处理失败")));
    }

    @Test
    void contentForMissingDocReturns400() throws Exception {
        mvc.perform(get("/api/document/999999/content")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不存在")));
    }

    @Test
    void contentEmptyReturns400() throws Exception {
        Document doc = uploadDoc("legacy.md", "x");
        doc.setStatus("READY");
        documentMapper.updateById(doc); // 模拟 V5 之前的存量文档:READY 但 content 为空
        mvc.perform(get("/api/document/" + doc.getId() + "/content")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("内容缺失")));
    }

    @Test
    void contentWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/document/1/content"))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=DocumentContentTest`
Expected: FAIL/编译失败 — 端点不存在(404 或编译错误)

- [ ] **Step 3: 写最小实现**

新建 `backend/src/main/java/com/neuralbase/dto/DocumentContentView.java`:

```java
package com.neuralbase.dto;

/** 文档阅读器「取内容」接口返回体(规格 4.3)。 */
public record DocumentContentView(Long id, String title, String fileType, String content) {
}
```

`DocumentService.java` 加方法(import 已有 DTO 风格,加到 `search` 方法之后):

```java
    public DocumentContentView content(Long documentId) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + documentId);
        }
        if (!"READY".equals(doc.getStatus())) {
            throw new IllegalArgumentException("FAILED".equals(doc.getStatus())
                ? "文档处理失败: " + (doc.getError() == null ? "未知原因" : doc.getError())
                : "文档尚未处理完成");
        }
        if (doc.getContent() == null || doc.getContent().isBlank()) {
            throw new IllegalArgumentException("文档内容缺失,请重新上传");
        }
        return new DocumentContentView(doc.getId(), doc.getTitle(), doc.getFileType(), doc.getContent());
    }
```

补 import:`import com.neuralbase.dto.DocumentContentView;`

`DocumentController.java` 在 `@DeleteMapping("/{id}")` 前加:

```java
    @GetMapping("/{id}/content")
    public Result<DocumentContentView> content(@PathVariable Long id) {
        return Result.ok(documentService.content(id));
    }
```

补 import:`import com.neuralbase.dto.DocumentContentView;`

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=DocumentContentTest`
Expected: 6 个测试全 PASS

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/neuralbase/dto/DocumentContentView.java backend/src/main/java/com/neuralbase/service/DocumentService.java backend/src/main/java/com/neuralbase/controller/DocumentController.java backend/src/test/java/com/neuralbase/document/DocumentContentTest.java
git commit -m "feat: document content endpoint for reader"
```

---

### Task 4: GET /api/document/{id}/file

**Files:**
- Modify: `backend/src/main/java/com/neuralbase/service/DocumentService.java`(加 `fileDocument(Long)` 方法)
- Modify: `backend/src/main/java/com/neuralbase/controller/DocumentController.java`(加 GET 端点)
- Test: `backend/src/test/java/com/neuralbase/document/DocumentFileTest.java`(新建)

**Interfaces:**
- Consumes: `document.file_path` 列、`Result`/`IllegalArgumentException` 约定(Task 3)
- Produces: `GET /api/document/{id}/file` → `ResponseEntity<Resource>`(inline 流式);前端 Task 10 `documentFileUrl` 消费

- [ ] **Step 1: 写失败测试**

新建 `backend/src/test/java/com/neuralbase/document/DocumentFileTest.java`:

```java
package com.neuralbase.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.config.MockEmbeddingConfig;
import com.neuralbase.entity.Document;
import com.neuralbase.mapper.DocumentMapper;
import com.neuralbase.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(MockEmbeddingConfig.class)
class DocumentFileTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DocumentService documentService;
    @Autowired
    private DocumentMapper documentMapper;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asText();
    }

    @Test
    void fileStreamsOriginalBytesWithInlineDisposition() throws Exception {
        byte[] bytes = "# 标题\n\n正文".getBytes();
        Document doc = documentService.upload(new MockMultipartFile(
            "file", "笔记.md", MediaType.TEXT_MARKDOWN_VALUE, bytes), 1L);

        mvc.perform(get("/api/document/" + doc.getId() + "/file")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/markdown")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8")))
            .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(bytes));
    }

    @Test
    void fileReturnsPdfContentTypeWithoutProcessing() throws Exception {
        Document doc = documentService.upload(new MockMultipartFile(
            "file", "paper.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.4 fake".getBytes()), 1L);

        mvc.perform(get("/api/document/" + doc.getId() + "/file")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/pdf")));
    }

    @Test
    void fileForDocWithoutOriginalReturns400() throws Exception {
        Document doc = documentService.upload(new MockMultipartFile(
            "file", "favorite.md", MediaType.TEXT_MARKDOWN_VALUE, "x".getBytes()), 1L);
        // 注意:MyBatis-Plus updateById 默认忽略 null 字段,置空 file_path 必须走 SQL
        jdbcTemplate.update("UPDATE document SET file_path = NULL WHERE id = ?", doc.getId());

        mvc.perform(get("/api/document/" + doc.getId() + "/file")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isBadRequest())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("无原文件"));
    }

    @Test
    void fileForMissingDiskFileReturns404() throws Exception {
        Document doc = documentService.upload(new MockMultipartFile(
            "file", "gone.md", MediaType.TEXT_MARKDOWN_VALUE, "x".getBytes()), 1L);
        Files.deleteIfExists(Path.of(doc.getFilePath()));

        mvc.perform(get("/api/document/" + doc.getId() + "/file")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isNotFound());
    }

    @Test
    void fileWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/document/1/file"))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=DocumentFileTest`
Expected: FAIL — 端点不存在(404)

- [ ] **Step 3: 写最小实现**

`DocumentService.java` 加方法:

```java
    public Document fileDocument(Long documentId) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + documentId);
        }
        if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            throw new IllegalArgumentException("该文档无原文件");
        }
        return doc;
    }
```

`DocumentController.java` 加端点与私有辅助(import 见下方):

```java
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id) {
        Document doc = documentService.fileDocument(id);
        Path path = Path.of(doc.getFilePath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        ContentDisposition disposition = ContentDisposition.builder("inline")
            .filename(doc.getTitle(), StandardCharsets.UTF_8)
            .build();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentTypeFor(doc.getFileType())))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(new FileSystemResource(path));
    }

    private String contentTypeFor(String fileType) {
        return switch (fileType == null ? "" : fileType.toLowerCase()) {
            case "md", "markdown" -> "text/markdown";
            case "txt" -> "text/plain";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }
```

补 import(与已有 import 合并去重):

```java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
```

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=DocumentFileTest`
Expected: 5 个测试全 PASS

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/neuralbase/service/DocumentService.java backend/src/main/java/com/neuralbase/controller/DocumentController.java backend/src/test/java/com/neuralbase/document/DocumentFileTest.java
git commit -m "feat: document original file endpoint for pdf preview and download"
```

---

### Task 5: utils/outline.ts(slugify + buildOutline + computeActiveId)

**Files:**
- Create: `frontend/src/utils/outline.ts`
- Test: `frontend/src/utils/__tests__/outline.test.ts`(新建)

**Interfaces:**
- Consumes: `markdown-it`(已有依赖)
- Produces:
  - `slugify(text: string): string` — 小写、去特殊字符、空格转 `-`,空串兜底 `'section'`
  - `buildOutline(content: string): OutlineItem[]` — 按序提取 h1~h3,重复标题 id 加 `-2`、`-3` 后缀
  - `computeActiveId(headings: {id: string; top: number}[], lineTop: number): string | null` — 取 top ≤ lineTop 的最后一个
  - `interface OutlineItem { text: string; level: number; id: string }`
  - Task 6(MarkdownRenderer)与 Task 11(视图)消费

- [ ] **Step 1: 写失败测试**

新建 `frontend/src/utils/__tests__/outline.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { buildOutline, computeActiveId, slugify } from '@/utils/outline'

describe('slugify', () => {
  it('转小写、去特殊字符、空格转横线', () => {
    expect(slugify('Hello World 世界!')).toBe('hello-world-世界')
  })

  it('空串兜底 section', () => {
    expect(slugify('')).toBe('section')
  })
})

describe('buildOutline', () => {
  it('按序提取 h1~h3 层级与文本', () => {
    const items = buildOutline('# 总览\n\n## 环境\n\n### 依赖\n\n正文')
    expect(items).toEqual([
      { text: '总览', level: 1, id: '总览' },
      { text: '环境', level: 2, id: '环境' },
      { text: '依赖', level: 3, id: '依赖' },
    ])
  })

  it('重复标题 id 加 -2 后缀', () => {
    const items = buildOutline('## 安装\n\n## 安装')
    expect(items.map((i) => i.id)).toEqual(['安装', '安装-2'])
  })

  it('代码块内的 # 不算标题,空内容返回空数组', () => {
    expect(buildOutline('```\n# 不是标题\n```')).toEqual([])
    expect(buildOutline('')).toEqual([])
  })

  it('标题含链接时取链接文本', () => {
    const items = buildOutline('## [链接标题](https://example.com)')
    expect(items[0]).toMatchObject({ text: '链接标题', id: '链接标题' })
  })
})

describe('computeActiveId', () => {
  it('取 top 不超过基准线的最后一个标题', () => {
    const headings = [
      { id: 'a', top: 50 },
      { id: 'b', top: 100 },
      { id: 'c', top: 200 },
    ]
    expect(computeActiveId(headings, 120)).toBe('b')
    expect(computeActiveId(headings, 30)).toBeNull()
    expect(computeActiveId(headings, 500)).toBe('c')
  })
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/utils/__tests__/outline.test.ts`
Expected: FAIL — 找不到 `@/utils/outline` 模块

- [ ] **Step 3: 写最小实现**

新建 `frontend/src/utils/outline.ts`:

```ts
import MarkdownIt from 'markdown-it'

export interface OutlineItem {
  text: string
  level: number
  id: string
}

/** 标题文本 → 锚点 id:小写、去特殊字符、空格转横线(与 MarkdownRenderer 锚点注入共用同一算法)。 */
export function slugify(text: string): string {
  const base = text
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .replace(/\s+/g, '-')
  return base || 'section'
}

/** 提取 markdown 的 h1~h3 标题生成大纲;重复标题 id 依次加 -2/-3 后缀(与锚点注入顺序一致)。 */
export function buildOutline(content: string): OutlineItem[] {
  const tokens = new MarkdownIt().parse(content, {})
  const used = new Map<string, number>()
  const items: OutlineItem[] = []
  for (let i = 0; i < tokens.length; i++) {
    const t = tokens[i]
    if (t.type !== 'heading_open') continue
    const level = Number(t.tag.slice(1))
    if (level < 1 || level > 3) continue
    const inline = tokens[i + 1]
    const base = slugify(inline?.content ?? '')
    const count = (used.get(base) ?? 0) + 1
    used.set(base, count)
    items.push({ text: inline?.content ?? '', level, id: count === 1 ? base : `${base}-${count}` })
  }
  return items
}

/** 滚动高亮:取 top 不超过基准线(top ≤ lineTop)的最后一个标题 id。 */
export function computeActiveId(
  headings: { id: string; top: number }[],
  lineTop: number,
): string | null {
  let current: string | null = null
  for (const h of headings) {
    if (h.top <= lineTop) current = h.id
    else break
  }
  return current
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd frontend && npx vitest run src/utils/__tests__/outline.test.ts`
Expected: 8 个测试全 PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/src/utils/outline.ts frontend/src/utils/__tests__/outline.test.ts
git commit -m "feat: markdown outline utils (slugify, buildOutline, computeActiveId)"
```

---

### Task 6: MarkdownRenderer 可选 anchor-headings prop

**Files:**
- Modify: `frontend/src/components/MarkdownRenderer.vue`(工厂化 md 实例 + 新 prop)
- Test: `frontend/src/components/__tests__/MarkdownRenderer.test.ts`(加用例)

**Interfaces:**
- Consumes: `slugify`/`buildOutline`(Task 5)
- Produces: prop `anchor-headings?: boolean`(默认 false 行为不变)— Task 10/11 消费

- [ ] **Step 1: 写失败测试**

`MarkdownRenderer.test.ts` 末尾 `describe` 内加:

```ts
  it('anchor-headings 模式给标题注入与 buildOutline 一致的 id,默认模式不注入', () => {
    const anchored = mount(MarkdownRenderer, {
      props: { content: '# 甲\n\n## 乙\n\n## 乙', anchorHeadings: true },
    })
    const anchoredHtml = anchored.html()
    expect(anchoredHtml).toContain('id="甲"')
    expect(anchoredHtml).toContain('id="乙"')
    expect(anchoredHtml).toContain('id="乙-2"')

    const plain = mount(MarkdownRenderer, {
      props: { content: '# 甲' },
    })
    expect(plain.html()).not.toContain('id="甲"')
  })
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/components/__tests__/MarkdownRenderer.test.ts`
Expected: FAIL — 渲染产物无 id 属性

- [ ] **Step 3: 写最小实现**

`MarkdownRenderer.vue` 的 `<script setup>` 改为(替换原第 1-54 行,保留 template 与 style 不动):

```vue
<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
import 'highlight.js/styles/github-dark.css'
import { message } from '@/utils/feedback'
import { buildOutline, slugify, type OutlineItem } from '@/utils/outline'

const props = defineProps<{ content: string; anchorHeadings?: boolean }>()

function createMd(): MarkdownIt {
  return new MarkdownIt({
    html: false, // 原始 HTML 一律转义(spec 二:Markdown 消毒第一道)
    linkify: true,
    highlight(code: string, lang: string): string {
      if (lang && hljs.getLanguage(lang)) {
        try {
          return `<pre class="hljs"><code>${hljs.highlight(code, { language: lang }).value}</code></pre>`
        } catch {
          /* 高亮失败走默认转义 */
        }
      }
      return `<pre class="hljs"><code>${md.utils.escapeHtml(code)}</code></pre>`
    },
  })
}

const md = createMd()
const mdAnchor = createMd()
let headingIndex = -1
let anchorItems: OutlineItem[] = []
// 锚点模式:渲染时按顺序取 anchorItems 的 id(与 buildOutline 共用 slugify 算法,id 必然一致)
mdAnchor.renderer.rules.heading_open = (tokens, idx) => {
  const item = anchorItems[++headingIndex]
  return `<${tokens[idx].tag}${item ? ` id="${item.id}"` : ''}>`
}

const container = ref<HTMLElement>()

function renderHtml(): string {
  headingIndex = -1
  if (props.anchorHeadings) {
    anchorItems = buildOutline(props.content) // 每次渲染只解析一次
    // 第二道消毒:渲染产物再过 DOMPurify(spec 二;id 属性默认保留)
    return DOMPurify.sanitize(mdAnchor.render(props.content), { USE_PROFILES: { html: true } })
  }
  return DOMPurify.sanitize(md.render(props.content), { USE_PROFILES: { html: true } })
}

function injectCopyButtons() {
  container.value?.querySelectorAll('pre').forEach((pre) => {
    if (pre.querySelector('.code-copy-btn')) return
    pre.classList.add('code-block')
    const btn = document.createElement('button')
    btn.className = 'code-copy-btn'
    btn.type = 'button'
    btn.textContent = '复制'
    pre.appendChild(btn)
  })
}

function onClick(e: MouseEvent) {
  const btn = (e.target as HTMLElement).closest('.code-copy-btn')
  if (!btn) return
  const code = btn.closest('.code-block')?.querySelector('code')?.textContent ?? ''
  void navigator.clipboard?.writeText(code)
  message.success('已复制')
}

onMounted(injectCopyButtons)
watch(() => props.content, () => void nextTick(injectCopyButtons))
</script>
```

- [ ] **Step 4: 运行确认通过**

Run: `cd frontend && npx vitest run src/components/__tests__/MarkdownRenderer.test.ts`
Expected: 3 个测试全 PASS(原 2 个不回归)

- [ ] **Step 5: 提交**

```bash
git add frontend/src/components/MarkdownRenderer.vue frontend/src/components/__tests__/MarkdownRenderer.test.ts
git commit -m "feat: optional anchor-headings mode in MarkdownRenderer"
```

---

### Task 7: api/types 扩展 + 阅读器路由 + 视图外壳(树/选中/头部/空态)

**Files:**
- Modify: `frontend/src/types/index.ts`(`DocumentItem` 加 `sourceUrl`;新增 `DocumentContent`)
- Modify: `frontend/src/api/document.ts`(加 `getDocumentContent`、`documentFileUrl`)
- Modify: `frontend/src/router/index.ts`(加路由)
- Create: `frontend/src/views/DocumentReaderView.vue`(外壳版)
- Test: `frontend/src/views/__tests__/DocumentReaderView.test.ts`(新建)

**Interfaces:**
- Consumes: `useDocumentStore`(kbs/documents/selectKb)、`getDocumentContent`(Task 3 后端)
- Produces: 路由 `kb-reader`(Task 10 内容渲染、Task 11 入口依赖);`DocumentContent { id: number; title: string; fileType: string; content: string }`;`documentFileUrl(id: number): string`

- [ ] **Step 1: 写失败测试**

新建 `frontend/src/views/__tests__/DocumentReaderView.test.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DocumentReaderView from '@/views/DocumentReaderView.vue'

const mockReplace = vi.fn()
const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { kbId: '1', docId: '11' } }),
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
  RouterLink: { template: '<a><slot /></a>' },
}))

vi.mock('@/api/kb', () => ({ listKbs: vi.fn(), createKb: vi.fn(), deleteKb: vi.fn() }))
vi.mock('@/api/document', () => ({
  listDocuments: vi.fn(),
  uploadDocument: vi.fn(),
  searchDocuments: vi.fn(),
  deleteDocument: vi.fn(),
  getDocumentContent: vi.fn(),
  documentFileUrl: (id: number) => `/api/document/${id}/file`,
}))

import { listKbs } from '@/api/kb'
import { listDocuments, getDocumentContent } from '@/api/document'

const kb = { id: 1, name: '我的知识库', createdAt: '' }
const readyDoc = { id: 11, kbId: 1, title: 'a.md', fileType: 'md', status: 'READY' as const, createdAt: '' }
const pendingDoc = { id: 12, kbId: 1, title: 'b.md', fileType: 'md', status: 'PENDING' as const, createdAt: '' }

function mountView() {
  return mount(DocumentReaderView, {
    global: {
      stubs: { 'n-select': true, 'n-tag': { template: '<span><slot /></span>' }, 'n-drawer': true, 'n-drawer-content': { template: '<div><slot /></div>' }, 'n-button': true },
    },
  })
}

describe('DocumentReaderView 外壳', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(listKbs).mockResolvedValue([kb])
    vi.mocked(listDocuments).mockResolvedValue([readyDoc, pendingDoc])
    vi.mocked(getDocumentContent).mockResolvedValue({
      id: 11, title: 'a.md', fileType: 'md', content: '# 甲',
    })
  })

  it('挂载后加载 KB 与当前库文档,树里非 READY 显示状态徽标', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(listKbs).toHaveBeenCalled()
    expect(listDocuments).toHaveBeenCalled()
    expect(wrapper.text()).toContain('a.md')
    expect(wrapper.text()).toContain('处理中') // PENDING 徽标
  })

  it('点击 READY 文档以 router.replace 切换 docId,非 READY 不响应', async () => {
    const wrapper = mountView()
    await flushPromises()
    const ready = wrapper.findAll('.doc-item').find((n) => n.text().includes('a.md'))!
    await ready.trigger('click')
    expect(mockReplace).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'kb-reader', params: { kbId: '1', docId: '11' } }),
    )
    const pending = wrapper.findAll('.doc-item').find((n) => n.text().includes('b.md'))!
    await pending.trigger('click')
    expect(mockReplace).toHaveBeenCalledTimes(1)
  })

  it('无文档时显示空态引导', async () => {
    vi.mocked(listDocuments).mockResolvedValue([])
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('还没有文档')
  })
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/views/__tests__/DocumentReaderView.test.ts`
Expected: FAIL — 找不到 `@/views/DocumentReaderView.vue`

- [ ] **Step 3: 写最小实现**

`types/index.ts`:在 `DocumentItem` 里 `fileType: string` 之后加一行 `sourceUrl?: string`,文件末尾加:

```ts
export interface DocumentContent {
  id: number
  title: string
  fileType: string
  content: string
}
```

`api/document.ts` 末尾加:

```ts
export const getDocumentContent = (id: number) =>
  http.get<DocumentContent>(`/document/${id}/content`)

// iframe 与下载链接用的同源相对路径(cookie 自动携带;dev 走 vite 代理)
export const documentFileUrl = (id: number) => `/api/document/${id}/file`
```

补 import:`import type { DocumentContent, DocumentItem, SearchResult } from '@/types'`

`router/index.ts` 在 `/kb` 路由后加:

```ts
    { path: '/kb/:kbId/reader/:docId?', name: 'kb-reader', component: () => import('@/views/DocumentReaderView.vue') },
```

新建 `frontend/src/views/DocumentReaderView.vue`(外壳版,内容区为 Task 10 的占位):

```vue
<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NSelect, NTag } from 'naive-ui'
import { useDocumentStore } from '@/stores/document'
import type { DocumentStatus } from '@/types'

const route = useRoute()
const router = useRouter()
const docStore = useDocumentStore()

const docId = computed(() => (route.params.docId ? Number(route.params.docId) : null))
const currentDoc = computed(() => docStore.documents.find((d) => d.id === docId.value) ?? null)
const kbOptions = computed(() => docStore.kbs.map((k) => ({ label: k.name, value: k.id })))

const statusMeta: Record<DocumentStatus, { type: 'default' | 'info' | 'success' | 'error'; label: string }> = {
  PENDING: { type: 'default', label: '排队中' },
  PROCESSING: { type: 'info', label: '处理中' },
  READY: { type: 'success', label: '就绪' },
  FAILED: { type: 'error', label: '失败' },
}

function openDoc(id: number) {
  router.replace({ name: 'kb-reader', params: { kbId: String(route.params.kbId), docId: String(id) } })
}

function switchKb(v: number) {
  docStore.selectKb(v)
  void router.replace({ name: 'kb-reader', params: { kbId: String(v) } })
}

onMounted(async () => {
  await docStore.loadKbs()
  docStore.selectKb(Number(route.params.kbId))
})

watch(
  () => route.params.kbId,
  (v) => {
    if (v && Number(v) !== docStore.currentKbId) docStore.selectKb(Number(v))
  },
)
</script>

<template>
  <div class="flex h-[calc(100vh-9.5rem)] gap-4">
    <!-- 左:两级树(知识库选择 + 文档列表) -->
    <aside class="hidden w-64 shrink-0 flex-col md:flex">
      <n-select :value="Number(route.params.kbId)" :options="kbOptions" placeholder="选择知识库" class="mb-3" @update:value="switchKb" />
      <div class="flex-1 space-y-1.5 overflow-y-auto pr-1">
        <button
          v-for="d in docStore.documents"
          :key="d.id"
          class="doc-item glass-card w-full !p-3 text-left"
          :class="[
            currentDoc?.id === d.id ? '!border-cyan-400/40' : '',
            d.status !== 'READY' ? 'cursor-not-allowed opacity-50' : 'glass-card--hover',
          ]"
          :disabled="d.status !== 'READY'"
          @click="d.status === 'READY' && openDoc(d.id)"
        >
          <div class="flex items-center justify-between gap-2">
            <span class="truncate text-sm text-gray-200">{{ d.title }}</span>
            <n-tag v-if="d.status !== 'READY'" size="small" :type="statusMeta[d.status].type" :bordered="false">
              {{ statusMeta[d.status].label }}
            </n-tag>
          </div>
        </button>
        <div v-if="!docStore.documents.length" class="py-8 text-center text-xs text-gray-600">
          这个知识库还没有文档,去「知识库」页上传
        </div>
      </div>
    </aside>

    <!-- 移动端:知识库/文档下拉(桌面端 aside 的等价) -->
    <div class="flex min-w-0 flex-1 flex-col md:hidden">
      <n-select :value="Number(route.params.kbId)" :options="kbOptions" placeholder="选择知识库" class="mb-2" @update:value="switchKb" />
      <n-select
        :value="docId ?? undefined"
        :options="docStore.documents.map((d) => ({ label: d.title, value: d.id, disabled: d.status !== 'READY' }))"
        placeholder="选择文档"
        @update:value="(v: number) => openDoc(v)"
      />
    </div>

    <!-- 右:阅读区 -->
    <main class="flex min-w-0 flex-1 flex-col gap-3">
      <header class="flex items-center gap-3 border-b border-white/5 pb-3">
        <RouterLink :to="{ name: 'kb' }" class="text-xs text-gray-500 hover:text-cyan-300">← 返回知识库</RouterLink>
        <span class="truncate text-base text-gray-100">{{ currentDoc?.title ?? '文档阅读' }}</span>
        <n-tag v-if="currentDoc" size="small" :bordered="false">
          {{ currentDoc.fileType.toUpperCase() }}<template v-if="currentDoc.chunkCount"> · {{ currentDoc.chunkCount }} 块</template>
        </n-tag>
      </header>
      <!-- 内容区:Task 10 填充 -->
      <div class="flex-1 overflow-y-auto">
        <p class="py-20 text-center text-sm text-gray-600">内容渲染将在下一步接入</p>
      </div>
    </main>
  </div>
</template>
```

- [ ] **Step 4: 运行确认通过**

Run: `cd frontend && npx vitest run src/views/__tests__/DocumentReaderView.test.ts && npx vue-tsc --noEmit`
Expected: 3 个测试全 PASS,typecheck 无输出

- [ ] **Step 5: 提交**

```bash
git add frontend/src/types/index.ts frontend/src/api/document.ts frontend/src/router/index.ts frontend/src/views/DocumentReaderView.vue frontend/src/views/__tests__/DocumentReaderView.test.ts
git commit -m "feat: document reader route and view shell (tree, header, empty states)"
```

---

### Task 8: 内容分发渲染 + 头部操作 + 错误处理

**Files:**
- Modify: `frontend/src/views/DocumentReaderView.vue`(内容区与头部操作)
- Test: `frontend/src/views/__tests__/DocumentReaderView.test.ts`(加用例)

**Interfaces:**
- Consumes: `getDocumentContent`/`documentFileUrl`(Task 7)、`MarkdownRenderer` prop `anchor-headings`(Task 6)
- Produces: 视图完整阅读功能(大纲除外)— Task 9 消费

- [ ] **Step 1: 写失败测试**

`DocumentReaderView.test.ts` 加用例(在 `describe` 内):

```ts
  it('md 内容用 MarkdownRenderer 渲染并开锚点', async () => {
    const wrapper = mountView()
    await flushPromises()
    const md = wrapper.findComponent({ name: 'MarkdownRenderer' })
    expect(md.exists()).toBe(true)
    expect(md.props('anchorHeadings')).toBe(true)
    expect(wrapper.text()).toContain('甲')
  })

  it('BAGUWEN 收藏文档按纯文本渲染,显示原文外链,无下载按钮', async () => {
    vi.mocked(getDocumentContent).mockResolvedValue({
      id: 21, title: '面试题', fileType: 'BAGUWEN', content: 'Q: 什么是JVM?\nA: 运行时。',
    })
    vi.mocked(listDocuments).mockResolvedValue([
      { id: 21, kbId: 1, title: '面试题', fileType: 'BAGUWEN', status: 'READY' as const, sourceUrl: 'https://example.com/a', createdAt: '' },
    ])
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('pre').text()).toContain('Q: 什么是JVM?')
    expect(wrapper.find('a[href="https://example.com/a"]').exists()).toBe(true)
    expect(wrapper.find('a.download-link').exists()).toBe(false)
  })

  it('pdf 内容用 iframe 指向原文件接口', async () => {
    vi.mocked(getDocumentContent).mockResolvedValue({
      id: 31, title: 'p.pdf', fileType: 'pdf', content: 'x',
    })
    vi.mocked(listDocuments).mockResolvedValue([
      { id: 31, kbId: 1, title: 'p.pdf', fileType: 'pdf', status: 'READY' as const, createdAt: '' },
    ])
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('iframe').attributes('src')).toBe('/api/document/31/file')
    expect(wrapper.find('a.download-link').exists()).toBe(true)
  })

  it('内容接口报错时提示并清空 docId', async () => {
    vi.mocked(getDocumentContent).mockRejectedValue(new Error('文档不存在: 11'))
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('文档不存在: 11')
    expect(mockReplace).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'kb-reader', params: { kbId: '1' } }),
    )
  })
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/views/__tests__/DocumentReaderView.test.ts`
Expected: FAIL — 内容区仍是占位文案

- [ ] **Step 3: 写最小实现**

`DocumentReaderView.vue` 更新 `<script setup>`(替换原 script,新增 ref/computed/函数;保留树逻辑不变):

```vue
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NSelect, NTag } from 'naive-ui'
import { useDocumentStore } from '@/stores/document'
import { getDocumentContent, documentFileUrl } from '@/api/document'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { message } from '@/utils/feedback'
import type { DocumentContent, DocumentStatus } from '@/types'

const route = useRoute()
const router = useRouter()
const docStore = useDocumentStore()

const docId = computed(() => (route.params.docId ? Number(route.params.docId) : null))
const currentDoc = computed(() => docStore.documents.find((d) => d.id === docId.value) ?? null)
const kbOptions = computed(() => docStore.kbs.map((k) => ({ label: k.name, value: k.id })))

const content = ref<DocumentContent | null>(null)
const loading = ref(false)
const loadError = ref('')

type RenderKind = 'markdown' | 'pdf' | 'text' | 'baguwen'
function renderKind(ft: string): RenderKind {
  const t = (ft ?? '').toLowerCase()
  if (t === 'md' || t === 'markdown') return 'markdown'
  if (t === 'pdf') return 'pdf'
  if (t === 'baguwen') return 'baguwen'
  return 'text'
}
const kind = computed<RenderKind | null>(() => (content.value ? renderKind(content.value.fileType) : null))
const hasOriginal = computed(() => !!currentDoc.value && currentDoc.value.fileType !== 'BAGUWEN')

const statusMeta: Record<DocumentStatus, { type: 'default' | 'info' | 'success' | 'error'; label: string }> = {
  PENDING: { type: 'default', label: '排队中' },
  PROCESSING: { type: 'info', label: '处理中' },
  READY: { type: 'success', label: '就绪' },
  FAILED: { type: 'error', label: '失败' },
}

async function loadDocument() {
  if (docId.value == null) {
    content.value = null
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    content.value = await getDocumentContent(docId.value)
  } catch (e) {
    const msg = (e as Error).message
    loadError.value = msg
    content.value = null
    message.error(msg)
    if (msg.includes('不存在')) {
      void router.replace({ name: 'kb-reader', params: { kbId: String(route.params.kbId) } })
    }
  } finally {
    loading.value = false
  }
}

function openDoc(id: number) {
  router.replace({ name: 'kb-reader', params: { kbId: String(route.params.kbId), docId: String(id) } })
}

function switchKb(v: number) {
  docStore.selectKb(v)
  void router.replace({ name: 'kb-reader', params: { kbId: String(v) } })
}

onMounted(async () => {
  await docStore.loadKbs()
  docStore.selectKb(Number(route.params.kbId))
  await loadDocument()
})

watch(docId, () => void loadDocument())
watch(
  () => route.params.kbId,
  (v) => {
    if (v && Number(v) !== docStore.currentKbId) docStore.selectKb(Number(v))
  },
)
</script>
```

template 头部改为(替换原 header 块):

```html
      <header class="flex items-center gap-3 border-b border-white/5 pb-3">
        <RouterLink :to="{ name: 'kb' }" class="shrink-0 text-xs text-gray-500 hover:text-cyan-300">← 返回知识库</RouterLink>
        <span class="truncate text-base text-gray-100">{{ content?.title ?? currentDoc?.title ?? '文档阅读' }}</span>
        <n-tag v-if="currentDoc" size="small" :bordered="false">
          {{ currentDoc.fileType.toUpperCase() }}<template v-if="currentDoc.chunkCount"> · {{ currentDoc.chunkCount }} 块</template>
        </n-tag>
        <div class="ml-auto flex shrink-0 items-center gap-3">
          <a
            v-if="docId != null && hasOriginal"
            class="download-link text-xs text-gray-500 hover:text-cyan-300"
            :href="documentFileUrl(docId)"
            :download="currentDoc?.title"
          >下载原文</a>
          <a
            v-if="kind === 'baguwen' && currentDoc?.sourceUrl"
            class="text-xs text-gray-500 hover:text-cyan-300"
            :href="currentDoc.sourceUrl"
            target="_blank"
            rel="noopener"
          >查看原文</a>
        </div>
      </header>
```

template 内容区替换占位块:

```html
      <div class="relative flex-1 overflow-y-auto">
        <div v-if="loading" class="py-20 text-center text-sm text-gray-500">加载中…</div>
        <div v-else-if="loadError" class="py-20 text-center text-sm text-gray-500">
          <p class="text-red-400">{{ loadError }}</p>
          <p v-if="loadError.includes('内容缺失')" class="mt-2 text-gray-600">请删除该文档后重新上传。</p>
          <p v-else-if="loadError.includes('尚未处理')" class="mt-2 text-gray-600">处理完成后自动可用,稍后重新选择即可。</p>
        </div>
        <div v-else-if="!content" class="py-20 text-center text-sm text-gray-600">从左侧选择一篇文档开始阅读</div>
        <template v-else>
          <MarkdownRenderer v-if="kind === 'markdown'" :content="content.content" anchor-headings />
          <iframe v-else-if="kind === 'pdf'" :src="documentFileUrl(content.id)" class="h-full min-h-[60vh] w-full rounded-xl border border-white/5" />
          <pre v-else class="whitespace-pre-wrap py-2 text-sm leading-relaxed text-gray-300">{{ content.content }}</pre>
        </template>
      </div>
```

- [ ] **Step 4: 运行确认通过**

Run: `cd frontend && npx vitest run src/views/__tests__/DocumentReaderView.test.ts && npx vue-tsc --noEmit`
Expected: 7 个测试全 PASS,typecheck 无输出

- [ ] **Step 5: 提交**

```bash
git add frontend/src/views/DocumentReaderView.vue frontend/src/views/__tests__/DocumentReaderView.test.ts
git commit -m "feat: per-format content rendering, download and original links, error handling"
```

---

### Task 9: 大纲抽屉(点击跳转 + 滚动高亮)+ 知识库页入口 + 规格微调

**Files:**
- Modify: `frontend/src/views/DocumentReaderView.vue`(目录按钮 + 抽屉 + scroll-spy)
- Modify: `frontend/src/views/KnowledgeBaseView.vue`(READY 文档点击入口)
- Modify: `docs/superpowers/specs/2026-08-13-document-reader-design.md`(5.4 节「IntersectionObserver」改「滚动监听」,与实现一致)
- Test: `frontend/src/views/__tests__/DocumentReaderView.test.ts`(加用例)

**Interfaces:**
- Consumes: `buildOutline`/`computeActiveId`(Task 5)、`MarkdownRenderer anchor-headings`(Task 6)
- Produces: 阅读器全部能力;kb-reader 路由入口(Task 8)

- [ ] **Step 1: 写失败测试**

`DocumentReaderView.test.ts` 加用例:

```ts
  it('md 文档显示目录按钮,打开抽屉列出大纲并可点击跳转', async () => {
    const scrollIntoView = vi.fn()
    Element.prototype.scrollIntoView = scrollIntoView
    const wrapper = mountView()
    await flushPromises()
    const btn = wrapper.find('button.outline-trigger')
    expect(btn.exists()).toBe(true)
    await btn.trigger('click')
    await flushPromises()
    const items = wrapper.findAll('button.outline-item')
    expect(items.map((n) => n.text())).toEqual(['甲'])
    await items[0].trigger('click')
    expect(scrollIntoView).toHaveBeenCalled()
  })

  it('非 md 文档不显示目录按钮', async () => {
    vi.mocked(getDocumentContent).mockResolvedValue({
      id: 41, title: 'n.txt', fileType: 'txt', content: '纯文本',
    })
    vi.mocked(listDocuments).mockResolvedValue([
      { id: 41, kbId: 1, title: 'n.txt', fileType: 'txt', status: 'READY' as const, createdAt: '' },
    ])
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('button.outline-trigger').exists()).toBe(false)
  })
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/views/__tests__/DocumentReaderView.test.ts`
Expected: FAIL — 找不到 `.outline-trigger`

- [ ] **Step 3: 写最小实现**

`DocumentReaderView.vue` script 新增(在 `hasOriginal` 之后):

```ts
const showOutline = ref(false)
const activeId = ref<string | null>(null)
const readerPane = ref<HTMLElement>()
const outlineItems = computed(() =>
  kind.value === 'markdown' && content.value ? buildOutline(content.value.content) : [],
)
let rafPending = false

function updateActive() {
  rafPending = false
  const pane = readerPane.value
  if (!pane) return
  const paneRect = pane.getBoundingClientRect()
  const headings = Array.from(pane.querySelectorAll<HTMLElement>('h1,h2,h3')).map((h) => ({
    id: h.id,
    top: h.getBoundingClientRect().top - paneRect.top,
  }))
  activeId.value = computeActiveId(headings, 120)
}

function onPaneScroll() {
  if (rafPending) return
  rafPending = true
  requestAnimationFrame(updateActive)
}

function jumpTo(id: string) {
  // slugify 产出的 id 仅含字母/数字/横线/CJK,getElementById 足够且兼容 happy-dom
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
  showOutline.value = false
}

watch([content, kind], updateActive, { flush: 'post' }) // DOM 更新后再读标题位置
```

补 import:`import { buildOutline, computeActiveId } from '@/utils/outline'`(`ref`/`computed`/`watch` 已在 Task 8 的 import 中)。滚动监听经 template 的 `@scroll` 挂载(见下),无需 onMounted 注册。

```ts
onMounted(async () => {
  await docStore.loadKbs()
  docStore.selectKb(Number(route.params.kbId))
  await loadDocument()
})
```

不变;滚动监听挂在 template 的 `@scroll` 上即可(无需 addEventListener)。

template 内容区替换 `<div class="relative flex-1 overflow-y-auto">` 为:

```html
      <div ref="readerPane" class="relative flex-1 overflow-y-auto" @scroll="onPaneScroll">
        <button
          v-if="kind === 'markdown'"
          class="outline-trigger absolute right-4 top-4 z-10 flex h-9 items-center gap-1.5 rounded-full border border-white/10 bg-[#15151f]/90 px-3 text-xs text-gray-300 backdrop-blur hover:text-cyan-300"
          @click="showOutline = true"
        >
          <span class="text-sm leading-none">☰</span> 目录
        </button>
        ...(原 loading/loadError/空态/分发模板保持不变)...
      </div>
```

`</main>` 之后(`</div>` 根节点内末尾)加大纲抽屉:

```html
    <!-- 目录抽屉(md 文档) -->
    <n-drawer v-model:show="showOutline" :width="280" placement="right">
      <n-drawer-content title="目录" closable>
        <div class="space-y-1">
          <button
            v-for="o in outlineItems"
            :key="o.id"
            class="outline-item block w-full rounded px-2 py-1.5 text-left text-sm transition-colors"
            :class="[
              o.level === 2 ? 'pl-4' : o.level === 3 ? 'pl-8' : '',
              o.id === activeId ? 'bg-cyan-400/10 text-cyan-300' : 'text-gray-400 hover:text-gray-200',
            ]"
            @click="jumpTo(o.id)"
          >
            {{ o.text }}
          </button>
          <div v-if="!outlineItems.length" class="py-8 text-center text-xs text-gray-600">本文档没有标题</div>
        </div>
      </n-drawer-content>
    </n-drawer>
```

注意:测试 stub `'n-drawer': true` 会吞掉 slot → 用 `{ template: '<div><slot /></div>' }`(测试文件 stub 已按此配置,见 Task 7 的 mountView stubs — 其中 `'n-drawer': true` 需改为 `'n-drawer': { template: '<div><slot /></div>' }`,`'n-drawer-content'` 已有 slot stub)。修改测试 mountView stubs:`'n-drawer': true` → `'n-drawer': { template: '<div><slot /></div>' }`。

`KnowledgeBaseView.vue` 入口:script 加:

```ts
import { useRouter } from 'vue-router'
import type { DocumentItem, DocumentStatus } from '@/types'

const router = useRouter()

function openReader(d: DocumentItem) {
  if (d.status !== 'READY' || docStore.currentKbId == null) return
  void router.push({ name: 'kb-reader', params: { kbId: String(docStore.currentKbId), docId: String(d.id) } })
}
```

(现有 `import type { DocumentStatus } from '@/types'` 合并为一行)

template 文档列表项(第 151 行 `<span class="truncate text-sm text-gray-200">`)改为:

```html
              <span
                class="truncate text-sm text-gray-200"
                :class="d.status === 'READY' && docStore.currentKbId != null ? 'cursor-pointer hover:text-cyan-300' : ''"
                @click="openReader(d)"
              >{{ d.title }}</span>
```

`docs/superpowers/specs/2026-08-13-document-reader-design.md` 5.4 节末行「**滚动高亮**:IntersectionObserver 观察渲染容器内 h1~h3,取当前可见标题(视口上部带偏移 rootMargin),更新抽屉高亮」改为:

```
- **滚动高亮**:滚动监听(rAF 节流)+ `computeActiveId` 纯函数:取 top 不超过基准线(容器顶 +120px)的最后一个标题,更新抽屉高亮
```

- [ ] **Step 4: 运行确认通过**

Run: `cd frontend && npx vitest run src/views/__tests__/DocumentReaderView.test.ts && npx vue-tsc --noEmit`
Expected: 9 个测试全 PASS,typecheck 无输出

- [ ] **Step 5: 提交**

```bash
git add frontend/src/views/DocumentReaderView.vue frontend/src/views/KnowledgeBaseView.vue frontend/src/views/__tests__/DocumentReaderView.test.ts docs/superpowers/specs/2026-08-13-document-reader-design.md
git commit -m "feat: outline drawer with scroll-spy, kb page reader entry"
```

---

### Task 10: 全量回归 + 真机冒烟

**Files:**
- 无代码改动(如回归暴露问题,按 systematic-debugging 修复并补测试)

- [ ] **Step 1: 后端全量测试**

Run: `cd backend && DB_PASSWORD=root mvn test`
Expected: 全部绿(原 74 + 新增 11 = 85;DocumentContentTest 6 + DocumentFileTest 5)

- [ ] **Step 2: 前端全量测试 + typecheck + build**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run build`
Expected: vitest 全绿(原 30 + 新增 8(outline)+ 1(MarkdownRenderer)+ 9(reader)= 48);typecheck 无输出;build 成功

- [ ] **Step 3: 真机冒烟(规格第九节)**

启动后端:`cd backend && DB_PASSWORD=root mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`
启动前端:`cd frontend && npm run dev`

1. 上传 md(含 `# 一级`/`## 二级` 标题)→ 处理完成 → 知识库页点标题进阅读器 → 渲染 + 目录按钮 → 抽屉列出两级大纲 → 点击滚动 → 滚动正文时抽屉高亮跟随
2. 上传 pdf → 阅读器 iframe 内嵌预览(原生查看器)→ 头部「下载原文」可下载
3. 上传 docx → 阅读器渲染提取文本
4. 八股文库收藏一篇文章 → 阅读器可读 Q/A 文本 + 「查看原文」外链,无下载按钮
5. 上传后回知识库页,PENDING 期间阅读器树中该文档置灰带徽标
6. Chat 页回归:消息 Markdown 渲染与复制按钮行为不变

- [ ] **Step 4: 冒烟问题修复与提交(如有)**

修复发现的问题(补测试)后:

```bash
git add -A
git commit -m "fix: smoke fixes for document reader"
```

---

## Self-Review 记录

- **规格覆盖**:规格 §4(迁移/落库/接口/测试)→ Task 1-4;§5.1-5.2(路由/API)→ Task 7;§5.3(视图)→ Task 7-8;§5.4(大纲)→ Task 5/6/9;§6(数据流)隐含于 Task 7-9;§7(错误处理)→ Task 8;§9(验收)→ Task 10
- **占位符**:无 TBD/TODO;每步含完整代码与命令
- **类型一致**:`DocumentContentView`、`DocumentContent`、`OutlineItem`、`getDocumentContent`、`documentFileUrl`、`computeActiveId`、`buildOutline`、`slugify` 全计划命名一致;`anchor-headings` prop ↔ `anchorHeadings`(Vue 模板 kebab 映射)
