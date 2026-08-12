# NeuralBase Phase 3 实施计划（AI 对话 + RAG：SSE 流式 + 会话持久化）

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 superpowers:subagent-driven-development 逐任务执行本计划。步骤用复选框跟踪。

**Goal:** 在 Phase 1-2 之上实现 DeepSeek 聊天：`POST /api/chat/stream` SSE 流式输出、知识库 RAG 模式（复用 Phase 2 语义检索）、会话/消息持久化（列表/删除、服务端加载多轮历史）。

**Architecture:** DeepSeek 走 spring-ai-openai 兼容接口（显式 Bean，非 starter，同 Phase 2 风格）→ `ChatModel.stream(Prompt)` 返回 `Flux<ChatResponse>`，Controller 直接返回 `Flux<ServerSentEvent<String>>`（Spring MVC 响应式适配 + reactor-core，已在 classpath）。RAG 模式复用 `DocumentService.search(query, kbId, topK)`，检索片段注入 system prompt，并存 `message.sources`（JSONB）随 SSE `sources` 事件发给前端。多轮上下文由服务端按 `conversationId` 加载最近 10 条消息（不靠客户端传历史）。

**Tech Stack:** 复用已有 `spring-ai-openai 1.1.8` + `reactor-core`（均已在 classpath，**不新增编译依赖**）；新增 `V3__chat_tables.sql` Flyway 迁移；测试用 `@Configuration` Mock ChatModel（组件扫描自动注册，同现有 `MockEmbeddingConfig` 模式），不依赖真实 API。

## Global Constraints

- 沿用 Phase 1/2：`D:\neural-base\backend`，包根 `com.neuralbase`，测试 `@SpringBootTest` + `@ActiveProfiles("test")` 跑 `neuralbase_test`。当前基线 **26/26 绿**（2026-08-12 已复跑确认）。
- **DeepSeek 接入（已裁决）**：spring-ai-openai 兼容，显式 Bean 非 starter。`OpenAiApi.builder().baseUrl(...).apiKey(...)`；`OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build()`。模型 `deepseek-v4-flash`（`neuralbase.ai.deepseek-model` 可配），`temperature 0.7`。
- **base-url 不带 `/v1` + 显式 `completionsPath("/chat/completions")`**：DeepSeek 官方 chat 路径是 `https://api.deepseek.com/chat/completions`（**不带 /v1**，2026-08-12 官方文档确认）；而 Spring AI 1.1.8 `OpenAiApi` 默认 `completionsPath=/v1/chat/completions`。所以 `OpenAiApi.builder().baseUrl("https://api.deepseek.com").completionsPath("/chat/completions")` 必须显式设路径，否则拼出官方未文档化的 `/v1/chat/completions`。
- **接入方式（已裁决）**：用 `spring-ai-openai` 兼容（无新增依赖、与 P2 一致）。Spring AI 1.1.x 虽有官方 `spring-ai-deepseek` 模块（`org.springframework.ai:spring-ai-deepseek`），但不采用。
- `ChatConfig` 用 `@Profile("!test")`；测试 Mock ChatModel 是 `@Configuration` + `@Primary`，放 `com.neuralbase.config`——**组件扫描自动注册**（与 `MockEmbeddingConfig` 一致，勿用 `@TestConfiguration`，否则必须逐测试 `@Import`）。**测试不碰真实 DeepSeek API。**
- SSE：`POST /api/chat/stream` → `text/event-stream`，事件顺序 `meta` → `sources`（仅 RAG 且有命中）→ `delta`* → `done`；异常时发 `error` 事件后仍发 `done`。**前端用 fetch + ReadableStream 带 Bearer header（已裁决），勿用原生 EventSource。**
- 会话持久化（V3）：`conversation` + `message` 表；`message.sources` JSONB 存 RAG 引用 `[{"docId":..,"title":..,"content":..,"score":..}]`。
- 会话接口（已裁决，最小）：`POST /api/conversation`（新建）、`GET /api/conversation`（列表含 lastMessageContent）、`GET /api/conversation/{id}/messages`、`DELETE /api/conversation/{id}`（级联删消息）。**无**重命名/收藏、**无**消息反馈（Phase 6 再做）。
- RAG 复用 `DocumentService.search(query, kbId, topK)`，topK=5；kbId 为空 → 普通模式（纯 DeepSeek）；无命中 → system prompt 写「未检索到相关内容」，由模型明说。
- 多轮历史：服务端 `selectRecentAsc(conversationId, 10)` 最近 10 条（含当前问题），`HISTORY_WINDOW=10`。
- 实体命名：本模块实体 `Message`（`com.neuralbase.entity.Message`）与 Spring AI 消息类**无命名冲突**——代码只 import `SystemMessage`/`UserMessage`/`AssistantMessage` 具体类，从不 import `org.springframework.ai.chat.messages.Message` 基类。
- 全接口 `/api/**` 需 Bearer token（SecurityConfig 已默认保护，无需改）。
- 真实冒烟：用户已确认**稍后有 `DEEPSEEK_API_KEY`**，开发期 `spring-boot:run` 需要；测试不需要。冒烟用 `--server.port=8081`（8080 被 `JobAgentApplication` 占用）。

