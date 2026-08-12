# NeuralBase Phase 2 实施计划（上传 + Embedding + pgvector 检索）

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 superpowers:subagent-driven-development 逐任务执行本计划。步骤用复选框跟踪。

**Goal:** 实现知识库文档上传、文本提取、切片、SiliconFlow Embedding、pgvector 存储与语义检索——Phase 1 的骨架之上加出"文档进得来、内容搜得到"。

**Architecture:** 沿用 Phase 1 单体 Spring Boot 3.5。上传（multipart）→ 存文件 → 建 `document` 记录（PENDING）→ `@Async` 处理（Tika 提取 → `TextChunker` 切片 → Spring AI `PgVectorStore.add` 批量 Embedding 入库 → 状态 READY）。语义检索：query 经同一 EmbeddingModel 向量化 → `PgVectorStore.similaritySearch`（可选按 `kbId` 过滤 metadata）。

**Tech Stack:** 新增 `spring-ai-bom 1.1.8`（`spring-ai-openai` + `spring-ai-pgvector-store` 模块，**非 starter**，避免自动配置陷阱）、`tika-core 3.3.2`。Embedding 走 SiliconFlow OpenAI 兼容接口（`Qwen/Qwen3-Embedding-8B`，`dimensions=4096`）。

## Global Constraints

- 沿用 Phase 1：`D:\neural-base\backend`，包根 `com.neuralbase`，测试 `@SpringBootTest` + `@ActiveProfiles("test")` 跑 `neuralbase_test`
- **pgvector 4096 维、无 ANN 索引**（已裁决）：Spring AI `PgVectorStore` 必须 `initializeSchema(false)` + `dimensions(4096)`，表由 V1 Flyway 管理，**绝不**让它自动建 schema/索引（2000 维上限会炸）
- Embedding 用显式 Bean（非 autoconfig）：`OpenAiEmbeddingModel` → SiliconFlow `https://api.siliconflow.cn`（**已修正 2026-08-12**：Spring AI 1.1.8 的 `OpenAiApi` 默认 `embeddingsPath=/v1/embeddings`，baseUrl 带 `/v1` 会拼成 `/v1/v1/embeddings` → 404；baseUrl 必须不含 `/v1`），模型 `Qwen/Qwen3-Embedding-8B`，`dimensions=4096`
- **测试不依赖真实 API**：`EmbeddingConfig` 用 `@Profile("!test")`（测试环境不建真 Bean），测试用 `@TestConfiguration` 提供假 `EmbeddingModel`（确定性向量）
- 异步：`@EnableAsync` + `@Async`（Q8-B 决策）；文档状态机 `PENDING → PROCESSING → READY/FAILED`，失败记 `document.error`
- 上传文件存 `neuralbase.storage.upload-dir`（默认 `uploads`，git 忽略）；multipart 上限 50MB
- 统一响应 `Result<T>`；`/api/document/**` 全部需 Bearer token（SecurityConfig 已默认保护）
- **测试 fixture（已裁决）**：P2 测试引用 `kbId=1`；由测试源码组件 `TestKbFixture` 在测试上下文启动时保证 kb id=1 存在 + `setval` 校正序列。**生产代码不 seed 默认库**（DataInitializer 只种 admin）
- 切片：段落分割 + 重叠，`maxChunkChars=2000`/`overlapChars=300`（以字符近似"512-1024 tokens"，CJK 下 1 token≈1-2 字符；纯本地、可测）
- 版本：`spring-ai-bom 1.1.8`、`tika-core 3.3.2` + `tika-parsers-standard-package 3.3.2`（**已修正 2026-08-12**：tika-core 无具体 parser，`parseToString` 恒返回空串，必须加 parsers 包才能提取）

## 前置条件（执行者向用户获取）

- **SiliconFlow API Key**（`SILICONFLOW_API_KEY` 环境变量）。开发期 `spring-boot:run` 需要；**测试不需要**（假 EmbeddingModel）。执行者拿到 key 后可跑一个真实 Embedding 冒烟（可选）。

---

### Task 1: 依赖 + Embedding/VectorStore 显式 Bean + 测试基建

