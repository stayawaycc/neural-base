<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { NButton, NInput, NModal, NPagination, NPopconfirm, NSelect, NSwitch, type SelectOption } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { useNewsStore } from '@/stores/news'
import { listKbs } from '@/api/kb'
import { message } from '@/utils/feedback'
import type { Kb, NewsItem } from '@/types'

const newsStore = useNewsStore()
const crawling = ref(false)
const favoriteModal = ref(false)
const favoriteNewsId = ref<number | null>(null)
const favoriteKbId = ref<number | null>(null)
const kbs = ref<Kb[]>([])

/* 悬浮预览:延迟 300ms 后 fixed 定位(避免滚动容器裁剪) */
const hoverPreview = ref<{ n: NewsItem; x: number; y: number } | null>(null)
let previewTimer: ReturnType<typeof setTimeout> | null = null
function clearPreviewTimer() {
  if (previewTimer) {
    clearTimeout(previewTimer)
    previewTimer = null
  }
}
function onCardEnter(e: MouseEvent, n: NewsItem) {
  clearPreviewTimer()
  // currentTarget 在事件处理结束后会被重置为 null,鼠标坐标需提前缓存
  const mx = e.clientX
  const my = e.clientY
  const w = 300
  const h = 180
  previewTimer = setTimeout(() => {
    const gap = 14
    let x = mx + gap
    let y = my + gap
    if (x + w > window.innerWidth - 8) x = mx - w - gap
    if (y + h > window.innerHeight - 8) y = my - h - gap
    hoverPreview.value = { n, x: Math.max(8, x), y: Math.max(8, y) }
  }, 300)
  // 滚动时 fixed 浮层会与鼠标错位,监听捕获滚动立即关闭
  window.addEventListener('scroll', onCardLeave, { capture: true, passive: true })
}
function onCardLeave() {
  clearPreviewTimer()
  hoverPreview.value = null
  window.removeEventListener('scroll', onCardLeave, { capture: true })
}

const sortOptions: SelectOption[] = [
  { label: '最新', value: 'created_desc' },
  { label: '最早', value: 'created_asc' },
  { label: '按来源', value: 'source' },
  { label: '按分类', value: 'category' },
]

const BUILTIN_SOURCE_NAMES = ['GitHub Trending', 'Hacker News', 'Papers with Code', '机器之心', 'InfoQ', 'GitHub Star AI']

const sourceModal = ref(false)
const sourceForm = ref({ name: '', url: '', selector: '' })
const savingSource = ref(false)