## 前置条件（执行者向用户获取）

- **DeepSeek API Key**（`DEEPSEEK_API_KEY` 环境变量）。用户已确认稍后提供。冒烟为可选步骤；不提供也能全量绿。

---

### Task 1: DeepSeek ChatModel Bean + Mock ChatModel + 上下文测试

**Files:**
- Modify: `backend/src/main/resources/application.yml`（`neuralbase.ai` 下加 3 个 deepseek 配置项）
- Create: `backend/src/main/java/com/neuralbase/ai/ChatConfig.java`（`@Profile("!test")`）
- Test: `backend/src/test/java/com/neuralbase/config/MockChatConfig.java`（`@Configuration` + `@Primary`，自动注册）
- Test: `backend/src/test/java/com/neuralbase/ai/ChatContextTest.java`

**Interfaces:**
- Produces: `ChatModel` Bean（真 Bean 仅非 test profile；测试自动注入 mock）——Task 3 的 `ChatService` 依赖。

- [ ] **Step 1: 写上下文测试**

`backend/src/test/java/com/neuralbase/ai/ChatContextTest.java`：
```java
package com.neuralbase.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class ChatContextTest {

    @Autowired
    private ChatModel chatModel;

    @Test
    void chatModelBeanAvailable() {
        assertNotNull(chatModel);
    }
}
```
> `MockChatConfig` 是 `@Configuration` 在 `com.neuralbase.config` 包，被组件扫描，无需 `@Import`。

- [ ] **Step 2: 跑测试确认失败**

```bash
cd /d/neural-base/backend
export DB_PASSWORD='root'
mvn test -Dtest=ChatContextTest
```
**预期：FAIL/ERROR**（无 `ChatModel` Bean，上下文加载失败）。

- [ ] **Step 3: application.yml 追加 deepseek 配置**

在现有 `neuralbase.ai:` 块内追加 3 项（最终块含 `siliconflow-*` + `deepseek-*`）：
```yaml
neuralbase:
  ai:
    siliconflow-base-url: https://api.siliconflow.cn
    siliconflow-api-key: ${SILICONFLOW_API_KEY:}
    deepseek-base-url: https://api.deepseek.com
    deepseek-api-key: ${DEEPSEEK_API_KEY:}
    deepseek-model: ${DEEPSEEK_MODEL:deepseek-v4-flash}
```

- [ ] **Step 4: ChatConfig（真 Bean，仅非 test profile）**

`backend/src/main/java/com/neuralbase/ai/ChatConfig.java`：
```java
package com.neuralbase.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class ChatConfig {

    @Bean
    public ChatModel chatModel(
            @Value("${neuralbase.ai.deepseek-api-key}") String apiKey,
            @Value("${neuralbase.ai.deepseek-base-url}") String baseUrl,
            @Value("${neuralbase.ai.deepseek-model}") String model) {
        var api = OpenAiApi.builder()
            .baseUrl(baseUrl)
            .completionsPath("/chat/completions")   // DeepSeek 官方路径不带 /v1，显式覆盖 Spring AI 默认 /v1/chat/completions
            .apiKey(apiKey)
            .build();
        var options = OpenAiChatOptions.builder()
            .model(model)
            .temperature(0.7)
            .build();
        return OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(options)
            .build();
    }
}
```
> Spring AI 1.1.8 `OpenAiChatModel` **没有**两参构造器，必须用 `OpenAiChatModel.builder()`（`openAiApi` + `defaultOptions`，其余组件 Builder 提供默认）。真实冒烟若报 404，先核对 `completionsPath` 拼出的 URL 是否为 `https://api.deepseek.com/chat/completions`。

- [ ] **Step 5: Mock ChatModel（@Configuration 自动注册）**

