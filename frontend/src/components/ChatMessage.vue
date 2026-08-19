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

const emit = defineEmits<{ feedback: ['LIKE' | 'DISLIKE' | null] }>()

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
          :title="s.content"
        >
          <Icon icon="mdi:file-document-outline" class="msg-sources__icon" />
          <span class="msg-sources__title">{{ s.title }}</span>
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
  max-width: 78%;
}

/* ==================== 用户消息(右侧) ==================== */
.msg-content--user .msg-text {
  padding: 12px 16px;
  border-radius: 12px 12px 4px 12px;
  background: var(--nb-accent-soft);
  border: 1px solid color-mix(in srgb, var(--nb-accent) 45%, transparent);
  color: var(--nb-text-primary);
  font-size: 15px;
  line-height: 1.65;
  word-break: break-word;
}

/* ==================== AI 消息(左侧) ==================== */
.msg-content--ai .msg-text {
  padding: 14px 18px;
  border-radius: 12px 12px 12px 4px;
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
  backdrop-filter: blur(16px) saturate(130%);
  -webkit-backdrop-filter: blur(16px) saturate(130%);
  box-shadow: var(--nb-card-shadow);
  font-size: 15px;
  line-height: 1.7;
  color: var(--nb-text-primary);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.msg-content--ai .msg-text:hover {
  border-color: var(--nb-border-strong);
  box-shadow: var(--nb-card-shadow-hover);
}

/* ==================== 参考来源(回复之后,仅文件名) ==================== */
.msg-sources {
  margin-top: 8px;
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
}
.msg-sources__header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--nb-accent);
}
.msg-sources__item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 6px;
  background: var(--nb-nav-active-bg);
  border: 1px solid var(--nb-border);
  margin-bottom: 4px;
  overflow: hidden;
}
.msg-sources__item:last-child {
  margin-bottom: 0;
}
.msg-sources__icon {
  flex-shrink: 0;
  color: var(--nb-text-faint);
}
.msg-sources__title {
  font-size: 13px;
  color: var(--nb-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ==================== 反馈按钮 ==================== */
.msg-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
}
.msg-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--nb-text-faint);
  cursor: pointer;
  transition: all 0.15s ease;
}
.msg-action-btn:hover {
  background: var(--nb-nav-active-bg);
  border-color: var(--nb-border);
  color: var(--nb-text-secondary);
}
.msg-action-btn--active {
  background: var(--nb-accent-soft);
  border-color: var(--nb-border-strong);
  color: var(--nb-accent);
}
.msg-action-btn--dislike {
  background: var(--nb-alert-soft);
  border-color: color-mix(in srgb, var(--nb-alert) 40%, transparent);
  color: var(--nb-alert);
}
</style>