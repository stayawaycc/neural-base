# 延后项补做 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 会话重命名/收藏(含筛选)、消息点赞点踩、八股详情原文 Tab——前端收尾最后一项。

**Architecture:** 后端 V6 迁移加 `conversation.favorite` 与 `message.feedback` 两列 + 三个接口(重命名/收藏切换/反馈设置);前端 chat store 三个新动作 + ChatView 内联重命名/星标/筛选 + ChatMessage 反馈按钮 + BaguwenDetail Tab。

**Tech Stack:** Spring Boot 3.5.16 + MyBatis-Plus + PostgreSQL;Vue 3.5 + Pinia + Naive UI;vitest + happy-dom;CDP 验收。

**规格:** `docs/superpowers/specs/2026-08-14-deferred-items-design.md`

## Global Constraints

- 后端测试:`@SpringBootTest @ActiveProfiles("test") @Import(MockEmbeddingConfig.class)`;HTTP 测试加 `@AutoConfigureMockMvc`,登录 admin/admin123,token 取 `data` 字段;测试命令 `cd backend && DB_PASSWORD=root mvn test`
- 业务错误抛 `IllegalArgumentException`(全局 400);前端命令 `cd frontend && npx vitest run`、`npx vue-tsc --noEmit`、`npm run build`
- 反馈值仅 `LIKE`/`DISLIKE`/null;收藏只标记不做排序;UI 亮暗两态按既有映射表
- commit 英文 subject;逐任务提交;不改后端 ChatService 流式逻辑

---

### Task 1: V6 迁移 + 实体与视图字段

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__chat_feedback.sql`
- Modify: `backend/src/main/java/com/neuralbase/entity/Conversation.java`、`backend/src/main/java/com/neuralbase/entity/Message.java`
- Modify: `backend/src/main/java/com/neuralbase/dto/ConversationView.java`、`backend/src/main/java/com/neuralbase/dto/MessageView.java`、`backend/src/main/java/com/neuralbase/service/ConversationService.java`

**Interfaces:**
- Produces: `conversation.favorite`(boolean)、`message.feedback`(String);`ConversationView` 与 `MessageView` 各多一个字段——Task 2/3/4 消费

- [ ] **Step 1: 写迁移与实体**

新建 `backend/src/main/resources/db/migration/V6__chat_feedback.sql`:

```sql
ALTER TABLE conversation ADD COLUMN favorite BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE message ADD COLUMN feedback VARCHAR(10);
```

`Conversation.java` 加:

```java
    private Boolean favorite;
```

`Message.java` 加:

```java
    private String feedback;
```

- [ ] **Step 2: 扩展视图 record**

先定位所有构造点:

Run: `cd backend && grep -rn "new ConversationView\|new MessageView" src/main/java`

Expected: `ConversationService.list()`(ConversationView)、`ConversationService.toView()`(MessageView);若还有其他构造点,同步补参

`ConversationView.java` 改为(favorite 加在 kbId 后):

```java
public record ConversationView(Long id, String title, Long kbId, Boolean favorite,
                               OffsetDateTime createdAt, OffsetDateTime updatedAt,
                               String lastMessageContent) {
}
```

`ConversationService.list()` 映射改为:

```java
        return conversations.stream()
            .map(c -> new ConversationView(c.getId(), c.getTitle(), c.getKbId(), c.getFavorite(),
                c.getCreatedAt(), c.getUpdatedAt(), lastByConversation.get(c.getId())))
            .toList();
```

`MessageView.java` 改为:

```java
public record MessageView(Long id, String role, String content, List<RagSource> sources,
                          String feedback, OffsetDateTime createdAt) {
}
```

`ConversationService.toView()` 改为:

```java
    private MessageView toView(Message m) {
        return new MessageView(m.getId(), m.getRole(), m.getContent(),
            parseSources(m.getSources()), m.getFeedback(), m.getCreatedAt());
    }
```

- [ ] **Step 3: 验证(编译 + 既有测试)**

Run: `cd backend && DB_PASSWORD=root mvn test -q`
Expected: 编译通过,现有测试全绿(V6 自动应用到 test 库)

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/resources/db/migration/V6__chat_feedback.sql backend/src/main/java/com/neuralbase/entity/Conversation.java backend/src/main/java/com/neuralbase/entity/Message.java backend/src/main/java/com/neuralbase/dto/ConversationView.java backend/src/main/java/com/neuralbase/dto/MessageView.java backend/src/main/java/com/neuralbase/service/ConversationService.java
git commit -m "feat: v6 migration - conversation favorite and message feedback columns"
```

