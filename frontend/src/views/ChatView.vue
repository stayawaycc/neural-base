<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NDrawer, NDrawerContent, NPopconfirm } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { useChatStore } from '@/stores/chat'
import { listKbs } from '@/api/kb'
import { message } from '@/utils/feedback'
import ChatMessage from '@/components/ChatMessage.vue'
import type { ConversationView, Kb, RagSource } from '@/types'

const chatStore = useChatStore()
const route = useRoute()
const router = useRouter()
const kbs = ref<Kb[]>([])
const mode = ref<'normal' | 'rag'>('normal')
const kbId = ref<number | null>(null)
const input = ref('')
const drawerOpen = ref(false)
const scrollRef = ref<HTMLElement>()

const activeKbName = computed(
  () => kbs.value.find((k) => k.id === kbId.value)?.name ?? '未选择',
)

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
  if (!title || title === c.title) {
    renamingId.value = null
    return
  }
  try {
    await chatStore.renameConversation(c.id, title)
    renamingId.value = null
  } catch (e) {
    message.error((e as Error).message)
  }
}

onMounted(async () => {
  await chatStore.loadConversations()
  try {
    kbs.value = await listKbs()
  } catch {
    kbs.value = []
  }
  const conv = Number(route.query.conv)
  if (conv && chatStore.conversations.some((c) => c.id === conv)) {
    await chatStore.openConversation(conv)
    void nextTick(scrollToBottom)
  }
})

watch(
  () => [chatStore.messages.length, chatStore.messages.at(-1)?.content],
  () => void nextTick(scrollToBottom),
)

function scrollToBottom() {
  scrollRef.value?.scrollTo({ top: scrollRef.value.scrollHeight })
}

async function onSend() {
  const question = input.value.trim()
  if (!question || chatStore.streaming) return
  input.value = ''
  const ragMode = mode.value === 'rag'
  await chatStore.send(
    question,
    ragMode ? (kbId.value ?? undefined) : undefined,
    ragMode,
  )
  scrollToBottom()
}

async function onNewConversation() {
  chatStore.newConversation()
  drawerOpen.value = false
}

async function onOpenConversation(id: number) {
  await chatStore.openConversation(id)
  drawerOpen.value = false
  scrollToBottom()
}

function fmtTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function onOpenSource(s: RagSource) {
  if (s.kbId == null) return
  void router.push({ name: 'kb-reader', params: { kbId: String(s.kbId), docId: String(s.docId) } })
}
</script>