function fmtTime(iso: string): string {
  const d = new Date(iso)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

async function onCreateSource() {
  if (!sourceForm.value.name.trim() || !sourceForm.value.url.trim()) {
    message.warning('请填写名称与 URL')
    return
  }
  savingSource.value = true
  try {
    await newsStore.createSource({
      name: sourceForm.value.name.trim(),
      url: sourceForm.value.url.trim(),
      selector: sourceForm.value.selector.trim() || undefined,
    })
    sourceForm.value = { name: '', url: '', selector: '' }
    message.success('来源已添加,下次爬取生效')
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    savingSource.value = false
  }
}

/* 分类收敛:仅青/橙/灰,无彩虹 */
const categoryStyles: Record<string, { color: string; bg: string }> = {
  '大模型': { color: 'var(--nb-accent)', bg: 'transparent' },
  '框架': { color: 'var(--nb-accent)', bg: 'transparent' },
  '工具': { color: 'var(--nb-alert)', bg: 'transparent' },
  '论文': { color: 'var(--nb-alert)', bg: 'transparent' },
}

onMounted(async () => {
  try {
    await newsStore.refreshTask()
  } catch {
    /* 任务状态拉取失败不阻塞列表加载 */
  }
  try {
    await newsStore.load()
  } catch {
    /* 列表加载失败不阻塞任务状态展示 */
  }
  try {
    await newsStore.loadSources()
  } catch {
    /* 来源加载失败不阻塞任务状态展示 */
  }
})

onBeforeUnmount(() => newsStore.stopPolling())

async function onCrawl() {
  crawling.value = true
  try {
    await newsStore.triggerCrawl()
    message.success('爬取任务已启动')
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    crawling.value = false
  }
}

function openAndMarkRead(n: { id: number; url?: string }) {
  if (n.url) window.open(n.url, '_blank', 'noopener')
  void newsStore.markRead(n.id, true)
}

async function openFavorite(id: number) {
  favoriteNewsId.value = id
  favoriteKbId.value = null
  favoriteModal.value = true
  if (!kbs.value.length) {
    try {
      kbs.value = await listKbs()
    } catch {
      kbs.value = []
    }
  }
}

async function onFavoriteConfirm() {
  if (!favoriteNewsId.value || !favoriteKbId.value) {
    message.warning('请选择知识库')
    return
  }
  try {
    await newsStore.favorite(favoriteNewsId.value, favoriteKbId.value)
    favoriteModal.value = false
    message.success('已收藏进知识库')
  } catch (e) {
    message.error((e as Error).message)
  }
}

const cleaningNonAi = ref(false)

async function onCleanupNonAi() {
  cleaningNonAi.value = true
  try {
    const deleted = await newsStore.cleanupNonAi()
    message.success(`已清理 ${deleted} 条非 AI 历史数据`)
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    cleaningNonAi.value = false
  }
}
</script>

<template>
  <div class="news-layout">
    <!-- 页面头部 -->
    <div class="news-header">
      <div class="news-header__icon">
        <Icon icon="mdi:newspaper-variant-outline" class="h-5 w-5" />
      </div>
      <div>
        <h1 class="news-header__title">AI 日报 [FEED]</h1>
        <p class="news-header__desc">前沿资讯定时爬取,AI 单篇摘要</p>
      </div>
      <div class="news-header__right">
        <div v-if="newsStore.task?.status === 'RUNNING'" class="news-crawl-progress">
          <div class="news-crawl-progress__bar">
            <div class="news-crawl-progress__fill" :style="{ width: (newsStore.task.progress || 0) + '%' }" />
          </div>
          <span class="news-crawl-progress__text">{{ newsStore.task.progress }}%</span>
        </div>
        <span
          v-if="newsStore.task && newsStore.task.status !== 'RUNNING'"
          class="news-header__badge"
          :class="newsStore.task.status === 'SUCCESS' ? 'news-header__badge--success' : newsStore.task.status === 'FAILED' ? 'news-header__badge--error' : ''"
        >
          {{ newsStore.task.status === 'SUCCESS' ? '已完成' : newsStore.task.status === 'FAILED' ? '失败' : '排队中' }}
        </span>
        <button
          class="news-crawl-btn"
          :disabled="crawling || newsStore.task?.status === 'RUNNING'"
          @click="onCrawl"
        >
          <Icon icon="mdi:cloud-download-outline" class="h-4 w-4" />
          {{ crawling || newsStore.task?.status === 'RUNNING' ? '// CRAWLING...' : '> 立即爬取' }}
        </button>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="news-toolbar">
      <div class="news-toolbar__left">
        <!-- 排序 -->
        <select v-model="newsStore.sort" class="news-select">
          <option v-for="o in sortOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
        <!-- 来源筛选 -->
        <div class="news-chips">
          <button
            class="news-chip"
            :class="newsStore.sourceFilter === null ? 'news-chip--active' : ''"
            @click="newsStore.sourceFilter = null"
          >
            全部来源
          </button>
          <button
            v-for="s in newsStore.sources"
            :key="s.id"
            class="news-chip"
            :class="newsStore.sourceFilter === s.id ? 'news-chip--active' : ''"
            @click="newsStore.sourceFilter = newsStore.sourceFilter === s.id ? null : s.id"
          >
            {{ s.name }}
          </button>
        </div>
        <!-- 分类筛选 -->
        <div class="news-chips">
          <button
            class="news-chip"
            :class="newsStore.filterCategory === '' ? 'news-chip--active' : ''"
            @click="newsStore.filterCategory = ''"
          >
            全部
          </button>
          <button
            v-for="c in newsStore.categories"
            :key="c"
            class="news-chip"
            :class="newsStore.filterCategory === c ? 'news-chip--active' : ''"
            @click="newsStore.filterCategory = newsStore.filterCategory === c ? '' : c"
          >
            {{ c }}
          </button>
        </div>
      </div>
      <div class="news-toolbar__right">
        <button class="news-tool-btn" @click="sourceModal = true">来源管理</button>
        <label class="news-toggle">
          <input type="checkbox" :checked="newsStore.unreadOnly" @change="newsStore.unreadOnly = !newsStore.unreadOnly" />
          <span class="news-toggle__label">仅未读</span>
        </label>
        <button class="news-tool-btn" @click="newsStore.markAllRead()">全部已读</button>
      </div>
    </div>

    <!-- 新闻列表 -->
    <div class="news-list">
      <div
        v-for="n in newsStore.items"
        :key="n.id"
        class="news-card"
        :class="!n.isRead ? 'news-card--unread' : ''"
        @mouseenter="onCardEnter($event, n)"
        @mouseleave="onCardLeave"
      >
        <div class="news-card__main">
          <div class="news-card__top">
            <button class="news-card__title" @click="openAndMarkRead(n)">
              {{ n.title }}
            </button>
            <div class="news-card__actions">
              <span
                v-if="n.category"
                class="news-card__category"
                :style="{
                  color: categoryStyles[n.category]?.color ?? 'var(--nb-text-faint)',
                  background: categoryStyles[n.category]?.bg ?? 'var(--nb-nav-active-bg)',
                }"
              >
                {{ n.category }}
              </span>
              <button class="news-card__action" title="收藏到知识库" @click="openFavorite(n.id)">
                <Icon icon="mdi:bookmark-plus-outline" class="h-4 w-4" />
              </button>
            </div>
          </div>
          <p v-if="n.summary" class="news-card__summary">{{ n.summary }}</p>
          <p v-else class="news-card__summary news-card__summary--empty">暂无摘要</p>
          <div class="news-card__meta">
            <span v-if="n.sourceName" class="news-card__source">
              <span class="news-card__source-dot" />
              {{ n.sourceName }}
            </span>
            <span class="news-card__time">
              <Icon icon="mdi:clock-outline" class="h-3 w-3" />
              {{ fmtTime(n.createdAt) }}
            </span>
            <span v-if="!n.isRead" class="news-card__unread">
              <span class="news-card__unread-dot" />
              未读
            </span>
          </div>
        </div>
      </div>
      <div v-if="!newsStore.items.length" class="news-empty">
        <Icon icon="mdi:newspaper-variant-outline" class="h-10 w-10" style="color: var(--nb-text-faint)" />
        <span class="cy-mono text-[12px]">// NO FEED — 点击「立即爬取」获取资讯</span>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="newsStore.total > newsStore.size" class="news-pagination">
      <n-pagination
        :page="newsStore.page"
        :page-size="newsStore.size"
        :item-count="newsStore.total"
        @update:page="newsStore.page = $event"
      />
    </div>

    <!-- 悬浮预览浮层 -->
    <div
      v-if="hoverPreview"
      class="news-preview"
      :style="{ left: hoverPreview.x + 'px', top: hoverPreview.y + 'px' }"
    >
      <div class="news-preview__title">{{ hoverPreview.n.title }}</div>
      <div class="news-preview__summary">
        {{ hoverPreview.n.summary || '暂无摘要' }}
      </div>
      <div class="news-preview__meta">
        <div class="news-preview__meta-left">
          <span v-if="hoverPreview.n.category" class="news-preview__tag">{{ hoverPreview.n.category }}</span>
          <span v-if="hoverPreview.n.sourceName" class="news-preview__src">{{ hoverPreview.n.sourceName }}</span>
        </div>
        <span class="news-preview__time">{{ fmtTime(hoverPreview.n.createdAt) }}</span>
      </div>
    </div>

    <!-- 收藏弹窗 -->
    <n-modal
      v-model:show="favoriteModal"
      preset="dialog"
      title="收藏到知识库"
      positive-text="收藏"
      negative-text="取消"
      @positive-click="onFavoriteConfirm"
    >
      <div class="py-2">
        <n-select
          v-model:value="favoriteKbId"
          placeholder="选择知识库"
          :options="kbs.map((k) => ({ label: k.name, value: k.id }))"
        />
      </div>
    </n-modal>

    <!-- 来源管理弹窗 -->
    <n-modal
      v-model:show="sourceModal"
      preset="dialog"
      title="来源管理"
      positive-text="关闭"
      @positive-click="sourceModal = false"
    >
      <div class="space-y-3 py-2">
        <div class="space-y-1.5">
          <div
            v-for="s in newsStore.sources"
            :key="s.id"
            class="flex items-center justify-between gap-2 rounded-none border px-3 py-2"
            style="border-color: var(--nb-border)"
          >
            <div class="min-w-0">
              <div class="truncate text-sm" style="color: var(--nb-text-primary)">{{ s.name }}</div>
              <div class="truncate text-xs" style="color: var(--nb-text-faint)">{{ s.url }}</div>
            </div>
            <div class="flex shrink-0 items-center gap-2">
              <n-switch
                size="small"
                :value="s.enabled"
                @update:value="(v: boolean) => newsStore.updateSource(s.id, { enabled: v })"
              />
              <button
                v-if="!BUILTIN_SOURCE_NAMES.includes(s.name)"
                class="text-gray-400 hover:text-[var(--nb-alert)] dark:text-gray-600"
                @click="newsStore.removeSource(s.id)"
              >
                <Icon icon="mdi:trash-can-outline" class="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
        <div class="border-t border-gray-100 pt-3 dark:border-white/5">
          <div class="mb-2 text-xs text-gray-500">
            新增静态来源(仅支持无 JS 渲染的列表页,需填写链接选择器)
          </div>
          <div class="space-y-2">
            <n-input v-model:value="sourceForm.name" size="small" placeholder="名称" />
            <n-input v-model:value="sourceForm.url" size="small" placeholder="URL" />
            <n-input v-model:value="sourceForm.selector" size="small" placeholder="链接选择器,如 ul.news-list a" />
            <n-button size="small" type="primary" :loading="savingSource" @click="onCreateSource">
              添加来源
            </n-button>
          </div>
        </div>
        <div class="border-t border-gray-100 pt-3 dark:border-white/5">
          <n-popconfirm
            @positive-click="onCleanupNonAi"
            :positive-button-props="{ type: 'warning' }"
          >
            <template #trigger>
              <n-button size="small" quaternary :loading="cleaningNonAi">清理非 AI 历史数据</n-button>
            </template>
            将删除 Hacker News / InfoQ 两个源中标题不含 AI 关键词的历史条目,确认继续?
          </n-popconfirm>
        </div>
      </div>
    </n-modal>
  </div>