---

### Task 2: 重命名 + 收藏接口(TDD)

**Files:**
- Modify: `backend/src/main/java/com/neuralbase/service/ConversationService.java`
- Modify: `backend/src/main/java/com/neuralbase/controller/ConversationController.java`
- Test: `backend/src/test/java/com/neuralbase/chat/ConversationCrudTest.java`(扩展)

**Interfaces:**
- Consumes: Task 1 字段
- Produces: `PUT /api/conversation/{id}` `{title}` → `Result<ConversationView>`;`PUT /api/conversation/{id}/favorite` → `Result<Boolean>` — Task 4 前端消费

- [ ] **Step 1: 写失败测试**

`ConversationCrudTest.java` 加(import 补 `put`):

```java
    @Test
    void renameValidatesAndPersists() throws Exception {
        String tk = token();
        long id = objectMapper.readTree(mvc.perform(post("/api/conversation")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"旧标题\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
            .get("data").get("id").asLong();

        mvc.perform(put("/api/conversation/" + id)
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"新标题\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("新标题"));

        mvc.perform(put("/api/conversation/" + id)
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("标题")));

        mvc.perform(put("/api/conversation/" + id)
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + "x".repeat(101) + "\"}"))
            .andExpect(status().isBadRequest());

        mvc.perform(put("/api/conversation/999999")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void favoriteToggles() throws Exception {
        String tk = token();
        long id = objectMapper.readTree(mvc.perform(post("/api/conversation")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"收藏测试\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
            .get("data").get("id").asLong();

        mvc.perform(put("/api/conversation/" + id + "/favorite")
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(true));

        mvc.perform(put("/api/conversation/" + id + "/favorite")
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(false));

        mvc.perform(get("/api/conversation").header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk());
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=ConversationCrudTest -q`
Expected: FAIL — PUT 端点不存在(405/404)

- [ ] **Step 3: 写最小实现**

`ConversationService.java` 加:

```java
    public ConversationView rename(Long id, String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (title.length() > 100) {
            throw new IllegalArgumentException("标题过长(最多 100 字符)");
        }
        Conversation c = get(id);
        c.setTitle(title);
        c.setUpdatedAt(OffsetDateTime.now());
        conversationMapper.updateById(c);
        return list().stream().filter(v -> v.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + id));
    }

    public boolean toggleFavorite(Long id) {
        Conversation c = get(id);
        boolean next = !Boolean.TRUE.equals(c.getFavorite());
        c.setFavorite(next);
        conversationMapper.updateById(c);
        return next;
    }
```

`ConversationController.java` 加(import `PutMapping`):

```java
    @PutMapping("/{id}")
    public Result<ConversationView> rename(@PathVariable Long id, @RequestBody RenameRequest req) {
        return Result.ok(conversationService.rename(id, req == null ? null : req.title()));
    }

    @PutMapping("/{id}/favorite")
    public Result<Boolean> toggleFavorite(@PathVariable Long id) {
        return Result.ok(conversationService.toggleFavorite(id));
    }

    public record RenameRequest(String title) {
    }
```

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=ConversationCrudTest -q`
Expected: PASS(原 2 + 新 2)

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/neuralbase/service/ConversationService.java backend/src/main/java/com/neuralbase/controller/ConversationController.java backend/src/test/java/com/neuralbase/chat/ConversationCrudTest.java
git commit -m "feat: conversation rename and favorite toggle endpoints"
```

---

### Task 3: 消息反馈接口(TDD)

**Files:**
- Create: `backend/src/main/java/com/neuralbase/controller/MessageController.java`
- Modify: `backend/src/main/java/com/neuralbase/service/ConversationService.java`
- Test: `backend/src/test/java/com/neuralbase/chat/MessageFeedbackTest.java`(新建)

**Interfaces:**
- Consumes: `message.feedback` 列(Task 1)
- Produces: `POST /api/message/{id}/feedback` `{feedback}` → `Result<Void>` — Task 4 前端消费

- [ ] **Step 1: 写失败测试**

新建 `backend/src/test/java/com/neuralbase/chat/MessageFeedbackTest.java`:

```java
package com.neuralbase.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuralbase.config.MockEmbeddingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(MockEmbeddingConfig.class)
class MessageFeedbackTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asText();
    }

    private long seedConversationAndMessage(String tk) throws Exception {
        long cid = objectMapper.readTree(mvc.perform(post("/api/conversation")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"feedback-test\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
            .get("data").get("id").asLong();
        jdbcTemplate.update(
            "INSERT INTO message (conversation_id, role, content, created_at) VALUES (?, 'ASSISTANT', 'hi', now())",
            cid);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM message WHERE conversation_id = ?", Long.class, cid);
    }

    @Test
    void feedbackSetSwitchCancel() throws Exception {
        String tk = token();
        long mid = seedConversationAndMessage(tk);

        mvc.perform(post("/api/message/" + mid + "/feedback")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\":\"LIKE\"}"))
            .andExpect(status().isOk());

        mvc.perform(post("/api/message/" + mid + "/feedback")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\":\"DISLIKE\"}"))
            .andExpect(status().isOk());

        mvc.perform(post("/api/message/" + mid + "/feedback")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\":null}"))
            .andExpect(status().isOk());

        String fb = jdbcTemplate.queryForObject(
            "SELECT feedback FROM message WHERE id = ?", String.class, mid);
        assertThat(fb).isNull();
    }

    @Test
    void feedbackRejectsInvalidAndMissing() throws Exception {
        String tk = token();
        long mid = seedConversationAndMessage(tk);

        mvc.perform(post("/api/message/" + mid + "/feedback")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\":\"LOVE\"}"))
            .andExpect(status().isBadRequest());

        mvc.perform(post("/api/message/999999/feedback")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\":\"LIKE\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("不存在")));
    }

    @Test
    void messagesExposeFeedback() throws Exception {
        String tk = token();
        long cid = jdbcTemplate.queryForObject(
            "SELECT conversation_id FROM message WHERE id = ?", Long.class,
            seedConversationAndMessage(tk));

        mvc.perform(get("/api/conversation/" + cid + "/messages")
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].feedback").doesNotExist())
            .andReturn();

        long mid = jdbcTemplate.queryForObject(
            "SELECT id FROM message WHERE conversation_id = ?", Long.class, cid);
        mvc.perform(post("/api/message/" + mid + "/feedback")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\":\"LIKE\"}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/conversation/" + cid + "/messages")
                .header("Authorization", "Bearer " + tk))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].feedback").value("LIKE"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=MessageFeedbackTest -q`
Expected: FAIL — `/api/message/...` 404(无 MessageController)

- [ ] **Step 3: 写最小实现**

新建 `backend/src/main/java/com/neuralbase/controller/MessageController.java`:

```java
package com.neuralbase.controller;

import com.neuralbase.common.Result;
import com.neuralbase.service.ConversationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/message")
public class MessageController {

    private final ConversationService conversationService;

    public MessageController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/{id}/feedback")
    public Result<Void> feedback(@PathVariable Long id, @RequestBody FeedbackRequest req) {
        conversationService.setFeedback(id, req == null ? null : req.feedback());
        return Result.ok(null);
    }

    public record FeedbackRequest(String feedback) {
    }
}
```

`ConversationService.java` 加:

```java
    public void setFeedback(Long messageId, String feedback) {
        Message m = messageMapper.selectById(messageId);
        if (m == null) {
            throw new IllegalArgumentException("消息不存在: " + messageId);
        }
        if (feedback != null && !feedback.equals("LIKE") && !feedback.equals("DISLIKE")) {
            throw new IllegalArgumentException("feedback 只能是 LIKE/DISLIKE/null");
        }
        m.setFeedback(feedback);
        messageMapper.updateById(m);
    }
```

注意:`messageMapper.updateById(m)` 对 null feedback 不生效(MyBatis-Plus 默认忽略 null)——取消反馈需用 SQL:

```java
        if (feedback == null) {
            jdbcTemplate.update("UPDATE message SET feedback = NULL WHERE id = ?", messageId);
        } else {
            m.setFeedback(feedback);
            messageMapper.updateById(m);
        }
```