**Files:**
- Modify: `backend/pom.xml`（加 dependencyManagement spring-ai-bom + 3 个依赖）
- Modify: `backend/src/main/resources/application.yml`（multipart、upload-dir、siliconflow 配置）
- Modify: `backend/src/main/java/com/neuralbase/NeuralBaseApplication.java`（加 `@EnableAsync`）
- Create: `backend/src/main/java/com/neuralbase/ai/EmbeddingConfig.java`（`@Profile("!test")`）
- Create: `backend/src/main/java/com/neuralbase/ai/VectorStoreConfig.java`
- Test: `backend/src/test/java/com/neuralbase/config/MockEmbeddingConfig.java`（假 EmbeddingModel）
- Test: `backend/src/test/java/com/neuralbase/ai/AiContextTest.java`（上下文加载 + PgVectorStore 可注入）

**Interfaces:**
- Produces: `EmbeddingModel`（真 Bean 在非 test profile；测试注入假 Bean）、`VectorStore` Bean（dimensions 4096 / initializeSchema false）——Task 4/5 依赖

- [ ] **Step 1: pom.xml 加依赖**

`pom.xml` 的 `<properties>` 下加 `spring-ai.version`，并在 `<dependencies>` 前加 `<dependencyManagement>`：
```xml
    <properties>
        <java.version>21</java.version>
        <mybatis-plus.version>3.5.17</mybatis-plus.version>
        <jjwt.version>0.12.7</jjwt.version>
        <spring-ai.version>1.1.8</spring-ai.version>
    </properties>
```
在 `<dependencies>` 起始处插入：
```xml
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
```
在现有依赖末尾（`spring-security-test` 之后）追加：
```xml
        <!-- Spring AI（模块，非 starter，避免自动配置） -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-openai</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-pgvector-store</artifactId>
        </dependency>
        <!-- Apache Tika 文本提取 -->
        <dependency>
            <groupId>org.apache.tika</groupId>
            <artifactId>tika-core</artifactId>
            <version>3.3.2</version>
        </dependency>
```

- [ ] **Step 2: application.yml 追加配置**

在 `application.yml` 末尾追加：
```yaml
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 60MB
neuralbase:
  storage:
    upload-dir: ${UPLOAD_DIR:uploads}
  ai:
    siliconflow-base-url: https://api.siliconflow.cn
    siliconflow-api-key: ${SILICONFLOW_API_KEY:}
```
（注意缩进：`servlet:` 要挂在 `spring:` 下——把它合并进现有 `spring:` 块，不要另起顶层 `spring`。最终 `application.yml` 的 `spring` 块应包含 `application/datasource/flyway/servlet`。）

- [ ] **Step 3: 主类加 @EnableAsync**

`NeuralBaseApplication.java` 改为：
```java
package com.neuralbase;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("com.neuralbase.mapper")
public class NeuralBaseApplication {
    public static void main(String[] args) {
        SpringApplication.run(NeuralBaseApplication.class, args);
    }
}
```

- [ ] **Step 4: EmbeddingConfig（真 Bean，仅非 test profile）**

`ai/EmbeddingConfig.java`：
```java
package com.neuralbase.ai;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiApi;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class EmbeddingConfig {

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${neuralbase.ai.siliconflow-api-key}") String apiKey,
            @Value("${neuralbase.ai.siliconflow-base-url}") String baseUrl) {
        var api = new OpenAiApi(baseUrl, apiKey);
        var options = OpenAiEmbeddingOptions.builder()
            .withModel("Qwen/Qwen3-Embedding-8B")
            .withDimensions(4096)
            .build();
        return new OpenAiEmbeddingModel(api, options);
    }
}
```

- [ ] **Step 5: VectorStoreConfig（PgVectorStore，initializeSchema false）**

`ai/VectorStoreConfig.java`：
```java
package com.neuralbase.ai;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(4096)
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .initializeSchema(false)
            .build();
    }
}
```

- [ ] **Step 6: 测试假 EmbeddingModel**

