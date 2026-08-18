<script setup lang="ts">
import { ref } from 'vue'
import { NIcon, NUpload, NUploadDragger } from 'naive-ui'
import type { UploadCustomRequestOptions } from 'naive-ui'
import { Icon } from '@iconify/vue'
import { useDocumentStore } from '@/stores/document'
import { message } from '@/utils/feedback'

const docStore = useDocumentStore()
const uploading = ref(false)

async function onUpload({ file, onFinish, onError, onProgress }: UploadCustomRequestOptions) {
  uploading.value = true
  try {
    await docStore.upload(file.file as File, (pct) => onProgress({ percent: pct }))
    onFinish()
    message.success('上传成功,后台解析中')
  } catch (e) {
    onError()
    message.error((e as Error).message)
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <n-upload
    :custom-request="onUpload"
    :show-file-list="false"
    accept=".pdf,.doc,.docx,.md,.txt,.markdown"
    :disabled="uploading"
  >
    <n-upload-dragger class="glass-card upload-ripple !border-dashed">
      <div class="flex flex-col items-center gap-3 py-4">
        <span class="nb-brand-badge h-12 w-12">
          <n-icon size="22">
            <Icon
              :icon="uploading ? 'mdi:loading' : 'mdi:cloud-upload-outline'"
              :class="uploading ? 'animate-spin' : ''"
            />
          </n-icon>
        </span>
        <span class="text-sm" style="color: var(--nb-text-primary)">
          {{ uploading ? '上传中…' : '点击或拖拽文件到此处上传' }}
        </span>
        <span class="cy-mono text-[11px]" style="color: var(--nb-text-faint)">PDF // WORD // MD // TXT — 自动切片入库</span>
      </div>
    </n-upload-dragger>
  </n-upload>
</template>
