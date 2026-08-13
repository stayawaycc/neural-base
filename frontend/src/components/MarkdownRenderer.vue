<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
import 'highlight.js/styles/github-dark.css'
import { message } from '@/utils/feedback'
import { buildOutline, type OutlineItem } from '@/utils/outline'

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
// 锚点模式:渲染时按顺序取 anchorItems 的 id(buildOutline 只收 h1~h3,这里同样跳过 h4+,
// 否则 h4 会消费后续大纲项的 id 造成错位)
mdAnchor.renderer.rules.heading_open = (tokens, idx) => {
  const level = Number(tokens[idx].tag.slice(1))
  if (level < 1 || level > 3) return `<${tokens[idx].tag}>`
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

<template>
  <div ref="container" class="md-body" v-html="renderHtml()" @click="onClick" />
</template>

<style scoped>
.md-body :deep(p) {
  margin: 0.5em 0;
}
.md-body :deep(pre.code-block) {
  position: relative;
  margin: 0.75em 0;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  overflow: hidden;
}
.md-body :deep(.code-copy-btn) {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 2px 10px;
  font-size: 12px;
  color: #9ca3af;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  cursor: pointer;
  transition: color 0.2s ease;
}
.md-body :deep(.code-copy-btn:hover) {
  color: #f3f4f6;
}
.md-body :deep(code) {
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
}
.md-body :deep(a) {
  color: #22d3ee;
}
.md-body :deep(table) {
  border-collapse: collapse;
  margin: 0.75em 0;
}
.md-body :deep(th),
.md-body :deep(td) {
  border: 1px solid rgba(255, 255, 255, 0.08);
  padding: 6px 12px;
}
</style>