`backend/src/test/java/com/neuralbase/config/MockChatConfig.java`：
```java
package com.neuralbase.config;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Configuration
public class MockChatConfig {

    /** 每次 prompt 调用都会记录（含 SystemMessage 的 RAG 上下文与多轮历史），测试断言用。测试前请 clear。 */
    public static final List<Prompt> PROMPTS = new CopyOnWriteArrayList<>();

    @Bean
    @Primary
    public ChatModel mockChatModel() {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                PROMPTS.add(prompt);
                return new ChatResponse(List.of(new Generation(new AssistantMessage("mock 回复"))));
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                PROMPTS.add(prompt);
                return Flux.just("mock 回答", "片段二")
                    .map(text -> new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
            }
        };
    }
}
```
> `Generation` 构造器接收 `AssistantMessage`（不是 String）；`ChatResponse(List<Generation>)`。流式返回两段 → 拼接为 `"mock 回答片段二"`，测试据此断言落库内容。

- [ ] **Step 6: 跑测试通过**

```bash
mvn test -Dtest=ChatContextTest
```
**预期：PASS**（上下文加载，mock ChatModel 注入成功）。

- [ ] **Step 7: 提交**

```bash
cd /d/neural-base
git add backend/src/main/resources/application.yml \
        backend/src/main/java/com/neuralbase/ai/ChatConfig.java \
        backend/src/test/java/com/neuralbase/config/MockChatConfig.java \
        backend/src/test/java/com/neuralbase/ai/ChatContextTest.java
git commit -m "feat: add deepseek chat model bean and mock

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: V3 迁移 + 会话/消息实体 + ConversationService + 会话 CRUD 接口

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__chat_tables.sql`
- Create: `backend/src/main/java/com/neuralbase/entity/Conversation.java`
- Create: `backend/src/main/java/com/neuralbase/entity/Message.java`
- Create: `backend/src/main/java/com/neuralbase/mapper/ConversationMapper.java`
- Create: `backend/src/main/java/com/neuralbase/mapper/MessageMapper.java`
- Create: `backend/src/main/java/com/neuralbase/dto/RagSource.java`
- Create: `backend/src/main/java/com/neuralbase/dto/ConversationView.java`
- Create: `backend/src/main/java/com/neuralbase/dto/MessageView.java`
- Create: `backend/src/main/java/com/neuralbase/service/ConversationService.java`
- Create: `backend/src/main/java/com/neuralbase/controller/ConversationController.java`
- Test: `backend/src/test/java/com/neuralbase/chat/ConversationCrudTest.java`

**Interfaces:**
- Consumes: 现有 `SecurityConfig`、`TestKbFixture`（kb id=1 fixture，已自动注册）
- Produces: `ConversationService.create(title,kbId)→Conversation`、`.get(id)`、`.touch(id)`、`.list()→List<ConversationView>`、`.messages(id)→List<MessageView>`、`.delete(id)`、`.parseSources(String)→List<RagSource>`；`MessageMapper.selectRecentAsc(convId,limit)`——Task 3 的 `ChatService` 全部复用。

- [ ] **Step 1: 写失败测试**

`backend/src/test/java/com/neuralbase/chat/ConversationCrudTest.java`：
```java
package com.neuralbase.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ConversationCrudTest {

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
    void createListMessagesDelete() throws Exception {
        String tk = token();

        MvcResult created = mvc.perform(post("/api/conversation")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"第一个会话\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.title").value("第一个会话"))
            .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("data").get("id").asLong();

        MvcResult list = mvc.perform(get("/api/conversation").header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        JsonNode arr = objectMapper.readTree(list.getResponse().getContentAsString()).get("data");
        assertThat(arr).anySatisfy(n -> assertThat(n.get("id").asLong()).isEqualTo(id));

        mvc.perform(get("/api/conversation/" + id + "/messages").header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());

        mvc.perform(delete("/api/conversation/" + id).header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        // 删除后再查消息 → 会话不存在 → 400（GlobalExceptionHandler 处理 IllegalArgumentException）
        mvc.perform(get("/api/conversation/" + id + "/messages").header("Authorization", "Bearer " + tk))
            .andExpect(status().isBadRequest());
    }

    @Test
    void conversationEndpointsRequireAuth() throws Exception {
        mvc.perform(get("/api/conversation")).andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd /d/neural-base/backend
export DB_PASSWORD='root'
mvn test -Dtest=ConversationCrudTest
```
**预期：FAIL**（`/api/conversation` 不存在 → 404/405）。

- [ ] **Step 3: V3 迁移**

`backend/src/main/resources/db/migration/V3__chat_tables.sql`：
```sql
CREATE TABLE conversation (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(255) NOT NULL,
    kb_id      BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE message (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    role            VARCHAR(16) NOT NULL,
    content         TEXT NOT NULL,
    sources         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_message_conversation_id ON message(conversation_id, id);
```

- [ ] **Step 4: 实体 + Mapper**

