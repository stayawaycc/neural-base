<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NInput, NModal, NPopconfirm, NPagination, NSelect, NSkeleton, NTag } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { useBaguwenStore } from '@/stores/baguwen'
import { listKbs } from '@/api/kb'
import { message } from '@/utils/feedback'
import type { ImportTaskStatus, Kb } from '@/types'

const baguwenStore = useBaguwenStore()
const router = useRouter()

const urlText = ref('')
const importing = ref(false)
const searchKeyword = ref('')
const listLoading = ref(true)

const favoriteModal = ref(false)
const favoriteArticleId = ref<number | null>(null)
const favoriteKbId = ref<number | null>(null)
const kbs = ref<Kb[]>([])
const favoriting = ref(false)

/* 状态收敛:处理中/成功 → 电光蓝,失败 → 警告橙 */
const taskStatusMeta: Record<ImportTaskStatus, { color: string; bg: string; label: string }> = {
  PENDING: { color: 'var(--nb-text-faint)', bg: 'var(--nb-nav-active-bg)', label: '排队中' },
  PROCESSING: { color: 'var(--nb-accent)', bg: 'var(--nb-accent-soft)', label: '爬取中' },
  SUCCESS: { color: 'var(--nb-accent)', bg: 'var(--nb-accent-soft)', label: '成功' },
  FAILED: { color: 'var(--nb-alert)', bg: 'var(--nb-alert-soft)', label: '失败' },
}

onMounted(async () => {
  try {
    await baguwenStore.loadArticles()
    await baguwenStore.loadTags()
    await baguwenStore.refreshTasks()
  } finally {
    listLoading.value = false
  }
})

onBeforeUnmount(() => baguwenStore.stopPolling())