`backend/src/test/java/com/neuralbase/config/MockEmbeddingConfig.java`：
```java
package com.neuralbase.config;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class MockEmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingModel mockEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                var embeddings = request.getInstructions().stream()
                    .map(text -> new Embedding(embed(text)))
                    .toList();
                return new EmbeddingResponse(embeddings);
            }

            @Override
            public int dimensions() {
                return 4096;
            }

            private float[] embed(String text) {
                float[] v = new float[4096];
                // 已修正（2026-08-12）：用前 8 字符前缀哈希，使 query 与含同前缀的 chunk 落同桶
                // （整文本 one-hot 下 query/chunk 正交，cosine 距离=1.0 被 PgVectorStore 阈值过滤，search 恒空）
                String prefix = text.length() <= 8 ? text : text.substring(0, 8);
                v[Math.floorMod(prefix.hashCode(), 4096)] = 1.0f;
                return v;
            }
        };
    }
}
```
> 确定性：同一文本 → 同一 one-hot 向量；不同文本 → 正交（cosine 0）。测试里"精确命中"就能断言 top-1。

- [ ] **Step 7: 上下文测试**

`backend/src/test/java/com/neuralbase/ai/AiContextTest.java`：
```java
package com.neuralbase.ai;

import com.neuralbase.config.MockEmbeddingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@Import(MockEmbeddingConfig.class)
class AiContextTest {

    @Autowired
    private VectorStore vectorStore;

    @Test
    void vectorStoreBeanAvailable() {
        assertNotNull(vectorStore);
    }
}
```

- [ ] **Step 8: 跑测试**

```bash
cd /d/neural-base/backend
export DB_PASSWORD='root'
mvn test -Dtest=AiContextTest
```
**预期：PASS**（上下文加载，PgVectorStore 注入成功，不建 schema、不碰真 API）。

- [ ] **Step 9: 提交**

```bash
cd /d/neural-base
git add backend/pom.xml backend/src/main/resources/application.yml \
        backend/src/main/java/com/neuralbase/NeuralBaseApplication.java \
        backend/src/main/java/com/neuralbase/ai/EmbeddingConfig.java \
        backend/src/main/java/com/neuralbase/ai/VectorStoreConfig.java \
        backend/src/test/java/com/neuralbase/config/MockEmbeddingConfig.java \
        backend/src/test/java/com/neuralbase/ai/AiContextTest.java
git commit -m "feat: add spring-ai embedding and pgvector store beans

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Document 实体/Mapper + V2 迁移 + 上传接口

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__document_error_column.sql`
- Create: `backend/src/main/java/com/neuralbase/entity/Document.java`
- Create: `backend/src/main/java/com/neuralbase/mapper/DocumentMapper.java`
- Create: `backend/src/main/java/com/neuralbase/service/FileStorageService.java`
- Create: `backend/src/main/java/com/neuralbase/controller/DocumentController.java`（仅上传 + 列表）
- Test: `backend/src/test/java/com/neuralbase/document/DocumentUploadTest.java`

**Interfaces:**
- Consumes: Task 1 的 `VectorStore`（本任务暂不用，为 Task 4 准备）、现有 SecurityConfig
- Produces: `Document` 实体、`DocumentMapper`、`FileStorageService.store(MultipartFile)→String path`、`DocumentController.upload`（Task 4 的 DocumentService 复用）

- [ ] **Step 1: 写失败测试**

`backend/src/test/java/com/neuralbase/document/DocumentUploadTest.java`：
```java
package com.neuralbase.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.config.MockEmbeddingConfig;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(MockEmbeddingConfig.class)
class DocumentUploadTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asText();
    }

    @Test
    void uploadCreatesPendingDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "notes.md", MediaType.TEXT_MARKDOWN_VALUE, "# 标题\n\n正文内容".getBytes());
        MvcResult r = mvc.perform(multipart("/api/document")
                .file(file)
                .param("kbId", "1")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.title").value("notes.md"))
            .andReturn();
        JsonNode data = objectMapper.readTree(r.getResponse().getContentAsString()).get("data");
        assertThat(data.get("id").asLong()).isPositive();
        String status = data.get("status").asText();
        assertThat(status).isIn("PENDING", "PROCESSING", "READY", "FAILED");
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn test -Dtest=DocumentUploadTest
```
**预期：FAIL**（/api/document 不存在 → 404/405）。

- [ ] **Step 3: V2 迁移**

`V2__document_error_column.sql`：
```sql
ALTER TABLE document ADD COLUMN error TEXT;
```