</template>

<style scoped>
/* ==================== 布局 ==================== */
.news-layout {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ==================== 页面头部 ==================== */
.news-header {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.news-header__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 0;
  background: var(--nb-accent);
  color: #050508;
  clip-path: polygon(8px 0, 100% 0, 100% calc(100% - 8px), calc(100% - 8px) 100%, 0 100%, 0 8px);
  box-shadow: var(--nb-glow-accent);
  flex-shrink: 0;
}
.news-header__title {
  font-family: var(--nb-font-mono);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--nb-text-primary);
}
.news-header__desc {
  font-size: 12px;
  color: var(--nb-text-faint);
  margin-top: 1px;
}
.news-header__right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
}
.news-header__badge {
  padding: 3px 10px;
  border-radius: 0;
  border: 1px solid var(--nb-border);
  font-family: var(--nb-font-mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.06em;
  color: var(--nb-text-secondary);
  background: transparent;
}
.news-header__badge--success {
  color: var(--nb-accent);
  border-color: color-mix(in srgb, var(--nb-accent) 40%, transparent);
  background: var(--nb-accent-soft);
}
.news-header__badge--error {
  color: var(--nb-alert);
  border-color: color-mix(in srgb, var(--nb-alert) 40%, transparent);
  background: var(--nb-alert-soft);
}

/* 爬取进度:分段式(跳格增长,一格一格亮) */
.news-crawl-progress {
  display: flex;
  align-items: center;
  gap: 8px;
}
.news-crawl-progress__bar {
  position: relative;
  width: 200px;
  height: 6px;
  border-radius: 0;
  background: var(--nb-nav-active-bg);
  border: 1px solid var(--nb-border);
  overflow: hidden;
}
.news-crawl-progress__bar::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: repeating-linear-gradient(
    90deg,
    transparent 0 10px,
    var(--nb-bg) 10px 12px
  );
}
.news-crawl-progress__fill {
  height: 100%;
  border-radius: 0;
  background: var(--nb-accent);
  box-shadow: 0 0 8px var(--nb-accent);
}
.news-crawl-progress__text {
  font-family: var(--nb-font-mono);
  font-size: 11px;
  font-weight: 500;
  color: var(--nb-accent);
  min-width: 36px;
}