`entity/Conversation.java`：
```java
package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("conversation")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private Long kbId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
```

`entity/Message.java`：
```java
package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("message")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private String sources;   // JSONB：RagSource 数组的 JSON 文本；可为 null
    private OffsetDateTime createdAt;
}
```

`mapper/ConversationMapper.java`：
```java
package com.neuralbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neuralbase.entity.Conversation;

public interface ConversationMapper extends BaseMapper<Conversation> {
}
```

`mapper/MessageMapper.java`：
```java
package com.neuralbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neuralbase.entity.Message;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface MessageMapper extends BaseMapper<Message> {

    /** 取某会话最近 limit 条消息，升序返回（子查询倒排截断再正排）。 */
    @Select("""
        SELECT * FROM (
            SELECT * FROM message
            WHERE conversation_id = #{conversationId}
            ORDER BY id DESC
            LIMIT #{limit}
        ) recent ORDER BY id ASC
        """)
    List<Message> selectRecentAsc(@Param("conversationId") Long conversationId, @Param("limit") int limit);

    /** 每个会话的最后一条消息内容（DISTINCT ON + 倒序）。map-underscore-to-camel-case 自动映射 conversation_id→conversationId。 */
    @Select("""
        SELECT DISTINCT ON (conversation_id) conversation_id, content
        FROM message
        ORDER BY conversation_id, id DESC
        """)
    List<LastMessageRow> selectLastMessages();

    class LastMessageRow {
        private Long conversationId;
        private String content;

        public Long getConversationId() { return conversationId; }
        public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
```

- [ ] **Step 5: DTO**

`dto/RagSource.java`：
```java
package com.neuralbase.dto;

public record RagSource(Long docId, String title, String content, double score) {
}
```

`dto/ConversationView.java`：
```java
package com.neuralbase.dto;

import java.time.OffsetDateTime;

public record ConversationView(Long id, String title, Long kbId, OffsetDateTime createdAt,
                               OffsetDateTime updatedAt, String lastMessageContent) {
}
```

`dto/MessageView.java`：
```java
package com.neuralbase.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record MessageView(Long id, String role, String content, List<RagSource> sources, OffsetDateTime createdAt) {
}
```

- [ ] **Step 6: ConversationService**

`service/ConversationService.java`：
```java
package com.neuralbase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.dto.ConversationView;
import com.neuralbase.dto.MessageView;
import com.neuralbase.dto.RagSource;
import com.neuralbase.entity.Conversation;
import com.neuralbase.entity.Message;
import com.neuralbase.mapper.ConversationMapper;
import com.neuralbase.mapper.MessageMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public ConversationService(ConversationMapper conversationMapper, MessageMapper messageMapper,
                               ObjectMapper objectMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    public Conversation create(String title, Long kbId) {
        Conversation c = new Conversation();
        c.setTitle(title == null || title.isBlank() ? "新对话" : title);
        c.setKbId(kbId);
        c.setCreatedAt(OffsetDateTime.now());
        c.setUpdatedAt(c.getCreatedAt());
        conversationMapper.insert(c);
        return c;
    }

    public Conversation get(Long id) {
        Conversation c = conversationMapper.selectById(id);
        if (c == null) {
            throw new IllegalArgumentException("会话不存在: " + id);
        }
        return c;
    }

    public void touch(Long id) {
        Conversation c = get(id);
        c.setUpdatedAt(OffsetDateTime.now());
        conversationMapper.updateById(c);
    }

    public List<ConversationView> list() {
        List<Conversation> conversations = conversationMapper.selectList(
            new LambdaQueryWrapper<Conversation>().orderByDesc(Conversation::getUpdatedAt));
        Map<Long, String> lastByConversation = messageMapper.selectLastMessages().stream()
            .collect(Collectors.toMap(MessageMapper.LastMessageRow::getConversationId,
                MessageMapper.LastMessageRow::getContent, (a, b) -> a));
        return conversations.stream()
            .map(c -> new ConversationView(c.getId(), c.getTitle(), c.getKbId(),
                c.getCreatedAt(), c.getUpdatedAt(), lastByConversation.get(c.getId())))
            .toList();
    }

    public List<MessageView> messages(Long conversationId) {
        get(conversationId);
        List<Message> messages = messageMapper.selectList(
            new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .orderByAsc(Message::getId));
        return messages.stream().map(this::toView).toList();
    }

    public void delete(Long conversationId) {
        conversationMapper.deleteById(conversationId);   // message 由 DB CASCADE 删除
    }

    public List<RagSource> parseSources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<RagSource>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private MessageView toView(Message m) {
        return new MessageView(m.getId(), m.getRole(), m.getContent(),
            parseSources(m.getSources()), m.getCreatedAt());
    }
}
```

