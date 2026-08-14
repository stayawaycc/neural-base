<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { NButton, NDrawer, NDrawerContent, NInput, NPopconfirm, NSelect, NSwitch, NTag } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { useChatStore } from '@/stores/chat'
import { listKbs } from '@/api/kb'
import { message } from '@/utils/feedback'
import ChatMessage from '@/components/ChatMessage.vue'
import type { ConversationView, Kb } from '@/types'

const chatStore = useChatStore()
const route = useRoute()
const kbs = ref<Kb[]>([])
const mode = ref<'normal' | 'rag'>('normal')
const kbId = ref<number | null>(null)
const input = ref('')
const drawerOpen = ref(false)
const scrollRef = ref<HTMLElement>()

const modeOptions = [
  { label: '普通模式', value: 'normal' },
  { label: '知识库模式', value: 'rag' },
]

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
  if (!title) {
    renamingId.value = null
    return
  }
  if (title === c.title) {
    renamingId.value = null
    return
  }
  try {
    await chatStore.renameConversation(c.id, title)
    renamingId.value = null
  } catch (e) {
    message.error((e as Error).message) // 保持编辑态,输入不丢
  }
}

onMounted(async () => {
  await chatStore.loadConversations()
  try {
    kbs.value = await listKbs()
  } catch {
    kbs.value = []
  }
  // 支持从首页「最近会话」带 ?conv= 直达
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
  await chatStore.send(question, mode.value === 'rag' && kbId.value ? kbId.value : undefined)
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
</script>

<template>
  <div class="flex h-[calc(100vh-9.5rem)] gap-4">
    <!-- 桌面端会话侧栏 -->
    <aside class="hidden w-64 shrink-0 flex-col md:flex">
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
      <div class="flex-1 space-y-1.5 overflow-y-auto pr-1">
        <button
          v-for="c in visibleConversations"
          :key="c.id"
          class="group glass-card btn-press relative w-full !p-3 text-left transition-colors"
          :class="c.id === chatStore.currentId ? 'glass-card--selected' : 'hover:!border-gray-300 dark:hover:!border-white/10'"
          @click="onOpenConversation(c.id)"
        >
          <span
            v-if="c.id === chatStore.currentId"
            class="absolute left-0 top-1/2 h-5 w-1 -translate-y-1/2 rounded-r-full bg-cyan-500 dark:bg-cyan-400"
          />
          <div class="flex items-center justify-between gap-2">
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
            <span v-else class="truncate text-sm text-gray-800 dark:text-gray-200">{{ c.title }}</span>
            <div class="flex shrink-0 items-center gap-1">
              <!-- 内层用 span + .stop:button 嵌套 button 非法且点击会冒泡误开会话 -->
              <span
                class="btn-press cursor-pointer rounded p-0.5 transition-colors"
                :class="c.favorite ? 'text-amber-400' : 'text-gray-400 hover:text-amber-400 dark:text-gray-600'"
                title="收藏"
                @click.stop="chatStore.toggleFavorite(c.id)"
              >
                <Icon :icon="c.favorite ? 'mdi:star' : 'mdi:star-outline'" class="h-4 w-4" />
              </span>
              <span
                v-if="renamingId !== c.id"
                class="btn-press cursor-pointer rounded p-0.5 text-gray-400 opacity-0 transition-opacity group-hover:opacity-100 hover:text-gray-600 dark:text-gray-600 dark:hover:text-gray-300"
                title="重命名"
                @click.stop="startRename(c)"
              >
                <Icon icon="mdi:pencil-outline" class="h-4 w-4" />
              </span>
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
          <div v-if="c.lastMessageContent" class="mt-1 truncate text-xs text-gray-500">
            {{ c.lastMessageContent }}
          </div>
        </button>
        <div v-if="!visibleConversations.length" class="py-8 text-center text-xs text-gray-400 dark:text-gray-600">
          {{ onlyFavorites ? '没有收藏的会话' : '暂无会话,直接提问即可创建' }}
        </div>
      </div>
    </aside>

    <!-- 移动端会话抽屉 -->
    <n-drawer v-model:show="drawerOpen" placement="left" :width="280">
      <n-drawer-content title="会话">
        <div class="mb-3 flex items-center justify-between">
          <span class="text-xs text-gray-500">仅看收藏</span>
          <n-switch v-model:value="onlyFavorites" size="small" />
        </div>
        <div class="space-y-1.5">
          <button
            v-for="c in visibleConversations"
            :key="c.id"
            class="glass-card btn-press w-full !p-3 text-left"
            :class="c.id === chatStore.currentId ? 'glass-card--selected' : ''"
            @click="onOpenConversation(c.id)"
          >
            <div class="flex items-center justify-between gap-2">
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
              <span v-else class="truncate text-sm text-gray-800 dark:text-gray-200">{{ c.title }}</span>
              <div class="flex shrink-0 items-center gap-1">
                <span
                  class="btn-press cursor-pointer rounded p-0.5 transition-colors"
                  :class="c.favorite ? 'text-amber-400' : 'text-gray-400 hover:text-amber-400 dark:text-gray-600'"
                  title="收藏"
                  @click.stop="chatStore.toggleFavorite(c.id)"
                >
                  <Icon :icon="c.favorite ? 'mdi:star' : 'mdi:star-outline'" class="h-4 w-4" />
                </span>
                <span
                  v-if="renamingId !== c.id"
                  class="btn-press cursor-pointer rounded p-0.5 text-gray-400 transition-colors hover:text-gray-600 dark:text-gray-600 dark:hover:text-gray-300"
                  title="重命名"
                  @click.stop="startRename(c)"
                >
                  <Icon icon="mdi:pencil-outline" class="h-4 w-4" />
                </span>
              </div>
            </div>
          </button>
          <div v-if="!visibleConversations.length" class="py-8 text-center text-xs text-gray-400 dark:text-gray-600">
            {{ onlyFavorites ? '没有收藏的会话' : '暂无会话' }}
          </div>
        </div>
      </n-drawer-content>
    </n-drawer>

    <!-- 消息区 -->
    <div class="flex min-w-0 flex-1 flex-col">
      <div class="mb-3 flex items-center gap-2">
        <n-button class="md:hidden" quaternary size="small" @click="drawerOpen = true">
          <template #icon><Icon icon="mdi:menu" class="h-4 w-4" /></template>
          会话
        </n-button>
        <n-select
          v-model:value="mode"
          :options="modeOptions"
          size="small"
          class="w-32"
        />
        <template v-if="mode === 'rag'">
          <n-select
            v-model:value="kbId"
            size="small"
            class="w-40"
            placeholder="选择知识库"
            :options="kbs.map((k) => ({ label: k.name, value: k.id }))"
          />
          <n-tag v-if="kbId" size="small" :bordered="false" type="info">{{ activeKbName }}</n-tag>
        </template>
      </div>

      <div ref="scrollRef" class="flex-1 space-y-4 overflow-y-auto pb-4 pr-1">
        <div v-if="!chatStore.messages.length" class="flex h-full flex-col items-center justify-center gap-4">
          <span class="nb-brand-badge h-12 w-12">
            <Icon icon="mdi:message-text-outline" class="h-6 w-6 text-white" />
          </span>
          <div class="text-center">
            <p class="text-sm font-medium text-gray-700 dark:text-gray-300">开始提问吧</p>
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">支持普通模式与知识库模式</p>
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
        />
        <div v-if="chatStore.streamError" class="rounded-lg border border-red-300 bg-red-50 px-3 py-2 text-xs text-red-600 dark:border-red-400/30 dark:bg-red-500/10 dark:text-red-300">
          ⚠️ {{ chatStore.streamError }}(已生成内容保留)
        </div>
      </div>

      <!-- 输入区:玻璃容器 + 无边框输入 + 胶囊按钮 -->
      <div class="input-shell mt-4 flex items-end gap-2 p-2.5">
        <n-input
          v-model:value="input"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 6 }"
          placeholder="输入问题,Enter 发送,Shift+Enter 换行"
          :disabled="chatStore.streaming"
          @keydown.enter.exact.prevent="onSend"
        />
        <n-button v-if="chatStore.streaming" round secondary @click="chatStore.stopStream()">
          停止
        </n-button>
        <n-button v-else round type="primary" :disabled="!input.trim()" @click="onSend">
          <template #icon><Icon icon="mdi:send" class="h-4 w-4" /></template>
          发送
        </n-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 输入区玻璃容器:内部 n-input 去边框融入容器 */
.input-shell {
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
  border-radius: 18px;
  box-shadow: var(--nb-card-shadow);
  backdrop-filter: blur(12px);
  transition:
    border-color 0.25s ease,
    box-shadow 0.25s ease;
}
.input-shell:focus-within {
  border-color: var(--nb-accent-cyan);
  box-shadow: var(--nb-card-shadow-hover), 0 0 0 3px color-mix(in srgb, var(--nb-accent-cyan) 12%, transparent);
}
.input-shell :deep(.n-input) {
  background: transparent;
}
.input-shell :deep(.n-input .n-input__border),
.input-shell :deep(.n-input .n-input__state-border) {
  display: none;
}
</style>