为此 `ConversationService` 需注入 `JdbcTemplate`(构造器加参数并补 import)。

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && DB_PASSWORD=root mvn test -Dtest=MessageFeedbackTest -q`
Expected: 3 个测试全 PASS

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/neuralbase/controller/MessageController.java backend/src/main/java/com/neuralbase/service/ConversationService.java backend/src/test/java/com/neuralbase/chat/MessageFeedbackTest.java
git commit -m "feat: message feedback endpoint (like/dislike/null)"
```

---

### Task 4: 前端 api/types + chat store(TDD)

**Files:**
- Modify: `frontend/src/api/conversation.ts`
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/stores/chat.ts`
- Test: `frontend/src/stores/__tests__/chat.test.ts`(扩展)

**Interfaces:**
- Consumes: Task 2/3 接口
- Produces: `renameConversation(id, title)`、`toggleFavorite(id)`、`setMessageFeedback(id, feedback)`(api 层);store 动作 `renameConversation`/`toggleFavorite`/`setFeedback(index, feedback)`;`ChatUiMessage` 增 `id`/`feedback` — Task 5 消费

- [ ] **Step 1: 写失败测试**

`chat.test.ts`(现有文件,在其 describe 内追加;先看其现有 mock 结构,api/conversation 的 mock 需补三个函数):

```ts
  it('renameConversation 更新列表标题', async () => {
    vi.mocked(renameConversation).mockResolvedValue({ id: 1, title: '新名', favorite: false, createdAt: '', updatedAt: '' } as ConversationView)
    const store = useChatStore()
    store.conversations = [{ id: 1, title: '旧名', favorite: false, createdAt: '', updatedAt: '' }]
    await store.renameConversation(1, '新名')
    expect(renameConversation).toHaveBeenCalledWith(1, '新名')
    expect(store.conversations[0].title).toBe('新名')
  })

  it('toggleFavorite 切换并更新列表', async () => {
    vi.mocked(toggleFavorite).mockResolvedValue(true)
    const store = useChatStore()
    store.conversations = [{ id: 1, title: 'a', favorite: false, createdAt: '', updatedAt: '' }]
    await store.toggleFavorite(1)
    expect(store.conversations[0].favorite).toBe(true)
  })

  it('setFeedback 写库并更新本地消息', async () => {
    vi.mocked(setMessageFeedback).mockResolvedValue(null)
    const store = useChatStore()
    store.messages = [{ id: 9, role: 'ASSISTANT', content: 'hi' }]
    await store.setFeedback(0, 'LIKE')
    expect(setMessageFeedback).toHaveBeenCalledWith(9, 'LIKE')
    expect(store.messages[0].feedback).toBe('LIKE')
  })
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/stores/__tests__/chat.test.ts`
Expected: FAIL — api 无 `renameConversation` 等导出

- [ ] **Step 3: 写最小实现**

`types/index.ts` 改:

```ts
export interface ConversationView {
  id: number
  title: string
  kbId?: number
  favorite: boolean
  createdAt: string
  updatedAt: string
  lastMessageContent?: string
}

export interface MessageView {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  sources?: RagSource[]
  feedback?: 'LIKE' | 'DISLIKE' | null
  createdAt: string
}
```

`api/conversation.ts` 加:

```ts
export const renameConversation = (id: number, title: string) =>
  http.put<ConversationView>(`/conversation/${id}`, { title })

export const toggleFavorite = (id: number) =>
  http.put<boolean>(`/conversation/${id}/favorite`)

export const setMessageFeedback = (id: number, feedback: 'LIKE' | 'DISLIKE' | null) =>
  http.post<null>(`/message/${id}/feedback`, { feedback })
```

`stores/chat.ts` 改:

```ts
export interface ChatUiMessage {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  sources?: RagSource[]
  feedback?: 'LIKE' | 'DISLIKE' | null
  streaming?: boolean
}
```

`openConversation` 映射加:

```ts
    messages.value = history.map((m) => ({
      id: m.id,
      role: m.role,
      content: m.content,
      sources: m.sources,
      feedback: m.feedback,
    }))
```

`send` 里两处 push 加 id 占位(本地新消息尚无服务端 id):

```ts
    messages.value.push({ id: 0, role: 'USER', content: question })
    messages.value.push({ id: 0, role: 'ASSISTANT', content: '', streaming: true })
