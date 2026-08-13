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
    <n-upload-dragger class="glass-card !border-dashed">
      <div class="flex flex-col items-center gap-2 py-2">
        <n-icon size="28" :color="uploading ? '#9ca3af' : '#22d3ee'">
          <Icon :icon="uploading ? 'mdi:loading' : 'mdi:cloud-upload-outline'" />
        </n-icon>
        <span class="text-sm text-gray-700 dark:text-gray-300">
          {{ uploading ? '上传中…' : '点击或拖拽文件到此处上传' }}
        </span>
        <span class="text-xs text-gray-400 dark:text-gray-600">支持 PDF / Word / Markdown / TXT</span>
      </div>
    </n-upload-dragger>
  </n-upload>
</template>