<template>
  <div class="chat-layout">
    <!-- 桌面端会话侧栏 -->
    <aside class="chat-sidebar">
      <div class="chat-sidebar__header">
        <span class="chat-sidebar__title cy-mono">// 会话</span>
        <div class="chat-sidebar__actions">
          <button class="chat-sidebar__filter" :class="{ 'chat-sidebar__filter--active': onlyFavorites }" @click="onlyFavorites = !onlyFavorites">
            <Icon :icon="onlyFavorites ? 'mdi:star' : 'mdi:star-outline'" class="h-3.5 w-3.5" />
          </button>
          <button class="chat-new-btn" @click="onNewConversation">
            <Icon icon="mdi:plus" class="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
      <div class="chat-sidebar__list">
        <button
          v-for="c in visibleConversations"
          :key="c.id"
          class="conv-item group"
          :class="c.id === chatStore.currentId ? 'conv-item--active' : ''"
          @click="onOpenConversation(c.id)"
        >
          <span v-if="c.id === chatStore.currentId" class="conv-item__bar" />
          <div class="conv-item__body">
            <div class="conv-item__top">
              <n-input
                v-if="renamingId === c.id"
                v-model:value="renameText"
                size="tiny"
                autofocus
                class="min-w-0 flex-1"
                @keyup.enter="commitRename(c)"
                @keyup.esc="renamingId = null"
                @blur="commitRename(c)"
              />
              <span v-else class="conv-item__title">{{ c.title }}</span>
              <span class="conv-item__time">{{ fmtTime(c.createdAt) }}</span>
            </div>
            <div v-if="c.lastMessageContent" class="conv-item__preview">
              {{ c.lastMessageContent }}
            </div>
          </div>
          <div class="conv-item__actions">
            <span
              class="conv-item__action"
              :class="c.favorite ? 'conv-item__action--star' : ''"
              title="收藏"
              @click.stop="chatStore.toggleFavorite(c.id)"
            >
              <Icon :icon="c.favorite ? 'mdi:star' : 'mdi:star-outline'" class="h-3.5 w-3.5" />
            </span>
            <span
              v-if="renamingId !== c.id"
              class="conv-item__action conv-item__action--edit"
              title="重命名"
              @click.stop="startRename(c)"
            >
              <Icon icon="mdi:pencil-outline" class="h-3.5 w-3.5" />
            </span>
            <n-popconfirm @positive-click="chatStore.removeConversation(c.id)">
              <template #trigger>
                <span class="conv-item__action conv-item__action--delete">
                  <Icon icon="mdi:trash-can-outline" class="h-3.5 w-3.5" />
                </span>
              </template>
              删除该会话?
            </n-popconfirm>
          </div>
        </button>
        <div v-if="!visibleConversations.length" class="chat-sidebar__empty">
          <Icon icon="mdi:message-text-outline" class="h-8 w-8 text-gray-300 dark:text-gray-600" />
          <span class="cy-mono">{{ onlyFavorites ? '// NO FAVORITES' : '// TYPE TO START' }}</span>
        </div>
      </div>
    </aside>

    <!-- 移动端会话抽屉 -->
    <n-drawer v-model:show="drawerOpen" placement="left" :width="280">
      <n-drawer-content title="会话">
        <div class="space-y-1.5">
          <button
            v-for="c in visibleConversations"
            :key="c.id"
            class="conv-item w-full"
            :class="c.id === chatStore.currentId ? 'conv-item--active' : ''"
            @click="onOpenConversation(c.id)"
          >
            <div class="conv-item__body">
              <span class="conv-item__title">{{ c.title }}</span>
              <div v-if="c.lastMessageContent" class="conv-item__preview">{{ c.lastMessageContent }}</div>
            </div>
          </button>
          <div v-if="!visibleConversations.length" class="py-8 text-center text-xs text-gray-400">
            {{ onlyFavorites ? '没有收藏的会话' : '暂无会话' }}
          </div>
        </div>
      </n-drawer-content>
    </n-drawer>

    <!-- 消息区 -->
    <div class="chat-main">
      <!-- 工具栏 -->
      <div class="chat-toolbar">
        <button class="chat-toolbar__menu md:hidden" @click="drawerOpen = true">
          <Icon icon="mdi:menu" class="h-4 w-4" />
        </button>
        <div class="chat-mode">
          <button
            class="chat-mode__btn"
            :class="mode === 'normal' ? 'chat-mode__btn--active' : ''"
            @click="mode = 'normal'"
          >
            普通
          </button>
          <button
            class="chat-mode__btn"
            :class="mode === 'rag' ? 'chat-mode__btn--active' : ''"
            @click="mode = 'rag'"
          >
            知识库
          </button>
        </div>
        <template v-if="mode === 'rag'">
          <select v-model="kbId" class="chat-kb-select">
            <option :value="null">全部知识库</option>
            <option v-for="k in kbs" :key="k.id" :value="k.id">{{ k.name }}</option>
          </select>
          <span v-if="kbId" class="chat-kb-tag">{{ activeKbName }}</span>
        </template>
      </div>

      <!-- 消息列表 -->
      <div ref="scrollRef" class="chat-messages">
        <div v-if="!chatStore.messages.length" class="chat-empty">
          <div class="chat-empty__icon">
            <Icon icon="mdi:message-text-outline" class="h-7 w-7" />
          </div>
          <div>
            <p class="chat-empty__title cy-mono">&gt; 开始提问吧</p>
            <p class="chat-empty__desc">普通模式 / 知识库 RAG 模式</p>
          </div>
        </div>
        <ChatMessage
          v-for="(m, i) in chatStore.messages"
          :key="i"
          :role="m.role"
          :content="m.content"
          :sources="m.sources"
          :streaming="m.streaming"
          :feedback="m.feedback"
          @feedback="(fb) => chatStore.setFeedback(i, fb)"
          @open-source="onOpenSource"
        />
        <div v-if="chatStore.streamError" class="chat-error">
          <Icon icon="mdi:alert-circle-outline" class="h-4 w-4 shrink-0" />
          {{ chatStore.streamError }}(已生成内容保留)
        </div>
      </div>

      <!-- 输入区 -->
      <div class="chat-input-shell">
        <div class="chat-input">
          <textarea
            v-model="input"
            class="chat-input__field"
            placeholder="输入问题,Enter 发送,Shift+Enter 换行"
            :disabled="chatStore.streaming"
            rows="1"
            @keydown.enter.exact.prevent="onSend"
            @input="(e: Event) => { const t = e.target as HTMLTextAreaElement; t.style.height = 'auto'; t.style.height = Math.min(t.scrollHeight, 150) + 'px' }"
          />
          <button
            v-if="chatStore.streaming"
            class="chat-input__stop"
            @click="chatStore.stopStream()"
          >
            <Icon icon="mdi:stop" class="h-4 w-4" />
            停止
          </button>
          <button
            v-else
            class="chat-input__send"
            :disabled="!input.trim()"
            @click="onSend"
          >
            <Icon icon="mdi:arrow-up" class="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ==================== 布局 ==================== */