```

store 新动作:

```ts
  async function renameConversation(id: number, title: string) {
    const updated = await conversationApi.renameConversation(id, title)
    const target = conversations.value.find((c) => c.id === id)
    if (target) target.title = updated.title
  }

  async function toggleFavorite(id: number) {
    const next = await conversationApi.toggleFavorite(id)
    const target = conversations.value.find((c) => c.id === id)
    if (target) target.favorite = next
  }

  async function setFeedback(index: number, feedback: 'LIKE' | 'DISLIKE' | null) {
    const msg = messages.value[index]
    if (!msg || msg.id === 0) return // 流式中的本地消息尚无服务端 id,不支持反馈
    await conversationApi.setMessageFeedback(msg.id, feedback)
    msg.feedback = feedback
  }
```

return 块加 `renameConversation, toggleFavorite, setFeedback`。

- [ ] **Step 4: 运行确认通过**

Run: `cd frontend && npx vitest run src/stores/__tests__/chat.test.ts && npx vue-tsc --noEmit`
Expected: chat 测试全 PASS(含新 3 例),typecheck 无输出(注意:ChatUiMessage.id 变为必填,既有 chat 测试中手写 messages 的用例需补 `id` 字段,按报错补)

- [ ] **Step 5: 提交**

```bash
git add frontend/src/api/conversation.ts frontend/src/types/index.ts frontend/src/stores/chat.ts frontend/src/stores/__tests__/chat.test.ts
git commit -m "feat: rename, favorite and message feedback in chat store and api"
```

---

### Task 5: ChatView 内联重命名 + 星标 + 筛选;ChatMessage 反馈按钮(TDD)

**Files:**
- Modify: `frontend/src/components/ChatMessage.vue`(反馈按钮 + emit)
- Modify: `frontend/src/views/ChatView.vue`(侧栏交互 + 筛选)
- Test: `frontend/src/components/__tests__/ChatMessage.test.ts`(扩展)

**Interfaces:**
- Consumes: Task 4 store 动作
- Produces: `ChatMessage` emit `feedback`(`'LIKE'|'DISLIKE'|null`) — ChatView 接线

- [ ] **Step 1: 写失败测试**

`ChatMessage.test.ts` 加:

```ts
  it('助手非流式消息渲染 👍👎,点击高亮项 emit null 取消', async () => {
    const wrapper = mount(ChatMessage, {
      props: { role: 'ASSISTANT', content: '回答', feedback: 'LIKE' },
    })
    const likeBtn = wrapper.find('button.fb-like')
    const dislikeBtn = wrapper.find('button.fb-dislike')
    expect(likeBtn.exists()).toBe(true)
    expect(dislikeBtn.exists()).toBe(true)
    expect(likeBtn.attributes('class')).toContain('fb-active')
    await likeBtn.trigger('click')
    expect(wrapper.emitted('feedback')?.[0]).toEqual([null])
    await dislikeBtn.trigger('click')
    expect(wrapper.emitted('feedback')?.[1]).toEqual(['DISLIKE'])
  })

  it('流式与用户消息不显示反馈按钮', () => {
    const streaming = mount(ChatMessage, {
      props: { role: 'ASSISTANT', content: '', streaming: true },
    })
    expect(streaming.find('button.fb-like').exists()).toBe(false)
    const user = mount(ChatMessage, { props: { role: 'USER', content: '你好' } })
    expect(user.find('button.fb-like').exists()).toBe(false)
  })
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/components/__tests__/ChatMessage.test.ts`
Expected: FAIL — 找不到 `button.fb-like`

- [ ] **Step 3: 写最小实现**

`ChatMessage.vue` script 加:

```ts
const props = defineProps<{
  role: 'USER' | 'ASSISTANT'
  content: string
  sources?: RagSource[]
  streaming?: boolean
  feedback?: 'LIKE' | 'DISLIKE' | null
}>()

const emit = defineEmits<{ feedback: ['LIKE' | 'DISLIKE' | null] }>()

function onFeedback(type: 'LIKE' | 'DISLIKE') {
  emit('feedback', props.feedback === type ? null : type)
}
```

(原 `defineProps` 调用替换为 `const props = defineProps(...)`)

助手消息 `</div>`(正文 div 之后、`</div>` 外层之前)加:

```html
        <div v-if="role === 'ASSISTANT' && !streaming" class="mt-1.5 flex items-center gap-2">
          <button
            class="fb-like rounded p-1 text-xs transition-colors"
            :class="feedback === 'LIKE' ? 'fb-active text-cyan-600 dark:text-cyan-300' : 'text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300'"
            title="赞"
            @click="onFeedback('LIKE')"
          >
            <span class="text-sm leading-none">👍</span>
          </button>
          <button
            class="fb-dislike rounded p-1 text-xs transition-colors"
            :class="feedback === 'DISLIKE' ? 'fb-active text-red-500 dark:text-red-300' : 'text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300'"
            title="踩"
            @click="onFeedback('DISLIKE')"
          >
            <span class="text-sm leading-none">👎</span>
          </button>
        </div>