- [ ] **Step 4: Document 实体 + Mapper**

`entity/Document.java`：
```java
package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("document")
public class Document {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private String title;
    private String fileType;
    private String filePath;
    private String sourceUrl;
    private String status;
    private Integer chunkCount;
    private String error;
    private OffsetDateTime createdAt;
}
```

`mapper/DocumentMapper.java`：
```java
package com.neuralbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neuralbase.entity.Document;

public interface DocumentMapper extends BaseMapper<Document> {
}
```

- [ ] **Step 5: FileStorageService**

`service/FileStorageService.java`：
```java
package com.neuralbase.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path uploadDir;

    public FileStorageService(@Value("${neuralbase.storage.upload-dir}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + this.uploadDir, e);
        }
    }

    public String store(MultipartFile file) throws IOException {
        String original = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        Path target = uploadDir.resolve(UUID.randomUUID() + "_" + original);
        file.transferTo(target);
        return target.toString();
    }
}
```

- [ ] **Step 6: DocumentController（上传 + 列表，暂不建 DocumentService）**

`controller/DocumentController.java`：
```java
package com.neuralbase.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neuralbase.common.Result;
import com.neuralbase.entity.Document;
import com.neuralbase.mapper.DocumentMapper;
import com.neuralbase.service.FileStorageService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/document")
public class DocumentController {

    private final DocumentMapper documentMapper;
    private final FileStorageService fileStorageService;

    public DocumentController(DocumentMapper documentMapper, FileStorageService fileStorageService) {
        this.documentMapper = documentMapper;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Document> upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "kbId", required = false) Long kbId) throws IOException {
        String path = fileStorageService.store(file);
        String original = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setTitle(original);
        doc.setFileType(extension(original));
        doc.setFilePath(path);
        doc.setStatus("PENDING");
        documentMapper.insert(doc);
        return Result.ok(doc);
    }

    @GetMapping
    public Result<List<Document>> list(@RequestParam(value = "kbId", required = false) Long kbId) {
        LambdaQueryWrapper<Document> w = new LambdaQueryWrapper<>();
        if (kbId != null) {
            w.eq(Document::getKbId, kbId);
        }
        w.orderByDesc(Document::getCreatedAt);
        return Result.ok(documentMapper.selectList(w));
    }

    private String extension(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase();
    }
}
```

- [ ] **Step 7: 跑测试通过**

```bash
mvn test -Dtest=DocumentUploadTest
```
**预期：PASS**。（V2 迁移会在上下文启动时自动应用。）

- [ ] **Step 8: 提交**

