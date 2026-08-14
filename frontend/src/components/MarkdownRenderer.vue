<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
import lightHljsCss from 'highlight.js/styles/github.css?inline'
import darkHljsCss from 'highlight.js/styles/github-dark.css?inline'
import { message } from '@/utils/feedback'
import { buildOutline, type OutlineItem } from '@/utils/outline'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
let hljsStyleEl: HTMLStyleElement | null = null

// 代码高亮样式:两套 css 文本随主题切换注入/移除单个 <style>(动态 import 有模块缓存,
// 二次 import 不会重注入,多轮切换会滞留旧主题,故用 ?inline 取文本手动管理)
function applyHighlightTheme() {
  const css = themeStore.isDark ? darkHljsCss : lightHljsCss
  if (hljsStyleEl?.textContent === css) return
  hljsStyleEl?.remove()
  hljsStyleEl = document.createElement('style')
  hljsStyleEl.textContent = css
  document.head.appendChild(hljsStyleEl)
}

applyHighlightTheme()
watch(() => themeStore.isDark, applyHighlightTheme)

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
/* AI 回答排版:全部走 CSS 变量双主题化(旧版深色专用样式在浅色下隐形,已修复) */
.md-body {
  font-size: 15px;
  line-height: 1.75;
  color: var(--nb-text-primary);
}
.md-body :deep(p) {
  margin: 0.6em 0;
}
/* 标题层级 */
.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3),
.md-body :deep(h4) {
  font-weight: 600;
  letter-spacing: -0.01em;
  line-height: 1.4;
  margin: 1.4em 0 0.5em;
}
.md-body :deep(h1) {
  font-size: 1.35em;
}
.md-body :deep(h2) {
  font-size: 1.2em;
  padding-bottom: 0.35em;
  border-bottom: 1px solid var(--nb-border);
}
.md-body :deep(h3) {
  font-size: 1.08em;
}
.md-body :deep(h4) {
  font-size: 1em;
  color: var(--nb-text-secondary);
}
/* 列表 */
.md-body :deep(ul),
.md-body :deep(ol) {
  margin: 0.5em 0;
  padding-left: 1.5em;
}
.md-body :deep(li) {
  margin: 0.3em 0;
}
.md-body :deep(li::marker) {
  color: var(--nb-text-faint);
}
/* 引用块:青色光条 + 淡青底(签名元素) */
.md-body :deep(blockquote) {
  margin: 0.9em 0;
  padding: 0.6em 1em;
  border-left: 3px solid var(--nb-accent-cyan);
  border-radius: 0 10px 10px 0;
  background: color-mix(in srgb, var(--nb-accent-cyan) 6%, transparent);
  color: var(--nb-text-secondary);
}
.md-body :deep(blockquote p) {
  margin: 0.3em 0;
}
/* 行内代码 */
.md-body :deep(code) {
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  font-size: 0.88em;
  background: var(--nb-nav-active-bg);
  border: 1px solid var(--nb-border);
  border-radius: 5px;
  padding: 0.15em 0.4em;
}
/* 代码块(背景由 hljs 主题 css 提供,这里补边框/圆角/复制按钮) */
.md-body :deep(pre.code-block) {
  position: relative;
  margin: 0.9em 0;
  border-radius: 12px;
  border: 1px solid var(--nb-border);
  overflow: hidden;
}
.md-body :deep(pre.code-block code) {
  background: transparent;
  border: none;
  padding: 0;
  font-size: 0.9em;
}
.md-body :deep(.code-copy-btn) {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 2px 10px;
  font-size: 12px;
  color: var(--nb-text-faint);
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
  border-radius: 6px;
  cursor: pointer;
  transition: color 0.2s ease;
}
.md-body :deep(.code-copy-btn:hover) {
  color: var(--nb-text-primary);
}
/* 链接:下划线用淡化青色,悬停加深 */
.md-body :deep(a) {
  color: var(--nb-accent-cyan);
  text-decoration: none;
  border-bottom: 1px solid color-mix(in srgb, var(--nb-accent-cyan) 40%, transparent);
  transition: border-color 0.2s ease;
}
.md-body :deep(a:hover) {
  border-color: var(--nb-accent-cyan);
}
/* 分隔线 */
.md-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--nb-border);
  margin: 1.4em 0;
}
/* 表格:极淡分隔线 + 悬停行高亮(隐形结构) */
.md-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 0.9em 0;
  font-size: 0.9em;
}
.md-body :deep(th) {
  text-align: left;
  font-weight: 600;
  color: var(--nb-text-primary);
  border-bottom: 1px solid var(--nb-border);
  padding: 8px 12px;
}
.md-body :deep(td) {
  border-bottom: 1px solid var(--nb-border);
  padding: 8px 12px;
}
.md-body :deep(tr:hover) {
  background: var(--nb-nav-active-bg);
}
.md-body :deep(tr:last-child td) {
  border-bottom: none;
}
</style>