```

`ChatView.vue`:

1. script 加:

```ts
import { NInput, NSwitch } from 'naive-ui'  // 并入现有 naive-ui import 行
const renamingId = ref<number | null>(null)
const renameText = ref('')
const onlyFavorites = ref(false)

const visibleConversations = computed(() =>
  onlyFavorites.value ? chatStore.conversations.filter((c) => c.favorite) : chatStore.conversations,
)

function startRename(c: ConversationView) {
  renamingId.value = c.id
  renameText.value = c.title
}

async function commitRename(c: ConversationView) {
  const title = renameText.value.trim()
  renamingId.value = null
  if (!title || title === c.title) return
  try {
    await chatStore.renameConversation(c.id, title)
  } catch (e) {
    message.error((e as Error).message)
  }
}
```

(import `message` from '@/utils/feedback';`ConversationView` type import)

2. 侧栏顶部(会话标题行)加筛选开关:

```html
      <div class="mb-3 flex items-center justify-between">
        <span class="text-sm text-gray-600 dark:text-gray-400">会话</span>
        <div class="flex items-center gap-2">
          <span class="text-xs text-gray-500">仅看收藏</span>
          <n-switch v-model:value="onlyFavorites" size="small" />
          <n-button size="small" quaternary @click="onNewConversation">
            <template #icon><Icon icon="mdi:plus" class="h-4 w-4" /></template>
            新建
          </n-button>
        </div>
      </div>
```

3. 桌面列表 `v-for="c in chatStore.conversations"` → `v-for="c in visibleConversations"`;标题区改为:

```html
          <div class="flex items-center justify-between gap-2">
            <n-input
              v-if="renamingId === c.id"
              v-model:value="renameText"
              size="tiny"
              class="min-w-0 flex-1"
              @keyup.enter="commitRename(c)"
              @keyup.esc="renamingId = null"
              @blur="commitRename(c)"
            />
            <span v-else class="truncate text-sm text-gray-800 dark:text-gray-200">{{ c.title }}</span>
            <div class="flex shrink-0 items-center gap-1">
              <button
                class="btn-press rounded p-0.5 transition-colors"
                :class="c.favorite ? 'text-amber-400' : 'text-gray-400 hover:text-amber-400 dark:text-gray-600'"
                title="收藏"
                @click="chatStore.toggleFavorite(c.id)"
              >
                <Icon :icon="c.favorite ? 'mdi:star' : 'mdi:star-outline'" class="h-4 w-4" />
              </button>
              <button
                v-if="renamingId !== c.id"
                class="btn-press rounded p-0.5 text-gray-400 transition-colors hover:text-gray-600 dark:text-gray-600 dark:hover:text-gray-300"
                title="重命名"
                @click="startRename(c)"
              >
                <Icon icon="mdi:pencil-outline" class="h-4 w-4" />
              </button>
              <n-popconfirm @positive-click="chatStore.removeConversation(c.id)">
                <template #trigger>
                  <span class="text-gray-400 hover:text-red-400 dark:text-gray-600">
                    <Icon icon="mdi:trash-can-outline" class="h-4 w-4" />
                  </span>
                </template>
                删除该会话?
              </n-popconfirm>
            </div>
          </div>
```

4. 空态区分筛选:

```html
        <div v-if="!visibleConversations.length" class="py-8 text-center text-xs text-gray-400 dark:text-gray-600">
          {{ onlyFavorites ? '没有收藏的会话' : '暂无会话,直接提问即可创建' }}
        </div>
```

5. 消息列表 `ChatMessage` 加 props 与事件:

```html
        <ChatMessage
          v-for="(m, i) in chatStore.messages"
          :key="i"
          :role="m.role"
          :content="m.content"
          :sources="m.sources"
          :streaming="m.streaming"
          :feedback="m.feedback"
          @feedback="(fb) => chatStore.setFeedback(i, fb)"
        />