- [ ] **Step 7: ConversationController**

`controller/ConversationController.java`：
```java
package com.neuralbase.controller;

import com.neuralbase.common.Result;
import com.neuralbase.dto.ConversationView;
import com.neuralbase.dto.MessageView;
import com.neuralbase.entity.Conversation;
import com.neuralbase.service.ConversationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversation")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public Result<Conversation> create(@RequestBody(required = false) CreateConversationRequest req) {
        String title = req == null ? null : req.title();
        Long kbId = req == null ? null : req.kbId();
        return Result.ok(conversationService.create(title, kbId));
    }

    @GetMapping
    public Result<List<ConversationView>> list() {
        return Result.ok(conversationService.list());
    }

    @GetMapping("/{id}/messages")
    public Result<List<MessageView>> messages(@PathVariable Long id) {
        return Result.ok(conversationService.messages(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        conversationService.delete(id);
        return Result.ok(null);
    }

    public record CreateConversationRequest(String title, Long kbId) {
    }
}
```

- [ ] **Step 8: 跑测试通过 + 全量回归**

```bash
mvn test -Dtest=ConversationCrudTest
```
**预期：PASS**（V3 迁移在上下文启动时自动应用）。

再全量：
```bash
mvn test
```
**预期：全绿**（原 26 + 新 2 用例）。

- [ ] **Step 9: 提交**