/* 爬取按钮 */
.news-crawl-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border-radius: 0;
  border: 1px solid var(--nb-accent);
  background: var(--nb-accent);
  color: #050508;
  font-family: var(--nb-font-mono);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.04em;
  clip-path: polygon(7px 0, 100% 0, 100% calc(100% - 7px), calc(100% - 7px) 100%, 0 100%, 0 7px);
  cursor: pointer;
  transition: all 0.2s var(--nb-ease-spring);
}
.news-crawl-btn:hover:not(:disabled) {
  background: var(--nb-accent-hover);
  box-shadow: var(--nb-glow-accent);
  transform: scale(1.02);
}
.news-crawl-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ==================== 工具栏 ==================== */
.news-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.news-toolbar__left {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  flex: 1;
}
.news-toolbar__right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 排序选择 */
.news-select {
  padding: 6px 12px;
  border-radius: 0;
  border: 1px solid var(--nb-border);
  background: var(--nb-card-bg);
  font-family: var(--nb-font-mono);
  font-size: 12px;
  color: var(--nb-text-primary);
  cursor: pointer;
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.news-select:focus {
  border-color: var(--nb-accent);
  box-shadow: 0 0 10px -4px var(--nb-accent);
}

/* Chips:空心等宽 */
.news-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}
.news-chip {
  padding: 5px 12px;
  border-radius: 0;
  border: 1px solid var(--nb-border);
  background: transparent;
  font-family: var(--nb-font-mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--nb-text-secondary);
  cursor: pointer;
  transition: all 0.2s var(--nb-ease-spring);
}
.news-chip:hover {
  border-color: var(--nb-border-strong);
  background: var(--nb-nav-active-bg);
  transform: translateX(2px);
}
.news-chip--active {
  border-color: var(--nb-accent);
  background: var(--nb-accent-soft);
  color: var(--nb-accent);
  box-shadow: 0 0 8px -3px var(--nb-accent);
}

