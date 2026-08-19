<script setup lang="ts">
import { Icon } from '@iconify/vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import type { RagSource } from '@/types'

const props = defineProps<{
  role: 'USER' | 'ASSISTANT'
  content: string
  sources?: RagSource[]
  streaming?: boolean
  feedback?: 'LIKE' | 'DISLIKE' | null
}>()

const emit = defineEmits<{ feedback: ['LIKE' | 'DISLIKE' | null]; 'open-source': [RagSource] }>()

function onFeedback(type: 'LIKE' | 'DISLIKE') {
  emit('feedback', props.feedback === type ? null : type)
}
</script>

<template>
  <div class="msg-row" :class="role === 'USER' ? 'msg-row--user' : 'msg-row--ai'">
    <div class="msg-content" :class="role === 'USER' ? 'msg-content--user' : 'msg-content--ai'">
      <!-- 消息正文 -->
      <div class="msg-text">
        <MarkdownRenderer :content="content" />
        <span v-if="streaming" class="nb-cursor" />
      </div>

      <!-- 参考来源(RAG 模式,放在回复后面,仅展示文件名) -->
      <div v-if="sources?.length" class="msg-sources">
        <div class="msg-sources__header">
          <Icon icon="mdi:book-open-variant" class="h-3.5 w-3.5" />
          <span class="cy-mono">// 参考来源</span>
        </div>
        <div
          v-for="(s, i) in sources"
          :key="i"
          class="msg-sources__item"
          :class="s.kbId != null ? 'msg-sources__item--link' : ''"
          :title="`${s.title}${s.kbName ? ' · ' + s.kbName : ''} · 点击查看`"
          @click="emit('open-source', s)"
        >
          <Icon icon="mdi:file-document-outline" class="msg-sources__icon" />
          <span class="msg-sources__title">{{ s.title }}</span>
          <span v-if="s.kbName" class="msg-sources__kb">{{ s.kbName }}</span>
          <Icon v-if="s.kbId != null" icon="mdi:open-in-new" class="msg-sources__open" />
        </div>
      </div>

      <!-- 反馈按钮(AI 消息) -->
      <div v-if="role === 'ASSISTANT' && !streaming" class="msg-actions">
        <button
          class="msg-action-btn"
          :class="feedback === 'LIKE' ? 'msg-action-btn--active' : ''"
          :style="feedback === 'LIKE' ? { color: 'var(--nb-accent)' } : undefined"
          title="赞"
          @click="onFeedback('LIKE')"
        >
          <Icon :icon="feedback === 'LIKE' ? 'mdi:thumb-up' : 'mdi:thumb-up-outline'" class="h-3.5 w-3.5" />
        </button>
        <button
          class="msg-action-btn"
          :class="feedback === 'DISLIKE' ? 'msg-action-btn--dislike' : ''"
          title="踩"
          @click="onFeedback('DISLIKE')"
        >
          <Icon :icon="feedback === 'DISLIKE' ? 'mdi:thumb-down' : 'mdi:thumb-down-outline'" class="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ==================== 左右对话布局:AI 靠左 / 用户靠右 ==================== */
.msg-row {
  display: flex;
  width: 100%;
}
.msg-row--user {
  justify-content: flex-end;
}
.msg-row--ai {
  justify-content: flex-start;
}

/* 消息内容容器 */
.msg-content {
  min-width: 0;
  max-width: 82%;
}

/* ==================== 用户消息(右侧):淡青气泡 ==================== */
.msg-content--user .msg-text {
  padding: 9px 14px;
  border-radius: 14px 14px 6px 14px;
  background: var(--nb-accent-soft);
  color: var(--nb-text-primary);
  font-size: 10px;
  line-height: 1.7;
  word-break: break-word;
}

/* ==================== AI 消息(左侧):去卡片化,文字直排 ==================== */
.msg-content--ai .msg-text {
  font-size: 10px;
  line-height: 1.8;
  color: var(--nb-text-primary);
  word-break: break-word;
}

/* ==================== 参考来源:Notion Callout 风格 ==================== */
.msg-sources {
  margin-top: 10px;
  padding: 9px 12px 10px;
  border-radius: 10px;
  background: var(--nb-nav-active-bg);
  border-left: 3px solid var(--nb-accent);
}
.msg-sources__header {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 6px;
  font-size: 10.5px;
  font-weight: 500;
  color: var(--nb-accent);
  opacity: 0.9;
}
.msg-sources__item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 6px;
  border-radius: 6px;
  margin-bottom: 2px;
  overflow: hidden;
}
.msg-sources__item:last-child {
  margin-bottom: 0;
}
.msg-sources__item--link {
  cursor: pointer;
  transition: background 0.15s ease;
}
.msg-sources__item--link:hover {
  background: var(--nb-accent-soft);
}
.msg-sources__icon {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--nb-text-faint);
}
.msg-sources__title {
  font-size: 11px;
  font-weight: 500;
  color: var(--nb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.msg-sources__kb {
  flex-shrink: 0;
  padding: 1px 6px;
  border: 1px solid var(--nb-border);
  border-radius: 999px;
  font-size: 9.5px;
  color: var(--nb-text-secondary);
}
.msg-sources__open {
  flex-shrink: 0;
  font-size: 10px;
  color: var(--nb-text-faint);
}

/* ==================== 反馈按钮 ==================== */
.msg-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 4px;
}
.msg-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: none;
  background: transparent;
  color: var(--nb-text-faint);
  cursor: pointer;
  transition: all 0.15s ease;
}
.msg-action-btn:hover {
  background: var(--nb-nav-active-bg);
  color: var(--nb-text-secondary);
}
.msg-action-btn--active {
  background: var(--nb-accent-soft);
  color: var(--nb-accent);
}
.msg-action-btn--dislike {
  background: var(--nb-alert-soft);
  color: var(--nb-alert);
}
</style>