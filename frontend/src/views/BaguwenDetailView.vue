<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NModal, NSelect, NTag } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { articleDetail, favoriteArticle } from '@/api/baguwen'
import { listKbs } from '@/api/kb'
import { message } from '@/utils/feedback'
import type { BaguwenDetail, Kb } from '@/types'

const route = useRoute()
const router = useRouter()

const detail = ref<BaguwenDetail | null>(null)
const loading = ref(true)
const activeTab = ref('qa')

const favoriteModal = ref(false)
const favoriteKbId = ref<number | null>(null)
const kbs = ref<Kb[]>([])

const difficultyMeta: Record<string, { type: 'success' | 'warning' | 'error'; label: string }> = {
  简单: { type: 'success', label: '简单' },
  中等: { type: 'warning', label: '中等' },
  困难: { type: 'error', label: '困难' },
}

const readerParas = computed(() =>
  (detail.value?.content ?? '')
    .split(/\n{2,}/)
    .map((s) => s.trim())
    .filter(Boolean),
)

onMounted(async () => {
  try {
    detail.value = await articleDetail(Number(route.params.id))
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    loading.value = false
  }
})

async function openFavorite() {
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
  if (!detail.value || !favoriteKbId.value) {
    message.warning('请选择知识库')
    return
  }
  try {
    const result = await favoriteArticle(detail.value.id, favoriteKbId.value)
    favoriteModal.value = false
    message.success(`已收藏,写入 ${result.embedded} 个向量片段`)
  } catch (e) {
    message.error((e as Error).message)
  }
}
</script>

<template>
  <div v-if="loading" class="py-16 text-center text-sm text-gray-400 dark:text-gray-600">加载中…</div>
  <div v-else-if="detail" class="mx-auto max-w-3xl space-y-5">
    <!-- 头部:返回 + 徽章 + 标题/标签 + 收藏 -->
    <div class="flex items-center gap-3">
      <button
        class="btn-press flex h-9 w-9 shrink-0 items-center justify-center rounded-none border transition-colors hover:text-[var(--nb-accent)]"
        style="border-color: var(--nb-border); color: var(--nb-text-secondary)"
        title="返回"
        @click="router.back()"
      >
        <Icon icon="mdi:arrow-left" class="h-5 w-5" />
      </button>
      <span class="nb-brand-badge h-9 w-9 shrink-0">
        <Icon icon="mdi:file-document-multiple-outline" class="h-5 w-5" />
      </span>
      <div class="min-w-0 flex-1">
        <h1 class="truncate text-xl font-semibold tracking-tight" style="color: var(--nb-text-primary)">
          {{ detail.title }}
        </h1>
        <div class="mt-1 flex flex-wrap items-center gap-2 meta-row">
          <n-tag v-for="t in detail.tags" :key="t" size="tiny" :bordered="false">
            {{ t }}
          </n-tag>
          <a
            v-if="detail.url"
            :href="detail.url"
            target="_blank"
            rel="noopener"
            class="text-xs hover:underline"
            style="color: var(--nb-accent)"
          >
            原文链接 ↗
          </a>
        </div>
      </div>
      <n-button secondary size="small" class="shrink-0" @click="openFavorite">
        <template #icon><Icon icon="mdi:bookmark-plus-outline" class="h-4 w-4" /></template>
        收藏
      </n-button>
    </div>

    <!-- Vuetify tabs:滑动指示器 + 玻璃容器融合 -->
    <div class="overflow-hidden rounded-none border" style="border-color: var(--nb-border)">
      <v-tabs v-model="activeTab" bg-color="transparent" slider-color="primary" density="compact">
        <v-tab value="qa">问答</v-tab>
        <v-tab value="article">原文</v-tab>
      </v-tabs>
      <v-window v-model="activeTab">
        <v-window-item value="qa">
          <div class="space-y-3 p-4">
            <div
              v-for="(qa, i) in detail.qaPairs"
              :key="qa.id"
              class="glass-card glass-card--amber glass-card--hover !p-5"
            >
              <div class="flex items-start gap-3">
                <span class="qa-badge">{{ i + 1 }}</span>
                <div class="min-w-0 flex-1">
                  <div class="flex items-center justify-between gap-2">
                    <span class="text-[15px] font-medium" style="color: var(--nb-text-primary)">{{ qa.question }}</span>
                    <n-tag
                      v-if="difficultyMeta[qa.difficulty]"
                      size="tiny"
                      :type="difficultyMeta[qa.difficulty].type"
                      :bordered="false"
                      class="shrink-0"
                    >
                      {{ difficultyMeta[qa.difficulty].label }}
                    </n-tag>
                  </div>
                  <div class="mt-2.5 whitespace-pre-wrap text-sm leading-relaxed" style="color: var(--nb-text-secondary)">
                    {{ qa.answer }}
                  </div>
                </div>
              </div>
            </div>
            <div v-if="!detail.qaPairs.length" class="py-8 text-center text-sm text-gray-400 dark:text-gray-600">
              本文未提取到问答对
            </div>
          </div>
        </v-window-item>
        <v-window-item value="article">
          <div class="reader">
            <p v-for="(para, i) in readerParas" :key="i" class="reader__para">{{ para }}</p>
            <div v-if="!detail.content" class="py-8 text-center text-sm text-gray-400 dark:text-gray-600">
              未提取到原文
            </div>
          </div>
        </v-window-item>
      </v-window>
    </div>
  </div>
  <div v-else class="py-16 text-center text-sm text-gray-400 dark:text-gray-600">文章不存在</div>

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
</template>

<style scoped>
/* 元数据:小字号 + 浅灰,与正文对比 */
.meta-row :deep(.n-tag__content) {
  font-size: 11px;
  color: var(--nb-text-faint);
}

/* 原文阅读排版:大行高 + 段落呼吸 + 限宽 */
.reader {
  max-width: 800px;
  margin: 0 auto;
  padding: 12px 8px 20px;
}
.reader__para {
  margin-bottom: 1.5em;
  font-size: 16px;
  line-height: 1.8;
  text-align: justify;
  color: var(--nb-text-primary);
  word-break: break-word;
}
.reader__para:last-child {
  margin-bottom: 0;
}

/* 问答序号徽章:霓虹空心数字 */
.qa-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  margin-top: 2px;
  border-radius: 0;
  border: 1px solid color-mix(in srgb, var(--nb-alert) 45%, transparent);
  font-family: var(--nb-font-mono);
  font-size: 11px;
  font-weight: 600;
  color: var(--nb-alert);
  background: var(--nb-alert-soft);
}
</style>