.chat-layout {
  display: flex;
  height: calc(100vh - 9.5rem);
  gap: 16px;
}

/* ==================== 侧栏 ==================== */
.chat-sidebar {
  display: none;
  flex-direction: column;
  width: 260px;
  flex-shrink: 0;
}
@media (min-width: 768px) {
  .chat-sidebar {
    display: flex;
  }
}
.chat-sidebar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  padding: 0 4px;
}
.chat-sidebar__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--nb-text-secondary);
}
.chat-sidebar__actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
.chat-sidebar__filter {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 0;
  border: 1px solid transparent;
  background: transparent;
  color: var(--nb-text-faint);
  cursor: pointer;
  transition: all 0.15s ease;
}
.chat-sidebar__filter:hover {
  background: var(--nb-nav-active-bg);
  color: var(--nb-text-secondary);
}
.chat-sidebar__filter--active {
  background: var(--nb-alert-soft);
  border-color: color-mix(in srgb, var(--nb-alert) 40%, transparent);
  color: var(--nb-alert);
}
.chat-new-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 0;
  border: 1px solid color-mix(in srgb, var(--nb-accent) 50%, transparent);
  background: transparent;
  color: var(--nb-accent);
  cursor: pointer;
  clip-path: polygon(6px 0, 100% 0, 100% calc(100% - 6px), calc(100% - 6px) 100%, 0 100%, 0 6px);
  transition: all 0.2s var(--nb-ease-spring);
}
.chat-new-btn:hover {
  background: var(--nb-accent);
  color: #050508;
  box-shadow: var(--nb-glow-accent);
}
.chat-sidebar__list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.chat-sidebar__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 0;
  font-size: 11px;
  color: var(--nb-text-faint);
}

/* ==================== 会话项 ==================== */
.conv-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 9px 12px;
  border-radius: 10px;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease;
}
.conv-item:hover {
  background: var(--nb-nav-active-bg);
}
.conv-item--active {
  background: var(--nb-accent-soft);
}
.conv-item--active .conv-item__title {
  font-weight: 600;
  color: var(--nb-text-primary);
}
.conv-item__bar {
  position: absolute;
  left: 0;
  top: 50%;
  height: 18px;
  width: 3px;
  border-radius: 2px;
  background: var(--nb-accent);
  box-shadow: 0 0 6px var(--nb-accent);
  transform: translateY(-50%);
}
.conv-item__body {
  flex: 1;
  min-width: 0;
}
.conv-item__top {
  display: flex;
  align-items: center;
  gap: 6px;
}
.conv-item__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 500;
  color: var(--nb-text-primary);
}
.conv-item__time {
  font-size: 10px;
  color: var(--nb-text-faint);
  flex-shrink: 0;
  align-self: flex-start;
  margin-top: 2px;
}
.conv-item__preview {
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--nb-text-faint);
  line-height: 1.4;
}
.conv-item__actions {
  display: flex;
  align-items: center;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s ease;
  flex-shrink: 0;
}
.conv-item:hover .conv-item__actions {
  opacity: 1;
}
.conv-item__action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  color: var(--nb-text-faint);
  cursor: pointer;
  transition: all 0.15s ease;
}
.conv-item__action:hover {
  background: var(--nb-nav-active-bg);
  color: var(--nb-text-secondary);
}
.conv-item__action--star {
  color: var(--nb-alert);
  opacity: 1;
}
.conv-item__action--edit {
  display: none;
}
.conv-item:hover .conv-item__action--edit {
  display: flex;
}
.conv-item__action--delete:hover {
  color: var(--nb-alert);
  background: var(--nb-alert-soft);
}

/* ==================== 消息主区 ==================== */
.chat-main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

/* 工具栏 */
.chat-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.chat-toolbar__menu {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  border: 1px solid var(--nb-border);
  background: var(--nb-card-bg);
  color: var(--nb-text-secondary);
  cursor: pointer;
  transition: all 0.15s ease;
}
.chat-toolbar__menu:hover {
  background: var(--nb-nav-active-bg);
}