```

移动端抽屉列表同步(标题+星标+铅笔+空态文案,复用同一套 visibleConversations)。

- [ ] **Step 4: 运行确认通过**

Run: `cd frontend && npx vitest run src/components/__tests__/ChatMessage.test.ts src/stores/__tests__/chat.test.ts && npx vue-tsc --noEmit && npm run build`
Expected: 测试全 PASS,typecheck 无输出,build 成功

- [ ] **Step 5: 提交**

```bash
git add frontend/src/components/ChatMessage.vue frontend/src/components/__tests__/ChatMessage.test.ts frontend/src/views/ChatView.vue
git commit -m "feat: inline rename, favorite filter and message feedback buttons"
```

---

### Task 6: 八股详情原文 Tab

**Files:**
- Modify: `frontend/src/views/BaguwenDetailView.vue`

**Interfaces:** 无新接口(纯前端渲染)

- [ ] **Step 1: 实现**

script 加(import `NTabs, NTabPane` 并入现有 naive-ui import;`ref` 已有):

```ts
const activeTab = ref('qa')
```

template 标题区(`<div class="flex flex-wrap items-center gap-2">` 标签行)之后、问答列表 `<div class="space-y-3">` 之前,包一层 Tab:

```html
    <n-tabs v-model:value="activeTab" type="line">
      <n-tab-pane name="qa" tab="问答">
        <div class="space-y-3 pt-2">
          <!-- 原 v-for="(qa, i) in detail.qaPairs" 内容与空态保持不变 -->
        </div>
      </n-tab-pane>
      <n-tab-pane name="article" tab="原文">
        <pre
          v-if="detail.content"
          class="whitespace-pre-wrap pt-2 text-sm leading-relaxed text-gray-600 dark:text-gray-400"
        >{{ detail.content }}</pre>
        <div v-else class="py-8 text-center text-sm text-gray-400 dark:text-gray-600">
          未提取到原文
        </div>
      </n-tab-pane>
    </n-tabs>
```

(「原文链接 ↗」保留在标签行,不随 Tab 移动)

- [ ] **Step 2: 验证与提交**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run build`
Expected: 全绿 + typecheck 无输出 + build 成功

```bash
git add frontend/src/views/BaguwenDetailView.vue
git commit -m "feat: qa/article tabs in baguwen detail"
```

---

### Task 7: 全量回归 + CDP 验收 + 文档

- [ ] **Step 1: 双端全量**

Run: `cd backend && DB_PASSWORD=root mvn test -q`(预期 89 + 4 新 = 93 绿)
Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run build`(预期 59 + 新 ≈ 64 绿)

- [ ] **Step 2: CDP 真机验收**(规格第四节)

1. 重命名:Chat 侧栏 hover 铅笔 → 输入新名回车 → 列表更新;刷新持久;空值回车不生效
2. 收藏:点星标变实心;开「仅看收藏」只剩收藏项;关掉恢复;刷新持久
3. 反馈:助手消息 👍 高亮 → 点 👎 切换 → 再点 👎 取消;刷新后状态持久;流式期间无按钮
4. 八股详情:「问答/原文」Tab 切换,原文渲染文本
5. 亮暗两态快速巡检 + SPA 切换不回归

- [ ] **Step 3: 验收修复(如有)提交**

```bash
git add -A
git commit -m "fix: deferred items acceptance findings"
```

- [ ] **Step 4: PROGRESS.md 更新并提交**

在「六、前端状态」记「延后项补做完成」;测试数更新;下一步只剩 Phase 5 AI 日报与 Phase 7/8 Docker:

```bash
git add docs/PROGRESS.md
git commit -m "docs: record deferred items completion"
```

---

## Self-Review 记录

- **规格覆盖**:§2.1 → Task 1;§2.2/2.3 → Task 2/3;§3.1/3.2 → Task 4/5;§3.3 → Task 6;§四 → Task 7
- **占位符**:无 TBD;MyBatis-Plus null 更新陷阱在 Task 3 已给出 SQL 方案
- **类型一致**:`renameConversation`/`toggleFavorite`/`setMessageFeedback`(api)↔ store 同名动作 ↔ 测试引用一致;`ChatUiMessage.id/feedback` 在 Task 4 定义、Task 5 消费;`ConversationView.favorite` 前后端同构