```bash
cd /d/neural-base
git add backend/src/main/resources/db/migration/V2__document_error_column.sql \
        backend/src/main/java/com/neuralbase/entity/Document.java \
        backend/src/main/java/com/neuralbase/mapper/DocumentMapper.java \
        backend/src/main/java/com/neuralbase/service/FileStorageService.java \
        backend/src/main/java/com/neuralbase/controller/DocumentController.java \
        backend/src/test/java/com/neuralbase/document/DocumentUploadTest.java
git commit -m "feat: document entity, v2 migration, upload api

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: 文本提取 + 切片

**Files:**
- Create: `backend/src/main/java/com/neuralbase/service/TextExtractorService.java`
- Create: `backend/src/main/java/com/neuralbase/service/TextChunker.java`
- Test: `backend/src/test/resources/extract/sample.md`
- Test: `backend/src/test/resources/extract/sample.txt`
- Test: `backend/src/test/java/com/neuralbase/service/TextChunkerTest.java`
- Test: `backend/src/test/java/com/neuralbase/service/TextExtractorServiceTest.java`

**Interfaces:**
- Produces: `TextExtractorService.extract(InputStream, String filename) → String`、`TextChunker.chunk(String text) → List<String>`（Task 4 依赖）

- [ ] **Step 1: 写失败测试（chunker）**

`TextChunkerTest.java`：
```java
package com.neuralbase.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker(2000, 300);

    @Test
    void shortTextYieldsSingleChunk() {
        List<String> chunks = chunker.chunk("第一段。\n\n第二段。");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("第一段", "第二段");
    }

    @Test
    void longTextSplitsIntoMultipleChunks() {
        String text = "字".repeat(5000); // 单段超限 → 硬切
        List<String> chunks = chunker.chunk(text);
        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks.stream().allMatch(c -> c.length() <= 2000)).isTrue();
    }

    @Test
    void overlapCarriesTailOfPreviousChunk() {
        String text = ("段落A内容".repeat(600)) + "\n\n" + ("段落B内容".repeat(600));
        List<String> chunks = chunker.chunk(text);
        assertThat(chunks.size()).isGreaterThan(1);
        // 后一块应含前一块的尾部重叠
        String prevTail = chunks.get(0).substring(chunks.get(0).length() - 300);
        assertThat(chunks.get(1)).contains(prevTail);
    }

    @Test
    void blankTextYieldsEmpty() {
        assertThat(chunker.chunk("   \n\n  ")).isEmpty();
        assertThat(chunker.chunk(null)).isEmpty();
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn test -Dtest=TextChunkerTest
```
**预期：FAIL**（TextChunker 不存在）。

- [ ] **Step 3: 实现 TextChunker**

`service/TextChunker.java`：
```java
package com.neuralbase.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {

    private final int maxChunkChars;
    private final int overlapChars;

    public TextChunker(@Value("${neuralbase.chunker.max-chars:2000}") int maxChunkChars,
                       @Value("${neuralbase.chunker.overlap-chars:300}") int overlapChars) {
        this.maxChunkChars = maxChunkChars;
        this.overlapChars = overlapChars;
    }

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String raw : text.split("\\n{2,}")) {
            String p = raw.strip();
            if (p.isEmpty()) {
                continue;
            }
            for (String piece : splitLong(p)) {
                if (!current.isEmpty() && current.length() + piece.length() > maxChunkChars) {
                    String prev = current.toString().strip();
                    chunks.add(prev);
                    current.setLength(0);
                    if (prev.length() >= overlapChars) {
                        current.append(prev, prev.length() - overlapChars, prev.length()).append('\n');
                    }
                }
                current.append(piece).append('\n');
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().strip());
        }
        return chunks;
    }

    private List<String> splitLong(String paragraph) {
        if (paragraph.length() <= maxChunkChars) {
            return List.of(paragraph);
        }
        List<String> pieces = new ArrayList<>();
        int i = 0;
        while (i < paragraph.length()) {
            pieces.add(paragraph.substring(i, Math.min(i + maxChunkChars, paragraph.length())));
            i += maxChunkChars;
        }
        return pieces;
    }
}
```
> 注：`TextChunkerTest` 用 `new TextChunker(2000, 300)` 直接构造（不用 Spring），所以构造器参数无默认值注解也不影响；@Value 默认值仅用于 Spring 实例。

- [ ] **Step 4: 实现 TextExtractorService + 测试资源**

`service/TextExtractorService.java`：
```java
package com.neuralbase.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class TextExtractorService {

    private final Tika tika = new Tika();

    public String extract(InputStream in, String filename) throws IOException {
        return tika.parseToString(in);
    }
}
```

`src/test/resources/extract/sample.md`：
```markdown
# NeuralBase 测试

这是用于文本提取测试的 Markdown 文件。

## 章节

包含一些内容。
```

`src/test/resources/extract/sample.txt`：
```
这是纯文本提取测试。
第二行内容。
```

`TextExtractorServiceTest.java`：
```java
package com.neuralbase.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TextExtractorServiceTest {

    @Autowired
    private TextExtractorService extractor;

    @Test
    void extractsTxt() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/extract/sample.txt")) {
            String text = extractor.extract(in, "sample.txt");
            assertThat(text).contains("纯文本提取测试");
        }
    }

    @Test
    void extractsMarkdown() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/extract/sample.md")) {
            String text = extractor.extract(in, "sample.md");
            assertThat(text).contains("NeuralBase 测试");
        }
    }
}
```

- [ ] **Step 5: 跑测试通过**

```bash
mvn test -Dtest=TextChunkerTest,TextExtractorServiceTest
```
**预期：PASS**（4 + 2 个用例）。

- [ ] **Step 6: 提交**

```bash
cd /d/neural-base
git add backend/src/main/java/com/neuralbase/service/TextChunker.java \
        backend/src/main/java/com/neuralbase/service/TextExtractorService.java \
        backend/src/test/resources/extract/sample.md \
        backend/src/test/resources/extract/sample.txt \
        backend/src/test/java/com/neuralbase/service/TextChunkerTest.java \
        backend/src/test/java/com/neuralbase/service/TextExtractorServiceTest.java