async function onImport() {
  const urls = urlText.value
    .split('\n')
    .map((u) => u.trim())
    .filter((u) => u && /^https?:\/\//.test(u))
  if (!urls.length) {
    message.warning('请输入至少一个 http(s) 链接,每行一个')
    return
  }
  importing.value = true
  try {
    await baguwenStore.importUrls(urls)
    urlText.value = ''
    message.success(`已提交 ${urls.length} 个导入任务`)
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    importing.value = false
  }
}

function onSearch() {
  baguwenStore.filter.keyword = searchKeyword.value.trim()
  baguwenStore.filter.page = 1
  void baguwenStore.loadArticles()
}

function onTagFilter(tag: string) {
  baguwenStore.filter.tag = baguwenStore.filter.tag === tag ? '' : tag
  baguwenStore.filter.page = 1
  void baguwenStore.loadArticles()
}

function onPageChange(page: number) {
  baguwenStore.filter.page = page
  void baguwenStore.loadArticles()
}

function onTaskPageChange(page: number) {
  baguwenStore.taskFilter.page = page
  void baguwenStore.loadTasksPage()
}

const clearingFailed = ref(false)

async function onClearFailed() {
  clearingFailed.value = true
  try {
    const deleted = await baguwenStore.clearFailedTasks()
    message.success(deleted > 0 ? `已清理 ${deleted} 个失败任务` : '没有失败任务')
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    clearingFailed.value = false
  }
}

async function openFavorite(id: number) {
  favoriteArticleId.value = id
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
  if (!favoriteArticleId.value || !favoriteKbId.value) {
    message.warning('请选择知识库')
    return
  }
  favoriting.value = true
  try {
    const result = await baguwenStore.favorite(favoriteArticleId.value, favoriteKbId.value)
    favoriteModal.value = false
    message.success(`已收藏,写入 ${result.embedded} 个向量片段`)
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    favoriting.value = false
  }
}
</script>

<template>
  <div class="bw-layout">
    <!-- 页面头部 -->
    <div class="bw-header">
      <div class="bw-header__icon">
        <Icon icon="mdi:file-document-multiple-outline" class="h-5 w-5" />
      </div>
      <div>
        <h1 class="bw-header__title">八股文库 [EXAM]</h1>
        <p class="bw-header__desc">URL 导入,AI 自动提取问答对</p>
      </div>
      <span class="bw-header__count">TOTAL // {{ baguwenStore.total }}</span>
    </div>

    <!-- 导入区 -->
    <div class="bw-import">
      <div class="bw-import__header">
        <div class="bw-import__icon">
          <Icon icon="mdi:link-variant" class="h-4 w-4" />
        </div>
        <div>
          <div class="bw-import__label">导入文章</div>
          <div class="bw-import__hint">每行一个 URL,后台异步爬取 + AI 结构化</div>
        </div>
      </div>
      <textarea
        v-model="urlText"
        class="bw-import__textarea"
        placeholder="https://example.com/article&#10;https://example.com/another"
        rows="3"
      />
      <div class="bw-import__footer">
        <button class="bw-import__btn" :disabled="importing || !urlText.trim()" @click="onImport">
          <Icon icon="mdi:download-box-outline" class="h-4 w-4" />
          {{ importing ? '// IMPORTING...' : '> 开始导入' }}
        </button>
      </div>
    </div>

    <!-- 任务进度 -->
    <div v-if="baguwenStore.tasks.length" class="bw-tasks">
      <div class="bw-tasks__header">
        <div class="bw-tasks__icon">
          <Icon icon="mdi:progress-clock" class="h-3.5 w-3.5" />
        </div>
        <span class="bw-tasks__title">导入任务</span>
        <span v-if="baguwenStore.pendingCount" class="bw-tasks__status">
          处理中 {{ baguwenStore.pendingCount }} 个
        </span>
        <span v-else class="bw-tasks__status bw-tasks__status--done">全部完成</span>
        <div class="ml-auto">
          <n-popconfirm @positive-click="onClearFailed">
            <template #trigger>
              <button class="bw-tasks__clear" :disabled="clearingFailed">清理失败</button>
            </template>
            删除全部失败任务(不影响已导入的文章)?
          </n-popconfirm>
        </div>
      </div>
      <div class="bw-tasks__list">
        <div
          v-for="t in baguwenStore.tasks"
          :key="t.id"
          class="bw-task"
        >
          <span class="bw-task__dot" :class="`bw-task__dot--${t.status.toLowerCase()}`" />
          <div class="bw-task__body">
            <div class="bw-task__url">{{ t.url }}</div>
            <div v-if="t.status === 'FAILED' && t.errorMsg" class="bw-task__error">
              {{ t.errorMsg }}
            </div>
          </div>
          <div class="bw-task__meta">
            <span class="bw-task__badge" :style="{ color: taskStatusMeta[t.status].color, background: taskStatusMeta[t.status].bg }">
              {{ taskStatusMeta[t.status].label }}
            </span>
            <button v-if="t.status === 'FAILED'" class="bw-task__retry" @click="baguwenStore.retry(t.id)">重试</button>
            <n-popconfirm
              v-if="t.status === 'FAILED' || t.status === 'SUCCESS'"
              @positive-click="baguwenStore.removeTask(t.id)"
            >
              <template #trigger>
                <span class="bw-task__delete" title="删除任务">
                  <Icon icon="mdi:close" class="h-3.5 w-3.5" />
                </span>
              </template>
              删除该导入任务?
            </n-popconfirm>
          </div>
        </div>
      </div>
      <div v-if="baguwenStore.taskTotal > baguwenStore.taskFilter.size" class="bw-tasks__pagination">
        <n-pagination
          :page="baguwenStore.taskFilter.page"
          :page-size="baguwenStore.taskFilter.size"
          :item-count="baguwenStore.taskTotal"
          @update:page="onTaskPageChange"
        />
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="bw-filter">
      <div class="bw-search">
        <Icon icon="mdi:magnify" class="bw-search__icon" />
        <input
          v-model="searchKeyword"
          class="bw-search__input"
          placeholder="搜索标题/正文/问答..."
          @keyup.enter="onSearch"
        />
        <button v-if="searchKeyword" class="bw-search__clear" @click="searchKeyword = ''; onSearch()">
          <Icon icon="mdi:close" class="h-3.5 w-3.5" />
        </button>
      </div>
      <div class="bw-tags">
        <button
          v-for="t in baguwenStore.tags"
          :key="t.tag"
          class="bw-tag"
          :class="baguwenStore.filter.tag === t.tag ? 'bw-tag--active' : ''"
          @click="onTagFilter(t.tag)"
        >
          {{ t.tag }}
          <span class="bw-tag__count">{{ t.count }}</span>
        </button>
      </div>
    </div>

    <!-- 文章列表 -->
    <div v-if="listLoading" class="bw-grid">
      <div v-for="i in 6" :key="i" class="bw-card-skeleton">
        <n-skeleton text :repeat="2" />
        <n-skeleton text style="width: 40%" />
      </div>
    </div>
    <div v-else class="bw-grid fade-in">
      <div
        v-for="a in baguwenStore.articles"
        :key="a.id"
        class="bw-card"
        @click="router.push({ name: 'baguwen-detail', params: { id: a.id } })"
      >
        <div class="bw-card__top">
          <h3 class="bw-card__title">{{ a.title }}</h3>
          <div class="bw-card__actions" @click.stop>
            <button class="bw-card__action" title="收藏到知识库" @click="openFavorite(a.id)">
              <Icon icon="mdi:bookmark-plus-outline" class="h-4 w-4" />
            </button>
            <n-popconfirm @positive-click="baguwenStore.removeArticle(a.id)">
              <template #trigger>
                <span class="bw-card__action bw-card__action--delete">
                  <Icon icon="mdi:trash-can-outline" class="h-4 w-4" />
                </span>
              </template>
              删除该文章及其问答对?
            </n-popconfirm>
          </div>
        </div>
        <div v-if="a.tags?.length" class="bw-card__tags">
          <span v-for="t in a.tags" :key="t" class="bw-card__tag">{{ t }}</span>
        </div>
        <div class="bw-card__footer">
          <Icon icon="mdi:calendar-blank-outline" class="h-3.5 w-3.5" />
          <span>{{ new Date(a.createdAt).toLocaleDateString() }}</span>
        </div>
      </div>
    </div>

    <div v-if="!listLoading && !baguwenStore.articles.length" class="bw-empty">
      <Icon icon="mdi:file-document-outline" class="h-10 w-10 text-gray-300 dark:text-gray-600" />
      <span>暂无文章,在上方输入 URL 开始导入</span>
    </div>

    <div v-if="baguwenStore.total > baguwenStore.filter.size" class="bw-pagination">
      <n-pagination
        :page="baguwenStore.filter.page"
        :page-size="baguwenStore.filter.size"
        :item-count="baguwenStore.total"
        @update:page="onPageChange"
      />
    </div>
  </div>

  <!-- 收藏弹窗 -->
  <n-modal
    v-model:show="favoriteModal"
    preset="dialog"
    title="收藏到知识库"
    positive-text="收藏"
    negative-text="取消"
    :positive-button-props="{ loading: favoriting }"
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
</template>

<style scoped>
/* ==================== 布局 ==================== */
.bw-layout {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ==================== 页面头部 ==================== */
.bw-header {
  display: flex;
  align-items: center;
  gap: 12px;
}
.bw-header__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 0;
  background: var(--nb-alert);
  color: #050508;
  clip-path: polygon(8px 0, 100% 0, 100% calc(100% - 8px), calc(100% - 8px) 100%, 0 100%, 0 8px);
  box-shadow: var(--nb-glow-alert);
  flex-shrink: 0;
}
.bw-header__title {
  font-family: var(--nb-font-mono);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--nb-text-primary);
}
.bw-header__desc {
  font-size: 12px;
  color: var(--nb-text-faint);
  margin-top: 1px;
}
.bw-header__count {
  margin-left: auto;
  padding: 4px 12px;
  border-radius: 0;
  font-family: var(--nb-font-mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.06em;
  color: var(--nb-alert);
  background: transparent;
  border: 1px solid color-mix(in srgb, var(--nb-alert) 40%, transparent);
}

/* ==================== 导入区 ==================== */
.bw-import {
  padding: 24px;
  border-radius: 0;
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
  border-left: 2px solid var(--nb-alert);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  box-shadow: var(--nb-card-shadow);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.bw-import__header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.bw-import__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 0;
  background: var(--nb-alert-soft);
  color: var(--nb-alert);
  border: 1px solid color-mix(in srgb, var(--nb-alert) 30%, transparent);
  flex-shrink: 0;
}
.bw-import__label {
  font-size: 14px;
  font-weight: 600;
  color: var(--nb-text-primary);
}
.bw-import__hint {
  font-size: 12px;
  color: var(--nb-text-faint);
  margin-top: 1px;
}
.bw-import__textarea {
  width: 100%;
  padding: 12px 14px;
  border-radius: 0;
  border: 1px solid var(--nb-border);
  border-bottom: 2px solid var(--nb-border);
  background: var(--nb-surface);
  font-family: var(--nb-font-mono);
  font-size: 12px;
  line-height: 1.6;
  color: var(--nb-text-primary);
  resize: vertical;
  min-height: 72px;
  max-height: 160px;
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.bw-import__textarea::placeholder {
  color: var(--nb-text-faint);
}
.bw-import__textarea:focus {
  border-color: var(--nb-accent);
  border-bottom-color: var(--nb-accent);
  box-shadow: 0 0 12px -6px var(--nb-accent);
}
.bw-import__footer {
  display: flex;
  justify-content: flex-end;
}
.bw-import__btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 22px;
  border-radius: 0;
  border: 1px solid var(--nb-accent);
  background: transparent;
  color: var(--nb-accent);
  font-family: var(--nb-font-mono);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.06em;
  clip-path: polygon(8px 0, 100% 0, 100% calc(100% - 8px), calc(100% - 8px) 100%, 0 100%, 0 8px);
  cursor: pointer;
  transition: all 0.2s var(--nb-ease-spring);
}
.bw-import__btn:hover:not(:disabled) {
  background: var(--nb-accent);
  color: #050508;
  box-shadow: var(--nb-glow-accent);
  animation: bw-glitch-shift 0.3s steps(2) infinite;
}
.bw-import__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
@keyframes bw-glitch-shift {
  0% { transform: translateX(0); }
  25% { transform: translateX(1px); }
  50% { transform: translateX(-1px); }
  75% { transform: translateX(0.5px); }
  100% { transform: translateX(0); }
}

/* ==================== 任务区 ==================== */
.bw-tasks {
  padding: 18px;
  border-radius: 0;
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  box-shadow: var(--nb-card-shadow);
}
.bw-tasks__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.bw-tasks__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 0;
  background: var(--nb-alert-soft);
  color: var(--nb-alert);
  border: 1px solid color-mix(in srgb, var(--nb-alert) 30%, transparent);
}
.bw-tasks__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--nb-text-primary);
}
.bw-tasks__status {
  font-size: 12px;
  color: var(--nb-alert);
}
.bw-tasks__status--done {
  color: var(--nb-accent);
}
.bw-tasks__clear {
  padding: 4px 10px;
  border-radius: 0;
  border: 1px solid transparent;
  background: transparent;
  font-family: var(--nb-font-mono);
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--nb-text-faint);
  cursor: pointer;
  transition: all 0.15s ease;
}
.bw-tasks__clear:hover {
  background: var(--nb-alert-soft);
  border-color: color-mix(in srgb, var(--nb-alert) 40%, transparent);
  color: var(--nb-alert);
}
.bw-tasks__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.bw-tasks__pagination {
  display: flex;
  justify-content: center;
  padding-top: 12px;
}