/* 工具按钮 */
.news-tool-btn {
  padding: 5px 12px;
  border-radius: 0;
  border: 1px solid transparent;
  background: transparent;
  font-family: var(--nb-font-mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--nb-text-faint);
  cursor: pointer;
  transition: all 0.15s ease;
}
.news-tool-btn:hover {
  background: var(--nb-nav-active-bg);
  border-color: var(--nb-border);
  color: var(--nb-text-secondary);
}

/* Toggle */
.news-toggle {
  display: flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
}
.news-toggle input {
  accent-color: var(--nb-accent);
}
.news-toggle__label {
  font-size: 12px;
  color: var(--nb-text-faint);
}

/* ==================== 新闻列表 ==================== */
.news-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 新闻卡片 */
.news-card {
  padding: 20px 22px;
  border-radius: 0;
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  box-shadow: var(--nb-card-shadow);
  transition: all 0.25s var(--nb-ease-spring);
}
.news-card:hover {
  border-color: var(--nb-border-strong);
  box-shadow: var(--nb-card-shadow-hover);
  transform: translateX(2px);
}
.news-card--unread {
  border-left: 2px solid var(--nb-accent);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--nb-accent) 20%, transparent), var(--nb-card-shadow);
}
.news-card__main {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.news-card__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}
.news-card__title {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--nb-text-primary);
  line-height: 1.45;
  text-align: left;
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
  transition: color 0.15s ease;
}
.news-card__title:hover {
  color: var(--nb-accent);
}
.news-card__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.news-card__category {
  padding: 3px 10px;
  border-radius: 0;
  border: 1px solid currentColor;
  font-family: var(--nb-font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.06em;
}
.news-card__action {
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
  opacity: 0;
}
.news-card:hover .news-card__action {
  opacity: 1;
}
.news-card__action:hover {
  background: var(--nb-nav-active-bg);
  border-color: var(--nb-border);
  color: var(--nb-accent);
}
.news-card__summary {
  font-size: 13px;
  line-height: 1.6;
  color: var(--nb-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.news-card__summary--empty {
  color: var(--nb-text-faint);
  font-style: italic;
}
.news-card__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--nb-text-faint);
}
.news-card__source {
  display: flex;
  align-items: center;
  gap: 5px;
}
.news-card__source-dot {
  width: 5px;
  height: 5px;
  border-radius: 0;
  background: var(--nb-text-faint);
}
.news-card__time {
  display: flex;
  align-items: center;
  gap: 4px;
}
.news-card__unread {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--nb-accent);
  font-weight: 500;
}
.news-card__unread-dot {
  width: 6px;
  height: 6px;
  border-radius: 0;
  background: var(--nb-accent);
  box-shadow: 0 0 5px var(--nb-accent);
  animation: news-led 1.8s ease-in-out infinite;
}
@keyframes news-led {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}
@media (prefers-reduced-motion: reduce) {
  .news-card__unread-dot { animation: none; }
}