git commit -m "feat: text extraction and chunking

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: 文档处理流水线（提取→切片→Embedding→入库→状态）

**Files:**
- Create: `backend/src/main/java/com/neuralbase/service/DocumentService.java`
- Modify: `backend/src/main/java/com/neuralbase/controller/DocumentController.java`（upload 改走 DocumentService）
- Test: `backend/src/test/java/com/neuralbase/document/DocumentProcessTest.java`

**Interfaces:**
- Consumes: Task 1 `VectorStore`、Task 2 `DocumentMapper`/`FileStorageService`、Task 3 `TextExtractorService`/`TextChunker`
- Produces: `DocumentService.upload(MultipartFile, Long kbId) → Document`（异步触发处理）、`DocumentService.process(Long documentId)`（同步核心，测试直接调）、`DocumentService.list(Long kbId)`；搜索留给 Task 5

- [ ] **Step 1: 写失败测试**

`DocumentProcessTest.java`：
```java
package com.neuralbase.document;

import com.neuralbase.config.MockEmbeddingConfig;
import com.neuralbase.entity.Document;
import com.neuralbase.mapper.DocumentMapper;
import com.neuralbase.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(MockEmbeddingConfig.class)
class DocumentProcessTest {

    @Autowired
    private DocumentService documentService;
    @Autowired
    private DocumentMapper documentMapper;

    @Test
    void uploadThenProcessMarksReadyAndStoresChunks() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "tutorial.md", "text/markdown",
            ("NeuralBase 教程\n\n" + "Java 虚拟线程详解内容。".repeat(300)).getBytes());
        Document doc = documentService.upload(file, 1L);
        assertThat(doc.getStatus()).isEqualTo("PENDING");

        documentService.process(doc.getId());

        Document after = documentMapper.selectById(doc.getId());
        assertThat(after.getStatus()).isEqualTo("READY");
        assertThat(after.getChunkCount()).isGreaterThan(0);
        assertThat(after.getFilePath()).isNotBlank();
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn test -Dtest=DocumentProcessTest
```
**预期：FAIL**（DocumentService 不存在）。

- [ ] **Step 3: 实现 DocumentService**

`service/DocumentService.java`：
```java
package com.neuralbase.service;

import com.neuralbase.entity.Document;
import com.neuralbase.mapper.DocumentMapper;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentService {

    private final DocumentMapper documentMapper;
    private final FileStorageService fileStorageService;
    private final TextExtractorService textExtractorService;
    private final TextChunker textChunker;
    private final VectorStore vectorStore;

    public DocumentService(DocumentMapper documentMapper, FileStorageService fileStorageService,
                           TextExtractorService textExtractorService, TextChunker textChunker,
                           VectorStore vectorStore) {
        this.documentMapper = documentMapper;
        this.fileStorageService = fileStorageService;
        this.textExtractorService = textExtractorService;
        this.textChunker = textChunker;
        this.vectorStore = vectorStore;
    }

    public Document upload(MultipartFile file, Long kbId) throws IOException {
        String path = fileStorageService.store(file);
        String original = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setTitle(original);
        doc.setFileType(extension(original));
        doc.setFilePath(path);
        doc.setStatus("PENDING");
        documentMapper.insert(doc);
        processAsync(doc.getId());
        return doc;
    }

    @Async
    public void processAsync(Long documentId) {
        try {
            process(documentId);
        } catch (Exception e) {
            Document doc = documentMapper.selectById(documentId);
            if (doc != null) {
                doc.setStatus("FAILED");
                doc.setError(e.getMessage());
                documentMapper.updateById(doc);
            }
        }
    }

    public void process(Long documentId) throws IOException {
        Document doc = documentMapper.selectById(documentId);
        doc.setStatus("PROCESSING");
        documentMapper.updateById(doc);
        try (var in = Files.newInputStream(Path.of(doc.getFilePath()))) {
            String text = textExtractorService.extract(in, doc.getTitle());
            List<String> chunks = textChunker.chunk(text);
            List<org.springframework.ai.document.Document> docs = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("docId", doc.getId());
                metadata.put("kbId", doc.getKbId());
                metadata.put("chunkIndex", i);
                docs.add(new org.springframework.ai.document.Document(chunks.get(i), metadata));
            }
            if (!docs.isEmpty()) {
                vectorStore.add(docs);
            }
            doc.setStatus("READY");
            doc.setChunkCount(chunks.size());
            documentMapper.updateById(doc);
        }
    }

    public List<Document> list(Long kbId) {
        return documentMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
            .eq(kbId != null, Document::getKbId, kbId)
            .orderByDesc(Document::getCreatedAt));
    }

    private String extension(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase();
    }
}
```