```bash
cd /d/neural-base
git add backend/src/main/resources/db/migration/V3__chat_tables.sql \
        backend/src/main/java/com/neuralbase/entity/Conversation.java \
        backend/src/main/java/com/neuralbase/entity/Message.java \
        backend/src/main/java/com/neuralbase/mapper/ConversationMapper.java \
        backend/src/main/java/com/neuralbase/mapper/MessageMapper.java \
        backend/src/main/java/com/neuralbase/dto/RagSource.java \
        backend/src/main/java/com/neuralbase/dto/ConversationView.java \
        backend/src/main/java/com/neuralbase/dto/MessageView.java \
        backend/src/main/java/com/neuralbase/service/ConversationService.java \
        backend/src/main/java/com/neuralbase/controller/ConversationController.java \
        backend/src/test/java/com/neuralbase/chat/ConversationCrudTest.java
git commit -m "feat: conversation and message persistence with crud api

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: ChatService + RAG prompt + SSE 流式端点

**Files:**
- Create: `backend/src/main/java/com/neuralbase/dto/ChatRequest.java`
- Create: `backend/src/main/java/com/neuralbase/service/ChatService.java`
- Create: `backend/src/main/java/com/neuralbase/controller/ChatController.java`
- Test: `backend/src/test/java/com/neuralbase/chat/ChatServiceTest.java`

**Interfaces:**
- Consumes: Task 1 `ChatModel`（test 用 mock）、Task 2 `ConversationService`/`MessageMapper`、Phase 2 `DocumentService.search(String,Long,Integer)→List<SearchResult>`（record `(Long docId, String title, String content, double score)`）
- Produces: `POST /api/chat/stream`，body `ChatRequest{conversationId?, kbId?, question}` → `Flux<ServerSentEvent<String>>`（`text/event-stream`，事件 `meta`/`sources`/`delta`/`done`/`error`）

- [ ] **Step 1: 写失败测试**

`backend/src/test/java/com/neuralbase/chat/ChatServiceTest.java`：
```java
package com.neuralbase.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.config.MockChatConfig;
import com.neuralbase.dto.ChatRequest;
import com.neuralbase.dto.RagSource;
import com.neuralbase.entity.Conversation;
import com.neuralbase.entity.Document;
import com.neuralbase.entity.Message;
import com.neuralbase.mapper.ConversationMapper;
import com.neuralbase.mapper.MessageMapper;
import com.neuralbase.service.ChatService;
import com.neuralbase.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ChatServiceTest {

    @Autowired private ChatService chatService;
    @Autowired private ConversationMapper conversationMapper;
    @Autowired private MessageMapper messageMapper;
    @Autowired private DocumentService documentService;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void clearPrompts() {
        MockChatConfig.PROMPTS.clear();
    }

    @Test
    void firstMessageCreatesConversationAndPersistsBothSides() throws Exception {
        List<ServerSentEvent<String>> events =
            chatService.stream(new ChatRequest(null, null, "你好")).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events.get(0).event()).isEqualTo("meta");
        long convId = objectMapper.readTree(events.get(0).data()).get("conversationId").asLong();

        List<String> deltas = events.stream()
            .filter(e -> "delta".equals(e.event())).map(ServerSentEvent::data).toList();
        assertThat(deltas).containsExactly("mock 回答", "片段二");
        assertThat(events.get(events.size() - 1).event()).isEqualTo("done");

        Conversation conv = conversationMapper.selectById(convId);
        assertThat(conv.getTitle()).isEqualTo("你好");

        List<Message> messages = messageMapper.selectList(
            new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, convId).orderByAsc(Message::getId));
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getRole()).isEqualTo("USER");
        assertThat(messages.get(0).getContent()).isEqualTo("你好");
        assertThat(messages.get(1).getRole()).isEqualTo("ASSISTANT");
        assertThat(messages.get(1).getContent()).isEqualTo("mock 回答片段二");
    }

    @Test
    void ragModeRetrievesAndStoresSources() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "rag.txt", "text/plain",
            "NeuralBase RAG 测试内容。".repeat(200).getBytes());
        Document doc = documentService.upload(file, 1L);
        documentService.process(doc.getId());

        List<ServerSentEvent<String>> events =
            chatService.stream(new ChatRequest(null, 1L, "NeuralBase RAG 测试内容。")).collectList().block();
        assertThat(events).isNotNull();

        ServerSentEvent<String> sourcesEvent = events.stream()
            .filter(e -> "sources".equals(e.event())).findFirst().orElse(null);
        assertThat(sourcesEvent).isNotNull();
        List<RagSource> sources = objectMapper.readValue(sourcesEvent.data(),
            new TypeReference<List<RagSource>>() {});
        assertThat(sources).isNotEmpty();

        long convId = objectMapper.readTree(events.get(0).data()).get("conversationId").asLong();
        List<Message> messages = messageMapper.selectList(
            new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, convId).orderByAsc(Message::getId));
        assertThat(messages.get(1).getSources()).contains("NeuralBase RAG 测试内容");
    }

    @Test
    void secondTurnLoadsHistoryServerSide() {
        List<ServerSentEvent<String>> first =
            chatService.stream(new ChatRequest(null, null, "问题一")).collectList().block();
        long convId = objectMapper.readTree(first.get(0).data()).get("conversationId").asLong();

        chatService.stream(new ChatRequest(convId, null, "问题二")).collectList().block();

        Prompt lastPrompt = MockChatConfig.PROMPTS.get(MockChatConfig.PROMPTS.size() - 1);
        assertThat(lastPrompt.getInstructions()).anySatisfy(m -> {
            if (m instanceof UserMessage u) {
                assertThat(u.getText()).isEqualTo("问题一");
            }
        });
        assertThat(lastPrompt.getInstructions()).anySatisfy(m -> {
            if (m instanceof AssistantMessage a) {
                assertThat(a.getText()).isEqualTo("mock 回答片段二");
            }
        });
    }
}
```
> 说明：`ChatService.stream` 用 `collectList().block()` 直接订阅断言事件序列（不依赖 MockMvc 异步，避免 MVC 响应式适配下的断言坑）。Mock ChatModel 的 one-hot 前缀哈希让 query 与种子 chunk 同桶命中（同 `DocumentSearchTest` 原理）。

- [ ] **Step 2: 跑测试确认失败**

```bash
cd /d/neural-base/backend
export DB_PASSWORD='root'
mvn test -Dtest=ChatServiceTest
```
**预期：FAIL**（ChatService/chat 接口不存在）。

- [ ] **Step 3: ChatRequest DTO（带校验）**

`dto/ChatRequest.java`：
```java
package com.neuralbase.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(Long conversationId, Long kbId, @NotBlank(message = "问题不能为空") String question) {
}
```
> `@NotBlank` + Controller `@Valid` → 空问题走 `GlobalExceptionHandler` 返回 400（spring-boot-starter-validation 已在 pom）。

- [ ] **Step 4: ChatService**

`service/ChatService.java`：
```java
package com.neuralbase.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.dto.ChatRequest;
import com.neuralbase.dto.RagSource;
import com.neuralbase.entity.Conversation;
import com.neuralbase.entity.Message;
import com.neuralbase.mapper.MessageMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final int HISTORY_WINDOW = 10;
    private static final int TOP_K = 5;

    private final ChatModel chatModel;
    private final ConversationService conversationService;
    private final DocumentService documentService;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public ChatService(ChatModel chatModel, ConversationService conversationService,
                       DocumentService documentService, MessageMapper messageMapper,
                       ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.conversationService = conversationService;
        this.documentService = documentService;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    public Flux<ServerSentEvent<String>> stream(ChatRequest req) {
        Conversation conv = req.conversationId() == null
            ? conversationService.create(deriveTitle(req.question()), req.kbId())
            : conversationService.get(req.conversationId());
        Long convId = conv.getId();

        Message userMsg = new Message();
        userMsg.setConversationId(convId);
        userMsg.setRole("USER");
        userMsg.setContent(req.question());
        userMsg.setCreatedAt(OffsetDateTime.now());
        messageMapper.insert(userMsg);
        conversationService.touch(convId);

        List<Message> history = messageMapper.selectRecentAsc(convId, HISTORY_WINDOW);
        List<RagSource> sources = req.kbId() == null ? List.of()
            : documentService.search(req.question(), req.kbId(), TOP_K).stream()
                .map(r -> new RagSource(r.docId(), r.title(), r.content(), r.score()))
                .toList();

        List<org.springframework.ai.chat.messages.Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage(buildSystemPrompt(req.kbId(), sources)));
        for (Message m : history) {
            if ("USER".equals(m.getRole())) {
                promptMessages.add(new UserMessage(m.getContent()));
            } else {
                promptMessages.add(new AssistantMessage(m.getContent()));
            }
        }

        StringBuilder full = new StringBuilder();
        AtomicBoolean persisted = new AtomicBoolean(false);

        Flux<ServerSentEvent<String>> stream = chatModel.stream(new Prompt(promptMessages))
            .map(resp -> resp.getResult().getOutput().getText())
            .doOnNext(full::append)
            .map(text -> ServerSentEvent.<String>builder(text).event("delta").build())
            .doFinally(sig -> {
                if (persisted.compareAndSet(false, true)) {
                    persistAssistant(convId, full.toString(), sources);
                }
            })
            .onErrorResume(e ->
                Flux.just(ServerSentEvent.<String>builder(e.getMessage()).event("error").build()));

        List<ServerSentEvent<String>> prefix = new ArrayList<>();
        prefix.add(ServerSentEvent.<String>builder(metaJson(convId)).event("meta").build());
        if (!sources.isEmpty()) {
            prefix.add(ServerSentEvent.<String>builder(toJson(sources)).event("sources").build());
        }
        return Flux.concat(
            Flux.fromIterable(prefix),
            stream,
            Flux.just(ServerSentEvent.<String>builder("").event("done").build()));
    }

    private String deriveTitle(String question) {
        String t = question.strip();
        return t.length() > 20 ? t.substring(0, 20) : t;
    }

    private String buildSystemPrompt(Long kbId, List<RagSource> sources) {
        if (kbId == null) {
            return "你是 NeuralBase 的 AI 助手，擅长技术问答和知识整理。";
        }
        String context = sources.stream()
            .map(s -> "- " + s.content())
            .collect(Collectors.joining("\n"));
        return "你是 NeuralBase 的 AI 助手。请基于以下检索到的文档片段回答用户问题。"
            + "如果文档中没有相关信息，请明确告知用户。\n\n--- 检索到的文档片段 ---\n"
            + (context.isEmpty() ? "（未检索到相关内容）" : context);
    }

    private void persistAssistant(Long conversationId, String content, List<RagSource> sources) {
        Message assistant = new Message();
        assistant.setConversationId(conversationId);
        assistant.setRole("ASSISTANT");
        assistant.setContent(content);
        assistant.setCreatedAt(OffsetDateTime.now());
        if (!sources.isEmpty()) {
            assistant.setSources(toJson(sources));
        }
        messageMapper.insert(assistant);
        conversationService.touch(conversationId);
    }

    private String metaJson(Long conversationId) {
        return "{\"conversationId\":" + conversationId + "}";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
```
> 说明：`doFinally` 在完成/出错/客户端断开三种情况下都会落库助手消息（`AtomicBoolean` 保证只落一次），单用户场景可接受；RAG 检索片段与 `sources` 事件同步写入 `message.sources`。`Message` 实体名与 Spring AI 消息基类无冲突（只 import 具体类 `SystemMessage`/`UserMessage`/`AssistantMessage`，消息列表用全限定名）。

- [ ] **Step 5: ChatController**

`controller/ChatController.java`：
```java
package com.neuralbase.controller;

import com.neuralbase.dto.ChatRequest;
import com.neuralbase.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody @Valid ChatRequest request) {
        return chatService.stream(request);
    }
}
```
> Spring MVC 直接返回 `Flux<ServerSentEvent<String>>` 即流式 SSE（`ReactiveTypeHandler` + reactor-core，均在 classpath）。鉴权走 Bearer header（SecurityConfig 已保护 `/api/**`）。

- [ ] **Step 6: 跑测试通过 + 全量回归**

```bash
mvn test -Dtest=ChatServiceTest
```
**预期：PASS**（3 个用例）。

再全量：
```bash
mvn test
```
**预期：全绿**（原 28 + 新 3 用例 = 31）。

> **已知风险**：若 `mvn test` 报编译错（如 `ChatModel.stream` 方法签名或 `Generation` 构造器与预期不符），以本计划 Global Constraints 中 javap 验证过的签名为准：`ChatModel` 抽象 `call(Prompt)`、`stream(Prompt)` default（继承 `StreamingChatModel` 抽象）；`new Generation(new AssistantMessage(text))`；`resp.getResult().getOutput().getText()`。

- [ ] **Step 7: 提交**

```bash
cd /d/neural-base
git add backend/src/main/java/com/neuralbase/dto/ChatRequest.java \
        backend/src/main/java/com/neuralbase/service/ChatService.java \
        backend/src/main/java/com/neuralbase/controller/ChatController.java \
        backend/src/test/java/com/neuralbase/chat/ChatServiceTest.java
git commit -m "feat: sse chat endpoint with rag mode and history

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: 全量回归 + 真实冒烟（可选）+ 记录

**Files:**
- 无代码变更；可选真实冒烟。

- [ ] **Step 1: 全量回归**

```bash
cd /d/neural-base/backend
export DB_PASSWORD='root'
mvn test
```
**预期：全绿**（31 个用例，不依赖任何真实 API key）。

- [ ] **Step 2: 真实冒烟（可选，等用户提供 DEEPSEEK_API_KEY）**

```bash
cd /d/neural-base/backend
export DEEPSEEK_API_KEY='<你的 key>'
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```
另一终端：
```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python -c 'import sys,json;print(json.load(sys.stdin)["data"])')
# 普通模式
curl -N -X POST http://localhost:8081/api/chat/stream \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"question":"用一句话介绍 Java 虚拟线程"}'
# RAG 模式（先上传文档进 kb=1，再带 kbId 提问）
```
**预期：** SSE 流式返回 `meta` → `delta`* → `done`；RAG 模式多出 `sources` 事件且回答引用检索片段。

- [ ] **Step 3: 更新项目记忆**

更新 `C:\Users\weihuangdj\.claude\projects\C--Users-weihuangdj\memory\neuralbase-project-status.md`：Phase 3（DeepSeek 聊天 + RAG + 会话持久化）完成；记录新增坑（如有）：DeepSeek base-url 不带 `/v1`、`OpenAiChatModel` 无两参构造器、`Generation` 需 `AssistantMessage`、`@Configuration` Mock ChatModel 自动注册、MVC 返回 `Flux<ServerSentEvent>` 需 reactor。

---

## 完成标准（Phase 3 Done）

- `POST /api/chat/stream`（Bearer + JSON body）→ `text/event-stream`：`meta` →（RAG 时）`sources` → `delta`* → `done`；`conversationId` 为空自动建会话，标题取首条问题前 20 字符
- 会话接口：`POST`/`GET`/`GET {id}/messages`/`DELETE {id}`；删除级联删消息
- 服务端加载最近 10 条历史拼 prompt；RAG 复用 `DocumentService.search` topK=5，片段存 `message.sources`
- `mvn test` 全绿（测试用 mock ChatModel + mock EmbeddingModel，不依赖真实 API）
- 可选：用户提供 `DEEPSEEK_API_KEY` 后真实冒烟通过

## Self-Review

- **Spec coverage**：Roadmap Phase 3（SSE 流式 + 知识库 RAG 模式 + DeepSeek 推理）全覆盖；会话持久化=列表+删除（已裁决）、无重命名/收藏/反馈（Phase 6）。设计文档「双模式切换」=请求 `kbId` 有无；「模型切换」= `model` 参数预留（Task 1 配置里 `neuralbase.ai.deepseek-model`）。
- **Placeholder scan**：无 TBD；唯一外部输入是 `DEEPSEEK_API_KEY`（前置条件已声明，测试不依赖）。所有代码块为最终实现。
- **Type consistency**：`ChatRequest(Long,Long,String)`、`RagSource(Long,String,String,double)`、`ConversationView(Long,String,Long,OffsetDateTime,OffsetDateTime,String)`、`MessageView(Long,String,String,List<RagSource>,OffsetDateTime)` 在 Task 2/3 间一致；`ConversationService` 的 `create/get/touch/list/messages/delete/parseSources` 签名在 Task 3 中按名复用；`DocumentService.search(String,Long,Integer)→List<SearchResult>` 直接映射 `RagSource`。SSE 事件名 `meta/sources/delta/done/error` 在 ChatService 与测试断言中一致。