/* 任务项 */
.bw-task {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 0;
  border: 1px solid transparent;
  transition: background 0.15s ease;
}
.bw-task:hover {
  background: var(--nb-nav-active-bg);
  border-color: var(--nb-border);
}
.bw-task__dot {
  width: 7px;
  height: 7px;
  border-radius: 0;
  background: var(--nb-text-faint);
  flex-shrink: 0;
}
.bw-task__dot--processing {
  background: var(--nb-accent);
  box-shadow: 0 0 6px var(--nb-accent);
  animation: bw-pulse 1.2s ease-in-out infinite;
}
.bw-task__dot--success {
  background: var(--nb-accent);
  box-shadow: 0 0 4px var(--nb-accent);
}
.bw-task__dot--failed {
  background: var(--nb-alert);
  box-shadow: 0 0 4px var(--nb-alert);
}
@keyframes bw-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
@media (prefers-reduced-motion: reduce) {
  .bw-task__dot--processing { animation: none; }
}
.bw-task__body {
  flex: 1;
  min-width: 0;
}
.bw-task__url {
  font-family: var(--nb-font-mono);
  font-size: 12px;
  color: var(--nb-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bw-task__error {
  font-size: 11px;
  color: var(--nb-alert);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bw-task__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.bw-task__badge {
  padding: 3px 9px;
  border-radius: 0;
  border: 1px solid currentColor;
  font-family: var(--nb-font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.06em;
}
.bw-task__retry {
  padding: 2px 8px;
  border-radius: 0;
  border: 1px solid transparent;
  background: transparent;
  font-family: var(--nb-font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
  color: var(--nb-text-faint);
  cursor: pointer;
  transition: all 0.15s ease;
}
.bw-task__retry:hover {
  background: var(--nb-alert-soft);
  border-color: color-mix(in srgb, var(--nb-alert) 40%, transparent);
  color: var(--nb-alert);
}
.bw-task__delete {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 0;
  color: var(--nb-text-faint);
  cursor: pointer;
  transition: all 0.15s ease;
}
.bw-task__delete:hover {
  background: var(--nb-alert-soft);
  color: var(--nb-alert);
}

/* ==================== 筛选栏 ==================== */
.bw-filter {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.bw-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 0;
  background: var(--nb-input-bg);
  border: 1px solid var(--nb-border);
  border-bottom: 2px solid var(--nb-border);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  max-width: 320px;
  flex: 1;
}
.bw-search:focus-within {
  border-color: var(--nb-accent);
  border-bottom-color: var(--nb-accent);
  box-shadow: 0 0 12px -6px var(--nb-accent);
  background: var(--nb-card-bg-solid);
}
.bw-search__icon {
  width: 16px;
  height: 16px;
  color: var(--nb-text-faint);
  flex-shrink: 0;
}
.bw-search__input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 13px;
  color: var(--nb-text-primary);
}
.bw-search__input::placeholder {
  color: var(--nb-text-faint);
}
.bw-search__clear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 6px;
  border: none;
  background: var(--nb-nav-active-bg);
  color: var(--nb-text-faint);
  cursor: pointer;
  transition: all 0.15s ease;
}
.bw-search__clear:hover {
  background: var(--nb-border);
  color: var(--nb-text-secondary);
}

/* 标签 chips */
.bw-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.bw-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
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
.bw-tag:hover {
  border-color: var(--nb-border-strong);
  background: var(--nb-nav-active-bg);
  transform: translateX(2px);
}
.bw-tag--active {
  border-color: var(--nb-accent);
  background: var(--nb-accent-soft);
  color: var(--nb-accent);
  box-shadow: 0 0 8px -3px var(--nb-accent);
}
.bw-tag__count {
  font-size: 10px;
  opacity: 0.6;
}

/* ==================== 文章网格 ==================== */
.bw-grid {
  display: grid;
  grid-template-columns: repeat(1, 1fr);
  gap: 12px;
}
@media (min-width: 768px) {
  .bw-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (min-width: 1280px) {
  .bw-grid { grid-template-columns: repeat(3, 1fr); }
}

/* 骨架 */
.bw-card-skeleton {
  padding: 20px;
  border-radius: 0;
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 文章卡片 */
.bw-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 20px;
  border-radius: 0;
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
  border-left: 2px solid color-mix(in srgb, var(--nb-alert) 50%, transparent);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  box-shadow: var(--nb-card-shadow);
  cursor: pointer;
  transition: all 0.25s var(--nb-ease-spring);
}
.bw-card:hover {
  border-color: var(--nb-border-strong);
  border-left-color: var(--nb-alert);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--nb-alert) 25%, transparent), var(--nb-card-shadow-hover);
  transform: translateX(2px);
}
.bw-card__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.bw-card__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--nb-text-primary);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.bw-card__actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s ease;
  flex-shrink: 0;
}
.bw-card:hover .bw-card__actions {
  opacity: 1;
}
.bw-card__action {
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
.bw-card__action:hover {
  background: var(--nb-nav-active-bg);
  border-color: var(--nb-border);
  color: var(--nb-accent);
}
.bw-card__action--delete:hover {
  color: var(--nb-alert);
  background: var(--nb-alert-soft);
  border-color: color-mix(in srgb, var(--nb-alert) 40%, transparent);
}
.bw-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}
.bw-card__tag {
  padding: 2px 9px;
  border-radius: 0;
  font-family: var(--nb-font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.05em;
  color: var(--nb-text-secondary);
  background: transparent;
  border: 1px solid var(--nb-border);
}
.bw-card__footer {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: auto;
  padding-top: 6px;
  font-size: 12px;
  color: var(--nb-text-faint);
}

/* ==================== 空态 ==================== */
.bw-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 48px 0;
  font-family: var(--nb-font-mono);
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--nb-text-faint);
}

/* ==================== 分页 ==================== */
.bw-pagination {
  display: flex;
  justify-content: center;
  padding-top: 8px;
}
</style>