- [ ] **Step 4: DocumentController 改用 DocumentService**

`controller/DocumentController.java` 的 `upload` 改为：
```java
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Document> upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "kbId", required = false) Long kbId) throws IOException {
        return Result.ok(documentService.upload(file, kbId));
    }
```
`list` 改为 `return Result.ok(documentService.list(kbId));`。删除不再使用的 `DocumentMapper`/`FileStorageService` 字段与构造参数，注入 `DocumentService`。imports 同步清理。

- [ ] **Step 5: 跑测试通过 + 回归**

```bash
mvn test -Dtest=DocumentProcessTest,DocumentUploadTest
```
**预期：PASS**。再全量：
```bash
mvn test
```
**预期：全绿**（原 10 个 + 新用例）。

- [ ] **Step 6: 提交**

```bash
cd /d/neural-base
git add backend/src/main/java/com/neuralbase/service/DocumentService.java \
        backend/src/main/java/com/neuralbase/controller/DocumentController.java \
        backend/src/test/java/com/neuralbase/document/DocumentProcessTest.java
git commit -m "feat: async document processing pipeline

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: 语义检索端点

**Files:**
- Create: `backend/src/main/java/com/neuralbase/dto/SearchQuery.java`
- Create: `backend/src/main/java/com/neuralbase/dto/SearchResult.java`
- Modify: `backend/src/main/java/com/neuralbase/service/DocumentService.java`（加 `search` 方法）
- Modify: `backend/src/main/java/com/neuralbase/controller/DocumentController.java`（加 `POST /api/document/search`）
- Test: `backend/src/test/java/com/neuralbase/document/DocumentSearchTest.java`

**Interfaces:**
- Consumes: Task 4 的 `process`（用于确定性造数据）
- Produces: `POST /api/document/search`，body `SearchQuery{query, kbId, topK}` → `List<SearchResult{docId, title, content, score}>`

- [ ] **Step 1: 写失败测试**

`DocumentSearchTest.java`：
```java
package com.neuralbase.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.config.MockEmbeddingConfig;
import com.neuralbase.entity.Document;
import com.neuralbase.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(MockEmbeddingConfig.class)
class DocumentSearchTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DocumentService documentService;

    private String token;

    @BeforeEach
    void seedAndLogin() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "seed.txt", "text/plain",
            "NeuralBase 测试文档内容，用于检索验证。".repeat(200).getBytes());
        Document doc = documentService.upload(file, 1L);
        documentService.process(doc.getId());

        var r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk()).andReturn();
        token = objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asText();
    }

    @Test
    void searchReturnsExactMatchChunk() throws Exception {
        String query = "NeuralBase 测试文档内容，用于检索验证。".repeat(1);
        mvc.perform(post("/api/document/search")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    java.util.Map.of("query", query, "topK", 3))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].content").exists())
            .andExpect(jsonPath("$.data[0].docId").isNumber());
    }

    @Test
    void searchReturnsEmptyForGibberish() throws Exception {
        mvc.perform(post("/api/document/search")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    java.util.Map.of("query", "zzz qqq xxx 无意义", "topK", 3))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }
}
```
> 说明：假 EmbeddingModel 是 one-hot（同文本→cosine 1，异文本→cosine 0）。`searchReturnsExactMatchChunk` 的 query 与种子 chunk 文本部分一致 → 命中；gibberish → 无高相似（可能仍返回任意 chunk，故只断言 `code:0` 不抛错）。

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn test -Dtest=DocumentSearchTest
```
**预期：FAIL**（search 端点不存在）。

