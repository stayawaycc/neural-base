# 延后项补做设计规格

> 日期:2026-08-14　状态:已敲定(经 brainstorming 澄清)
> 所属:前端收尾 · 子项目 4/4(最后一项)
> 内容:会话重命名/收藏、消息点赞点踩、八股原文渲染(此前各阶段裁决延后)

---

## 一、已敲定决策

| # | 决策点 | 结论 |
|---|--------|------|
| 1 | 会话重命名交互 | hover 标题旁铅笔图标 → 内联输入框,回车/失焦保存,空值取消 |
| 2 | 会话收藏范围 | 星标标记(落库)+ 侧栏顶部「仅看收藏」筛选开关;不做排序 |
| 3 | 消息反馈 | 👍👎 互斥切换,点同一按钮取消,即时写库 |
| 4 | 八股原文 | 详情页顶部「问答 / 原文」Tab 切换,默认问答 |

**不做(YAGNI)**:反馈训练回路与分析、收藏排序、会话搜索、原文编辑。

## 二、后端设计

### 2.1 迁移

`V6__chat_feedback.sql`:

```sql
ALTER TABLE conversation ADD COLUMN favorite BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE message ADD COLUMN feedback VARCHAR(10);
```

`Conversation` 实体加 `favorite`;`Message` 实体加 `feedback`。

### 2.2 接口

**重命名**:`PUT /api/conversation/{id}` body `{title}`
- 会话不存在 → 400;title 为 null/blank → 400「标题不能为空」;长度 >100 → 400
- 返回更新后的 `ConversationView`

**收藏**:`PUT /api/conversation/{id}/favorite`(无 body)
- 切换 favorite 布尔值;返回 `Result<Boolean>`(新状态)

**消息反馈**:新 `MessageController`,`POST /api/message/{id}/feedback` body `{feedback}`
- feedback ∈ `LIKE`/`DISLIKE`/null(null=取消);其他值 → 400
- 消息不存在 → 400;落库后返回 `Result<Void>`
- `MessageView` 增加 `feedback` 字段(列表查询透出)

### 2.3 后端测试

- `ConversationCrudTest` 扩展:重命名成功返回新标题并落库;空标题 400;超长 400;不存在 400;收藏往返切换
- 新 `MessageFeedbackTest`:LIKE→DISLIKE 切换、再点取消(null)、非法值 400、不存在 400、`GET messages` 透出 feedback

## 三、前端设计

### 3.1 API 与类型

- `api/conversation.ts` 加:`renameConversation(id, title)`、`toggleFavorite(id)`、`setMessageFeedback(id, feedback: 'LIKE' | 'DISLIKE' | null)`
- `types/index.ts`:`ConversationView` 加 `favorite: boolean`;`MessageView` 加 `feedback?: 'LIKE' | 'DISLIKE' | null`

### 3.2 Chat 状态与视图

- `stores/chat.ts`:`ChatUiMessage` 加 `id: number` 与 `feedback?: 'LIKE' | 'DISLIKE' | null`(`openConversation` 映射时带上);新增 `renameConversation(id, title)`(调 API 后更新列表项标题)、`toggleFavorite(id)`(调 API 后更新列表项)、`setFeedback(index, feedback)`(调 API 后更新本地消息)
- `ChatView.vue` 侧栏:
  - 会话标题旁 hover 铅笔(`mdi:pencil-outline`)→ 点击切换为内联 `n-input`(autofocus),回车保存、Esc/失焦空值取消;保存失败 message 提示
  - 星标图标(实心 `mdi:star` = 已收藏 / 空心 `mdi:star-outline`)点击 `toggleFavorite`
  - 顶部「仅看收藏」`n-switch`(或星标切换按钮),`filterFavorites` ref;过滤后空态「没有收藏的会话」
  - 移动端抽屉列表同样支持铅笔/星标
- `ChatMessage.vue`:
  - ASSISTANT 且非 `streaming` 的消息底部加 👍👎 两个小按钮(`mdi:thumb-up-outline`/`thumb-up` 高亮态,点踩同理);点击 `emit('feedback', 'LIKE' | 'DISLIKE' | null)`——再次点击当前高亮按钮传 null 取消
  - `ChatView` 把 emit 接到 `chatStore.setFeedback(index, ...)`;用户消息不显示反馈按钮
- 视觉:按钮小号灰色,hover 高亮,选中态 cyan(赞)/red(踩);亮暗两态适配(沿用映射表)

### 3.3 八股详情

- `BaguwenDetailView.vue` 顶部(标题区之下)加 `n-tabs`:`问答`(默认)/`原文`;`原文` Tab 渲染 `detail.content`(`whitespace-pre-wrap`,与阅读器 text 分支一致),为空时显示「未提取到原文」;「原文链接 ↗」保持不变

### 3.4 前端测试

- `stores/__tests__/chat.test.ts` 扩展:rename 更新列表标题;toggleFavorite 切换并更新;setFeedback 调用 API 并更新本地
- `ChatMessage.test.ts` 扩展:助手非流式渲染 👍👎、流式/用户消息不渲染、点击高亮按钮 emit null(取消)
- 八股 Tab、内联重命名输入由 CDP 验收覆盖

## 四、验收标准

1. **自动化**:后端新增测试绿 + 现有 89 不回归;前端存量 59 + 新增绿;typecheck 无输出;build 成功
2. **CDP 真机**:① 内联重命名(保存/空值取消/刷新持久)② 星标切换 + 筛选开关(过滤、空态)③ 👍👎 互斥/取消/刷新持久 ④ 八股详情 Tab 切换与原文渲染 ⑤ 亮暗两态正常
3. **不做**:反馈训练回路、收藏排序、会话搜索、原文编辑