/* ==================== 悬浮预览浮层 ==================== */
.news-preview {
  position: fixed;
  z-index: 1000;
  width: 300px;
  max-width: 320px;
  pointer-events: none;
  padding: 16px 18px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  box-shadow:
    0 10px 25px -5px rgba(0, 0, 0, 0.1),
    0 8px 10px -6px rgba(0, 0, 0, 0.1);
  animation: news-preview-in 0.2s ease-out;
}
.dark .news-preview {
  background: var(--nb-card-bg-solid);
  border-color: var(--nb-border);
  box-shadow:
    0 10px 25px -5px rgba(0, 0, 0, 0.5),
    0 8px 10px -6px rgba(0, 0, 0, 0.5);
}
@keyframes news-preview-in {
  from {
    opacity: 0;
    transform: translateY(5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.news-preview__title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dark .news-preview__title {
  color: var(--nb-text-primary);
}
.news-preview__summary {
  font-size: 13px;
  line-height: 1.5;
  color: #6b7280;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.dark .news-preview__summary {
  color: var(--nb-text-secondary);
}
.news-preview__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 12px;
}
.news-preview__meta-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.news-preview__tag {
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(0, 135, 160, 0.1);
  color: var(--nb-accent);
  font-size: 12px;
  font-weight: 500;
  flex-shrink: 0;
}
.dark .news-preview__tag {
  background: rgba(0, 240, 255, 0.12);
}
.news-preview__src {
  font-size: 12px;
  color: #6b7280;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dark .news-preview__src {
  color: var(--nb-text-secondary);
}
.news-preview__time {
  font-size: 12px;
  color: #9ca3af;
  flex-shrink: 0;
}
.dark .news-preview__time {
  color: var(--nb-text-faint);
}
@media (prefers-reduced-motion: reduce) {
  .news-preview {
    animation: none;
  }
}

/* ==================== 空态 ==================== */
.news-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 56px 0;
  color: var(--nb-text-faint);
}

/* ==================== 分页 ==================== */
.news-pagination {
  display: flex;
  justify-content: center;
  padding-top: 8px;
}
</style>