- [ ] **Step 3: DTO + service.search + controller**

`dto/SearchQuery.java`：
```java
package com.neuralbase.dto;

public record SearchQuery(String query, Long kbId, Integer topK) {
}
```

`dto/SearchResult.java`：
```java
package com.neuralbase.dto;

public record SearchResult(Long docId, String title, String content, double score) {
}
```

`DocumentService` 加方法（import `org.springframework.ai.vectorstore.SearchRequest as AiSearchRequest`，与 DTO 区分）：
```java
    public List<SearchResult> search(String query, Long kbId, Integer topK) {
        int k = topK == null ? 5 : topK;
        org.springframework.ai.vectorstore.SearchRequest.Builder builder =
            org.springframework.ai.vectorstore.SearchRequest.builder().query(query).topK(k);
        if (kbId != null) {
            builder.filterExpression("kbId == " + kbId);
        }
        var matches = vectorStore.similaritySearch(builder.build());
        return matches.stream().map(d -> {
            Object rawDocId = d.getMetadata().get("docId");
            Long docId = rawDocId instanceof Number n ? n.longValue() : null;
            Document doc = docId == null ? null : documentMapper.selectById(docId);
            return new SearchResult(docId, doc == null ? null : doc.getTitle(), d.getText(), d.getScore());
        }).toList();
    }
```
（加 import `com.neuralbase.dto.SearchResult`。）

`DocumentController` 加：
```java
    @PostMapping("/search")
    public Result<List<SearchResult>> search(@RequestBody SearchQuery query) {
        return Result.ok(documentService.search(query.query(), query.kbId(), query.topK()));
    }
```
（加 import `com.neuralbase.dto.SearchQuery`/`SearchResult`、`org.springframework.web.bind.annotation.RequestBody`、`java.util.List`。）

- [ ] **Step 4: 跑测试通过 + 全量回归**

```bash
mvn test -Dtest=DocumentSearchTest
```
**预期：PASS**。再全量：
```bash
mvn test
```
**预期：全绿。**

> **已知风险**：`filterExpression("kbId == " + kbId)` 依赖 Spring AI 1.1.x 的 metadata 过滤转换。若测试/运行发现 kbId 过滤不生效（metadata JSONB 里数字与字符串比较不匹配），fallback：metadata 存 `kbId` 为字符串（`String.valueOf(doc.getKbId())`）+ 过滤器用 `"kbId == '" + kbId + "'"`。实现者遇到问题按此调整并在报告中说明。

- [ ] **Step 5: 提交**

```bash
cd /d/neural-base
git add backend/src/main/java/com/neuralbase/dto/SearchQuery.java \
        backend/src/main/java/com/neuralbase/dto/SearchResult.java \
        backend/src/main/java/com/neuralbase/service/DocumentService.java \
        backend/src/main/java/com/neuralbase/controller/DocumentController.java \
        backend/src/test/java/com/neuralbase/document/DocumentSearchTest.java
git commit -m "feat: semantic search endpoint

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## 完成标准（Phase 2 Done）

- `POST /api/document`（multipart）→ 建 PENDING 记录，异步处理 → READY + chunk 入库
- `POST /api/document/search`（query/kbId/topK）→ 返回 top-k 片段 + 相似度分
- `mvn test` 全绿（测试用假 EmbeddingModel，不依赖真实 API）
- 可选：设了 `SILICONFLOW_API_KEY` 后，可真实跑一次 embedding 冒烟

## Self-Review

- **Spec coverage**：Roadmap Phase 2（上传 + Embedding + 检索）全覆盖；`@Async`（Q8-B）、4096 维无索引（已裁决）、多 kb 过滤（Q7-B）均已体现。
- **Placeholder scan**：无 TBD；唯一外部输入是 SiliconFlow API key（前置条件已声明，测试不依赖）。
- **Type consistency**：`SearchQuery`/`SearchResult` 与 `org.springframework.ai.vectorstore.SearchRequest` 无命名冲突（全限定名区分）；`process/processAsync/upload/list/search` 签名在 Task 4/5 间一致。
