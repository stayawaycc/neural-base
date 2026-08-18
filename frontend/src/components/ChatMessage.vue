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
    <!-- 问题在上 / 回答在下,均为全宽垂直堆叠 -->
    <div class="msg-content" :class="role === 'USER' ? 'msg-content--user' : 'msg-content--ai'">
      <!-- 参考来源(RAG 模式) -->
      <div v-if="sources?.length" class="msg-sources">
        <div class="msg-sources__header">
          <Icon icon="mdi:book-open-variant" class="h-3.5 w-3.5" />
          <span class="cy-mono">// 参考来源</span>
        </div>
        <div
          v-for="(s, i) in sources"
          :key="i"
          class="msg-sources__item"
        >
          <div class="msg-sources__title">{{ s.title }}</div>
          <div class="msg-sources__excerpt">{{ s.content }}</div>
        </div>
      </div>

      <!-- 消息正文 -->
      <div class="msg-text">
        <MarkdownRenderer :content="content" />
        <span v-if="streaming" class="nb-cursor" />
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
/* ==================== 上下问答布局 ==================== */
.msg-row {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 100%;
  gap: 8px;
}
.msg-row--user {
  align-items: stretch;
}
.msg-row--ai {
  align-items: stretch;
}

/* 消息内容容器 */
.msg-content {
  min-width: 0;
  width: 100%;
}

/* ==================== 问题块(上方):终端回显风格 ==================== */
.msg-content--user .msg-text {
  padding: 14px 18px 14px 20px;
  border-radius: 0;
  background: var(--nb-surface);
  border: 1px solid var(--nb-border);
  border-left: 2px solid var(--nb-accent);
  box-shadow: 0 0 16px -8px var(--nb-accent);
  color: var(--nb-text-primary);
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
}
.msg-content--user .msg-text::before {
  content: '[USER] >';
  display: block;
  font-family: var(--nb-font-mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.12em;
  color: var(--nb-accent);
  margin-bottom: 8px;
  opacity: 0.85;
}

/* ==================== 回答块(下方):HUD 面板 ==================== */
.msg-content--ai {
  padding-left: 4px;
}
.msg-content--ai .msg-text {
  padding: 16px 20px;
  border-radius: 0;
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
  border-left: 2px solid var(--nb-accent);
  backdrop-filter: blur(16px) saturate(130%);
  -webkit-backdrop-filter: blur(16px) saturate(130%);
  box-shadow: var(--nb-card-shadow);
  font-size: 15px;
  line-height: 1.65;
  color: var(--nb-text-primary);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.msg-content--ai .msg-text:hover {
  border-color: var(--nb-border-strong);
  box-shadow: var(--nb-card-shadow-hover);
}
.msg-content--ai .msg-text::before {
  content: '// AI_RESPONSE';
  display: block;
  font-family: var(--nb-font-mono);
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--nb-text-faint);
  margin-bottom: 8px;
}

/* ==================== 参考来源 ==================== */
.msg-sources {
  margin-bottom: 10px;
  padding: 12px 16px;
  border-radius: 0;
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
  border-left: 2px solid var(--nb-accent);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}
.msg-sources__header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 500;
  color: var(--nb-accent);
}
.msg-sources__item {
  padding: 8px 10px;
  border-radius: 0;
  background: var(--nb-nav-active-bg);
  border: 1px solid var(--nb-border);
  margin-bottom: 6px;
}
.msg-sources__item:last-child {
  margin-bottom: 0;
}
.msg-sources__title {
  font-size: 12px;
  font-weight: 500;
  color: var(--nb-text-primary);
}
.msg-sources__excerpt {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--nb-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ==================== 反馈按钮 ==================== */
.msg-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  padding-left: 20px;
}
.msg-action-btn {
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