/* 模式切换分段控制器(iOS Segmented Control) */
.chat-mode {
  display: flex;
  padding: 3px;
  border-radius: 12px;
  background: var(--nb-nav-active-bg);
  gap: 2px;
}
.chat-mode__btn {
  padding: 5px 16px;
  border-radius: 9px;
  border: none;
  background: transparent;
  font-size: 12px;
  font-weight: 500;
  color: var(--nb-text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
}
.chat-mode__btn--active {
  background: var(--nb-card-bg-solid);
  color: var(--nb-accent);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  font-weight: 600;
}

/* 知识库选择 */
.chat-kb-select {
  appearance: none;
  -webkit-appearance: none;
  padding: 6px 30px 6px 12px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background-color: var(--nb-card-bg-solid);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%236b7280' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 10px center;
  font-family: var(--nb-font-mono);
  font-size: 12px;
  color: var(--nb-text-primary);
  cursor: pointer;
  outline: none;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}
.dark .chat-kb-select {
  border-color: rgba(255, 255, 255, 0.16);
}
.chat-kb-select:hover {
  border-color: var(--nb-accent);
  background-color: var(--nb-accent-soft);
}
.chat-kb-select:focus {
  border-color: var(--nb-accent);
  box-shadow: 0 0 0 3px var(--nb-accent-soft);
}
.chat-kb-tag {
  padding: 3px 10px;
  border-radius: 0;
  border: 1px solid color-mix(in srgb, var(--nb-accent) 50%, transparent);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--nb-accent);
  background: var(--nb-accent-soft);
}

/* ==================== 消息列表 ==================== */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 16px;
  padding-right: 4px;
}

/* 空态 */
.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  flex: 1;
  justify-content: center;
}
.chat-empty__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 0;
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
  box-shadow: var(--nb-card-shadow);
  clip-path: polygon(12px 0, 100% 0, 100% calc(100% - 12px), calc(100% - 12px) 100%, 0 100%, 0 12px);
}
.chat-empty__icon :deep(svg) {
  color: var(--nb-text-faint);
}
.chat-empty__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--nb-text-primary);
  text-align: center;
}
.chat-empty__desc {
  font-size: 13px;
  color: var(--nb-text-faint);
  text-align: center;
}

/* 错误提示 */
.chat-error {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  border-radius: 0;
  font-size: 13px;
  color: var(--nb-alert);
  background: var(--nb-alert-soft);
  border: 1px solid color-mix(in srgb, var(--nb-alert) 30%, transparent);
}

/* ==================== 输入区:悬浮胶囊 ==================== */
.chat-input-shell {
  margin-top: auto;
  padding: 12px 0 20px;
}
.chat-input {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 10px 10px 10px 18px;
  border-radius: 24px;
  background: var(--nb-card-bg-solid);
  border: 1px solid var(--nb-border);
  box-shadow: 0 4px 24px -6px rgba(0, 0, 0, 0.12);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.chat-input:focus-within {
  border-color: color-mix(in srgb, var(--nb-accent) 55%, var(--nb-border));
  box-shadow: 0 0 0 3px var(--nb-accent-soft), 0 4px 24px -6px rgba(0, 0, 0, 0.12);
}
.chat-input__field {
  flex: 1;
  min-height: 22px;
  max-height: 150px;
  resize: none;
  border: none;
  outline: none;
  background: transparent;
  font-size: 13px;
  line-height: 1.6;
  color: var(--nb-text-primary);
  font-family: inherit;
}
.chat-input__field::placeholder {
  color: var(--nb-text-faint);
}
.chat-input__field:disabled {
  opacity: 0.6;
}
/* 隐藏 textarea 滚动条 */
.chat-input__field::-webkit-scrollbar {
  display: none;
}
.chat-input__send {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: var(--nb-accent);
  color: #050508;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s ease;
}
.chat-input__send:hover:not(:disabled) {
  background: var(--nb-accent-hover);
  box-shadow: 0 0 14px color-mix(in srgb, var(--nb-accent) 50%, transparent);
  transform: scale(1.06);
}
.chat-input__send:active:not(:disabled) {
  transform: scale(0.96);
}
.chat-input__send:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.chat-input__stop {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 7px 16px;
  border-radius: 999px;
  border: 1px solid var(--nb-border);
  background: var(--nb-card-bg);
  font-size: 12px;
  color: var(--nb-text-secondary);
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s ease;
}
.chat-input__stop:hover {
  background: var(--nb-alert-soft);
  border-color: color-mix(in srgb, var(--nb-alert) 40%, transparent);
  color: var(--nb-alert);
}
</style>
