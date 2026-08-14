<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NModal, NSelect, NTabPane, NTabs, NTag } from 'naive-ui'
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
  <div v-else-if="detail" class="mx-auto max-w-3xl space-y-4">
    <div class="flex items-start justify-between gap-3">
      <h1 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ detail.title }}</h1>
      <div class="flex shrink-0 items-center gap-2">
        <n-button quaternary size="small" @click="router.back()">
          <template #icon><Icon icon="mdi:arrow-left" class="h-4 w-4" /></template>
          返回
        </n-button>
        <n-button size="small" @click="openFavorite">
          <template #icon><Icon icon="mdi:bookmark-plus-outline" class="h-4 w-4" /></template>
          收藏
        </n-button>
      </div>
    </div>
    <div class="flex flex-wrap items-center gap-2">
      <n-tag v-for="t in detail.tags" :key="t" size="small" :bordered="false" type="warning">
        {{ t }}
      </n-tag>
      <a
        v-if="detail.url"
        :href="detail.url"
        target="_blank"
        rel="noopener"
        class="text-xs text-cyan-600 hover:underline dark:text-cyan-400"
      >
        原文链接 ↗
      </a>
    </div>
    <n-tabs v-model:value="activeTab" type="line">
      <n-tab-pane name="qa" tab="问答">
        <div class="space-y-3 pt-2">
          <div v-for="(qa, i) in detail.qaPairs" :key="qa.id" class="glass-card !p-4">
            <div class="flex items-start gap-2">
              <span class="mt-0.5 shrink-0 text-sm text-gray-500">Q{{ i + 1 }}</span>
              <div class="flex-1">
                <div class="flex items-center justify-between gap-2">
                  <span class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ qa.question }}</span>
                  <n-tag
                    v-if="difficultyMeta[qa.difficulty]"
                    size="tiny"
                    :type="difficultyMeta[qa.difficulty].type"
                    :bordered="false"
                  >
                    {{ difficultyMeta[qa.difficulty].label }}
                  </n-tag>
                </div>
                <div class="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-gray-600 dark:text-gray-400">
                  {{ qa.answer }}
                </div>
              </div>
            </div>
          </div>
          <div v-if="!detail.qaPairs.length" class="py-8 text-center text-sm text-gray-400 dark:text-gray-600">
            本文未提取到问答对
          </div>
        </div>
      </n-tab-pane>
      <n-tab-pane name="article" tab="原文">
        <pre
          v-if="detail.content"
          class="whitespace-pre-wrap pt-2 text-sm leading-relaxed text-gray-600 dark:text-gray-400"
        >{{ detail.content }}</pre>
        <div v-else class="py-8 text-center text-sm text-gray-400 dark:text-gray-600">
          未提取到原文
        </div>
      </n-tab-pane>
    </n-tabs>
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
