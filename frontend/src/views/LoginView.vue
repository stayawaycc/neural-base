<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NForm, NFormItem, NInput } from 'naive-ui'
import { useUserStore } from '@/stores/user'
import { message } from '@/utils/feedback'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

const username = ref('')
const password = ref('')
const loading = ref(false)

async function onSubmit() {
  if (!username.value || !password.value) {
    message.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login(username.value, password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.push(redirect)
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4">
    <div class="glass-card w-full max-w-sm !p-8">
      <div class="mb-8 flex flex-col items-center gap-3">
        <span class="h-3 w-3 rounded-full bg-gradient-to-r from-cyan-400 to-purple-400" />
        <h1 class="text-xl font-semibold tracking-wide text-gray-900 dark:text-gray-100">NeuralBase</h1>
        <p class="text-xs text-gray-500">个人 AI 知识中枢</p>
      </div>
      <n-form label-placement="top" @keyup.enter="onSubmit">
        <n-form-item label="用户名">
          <n-input v-model:value="username" placeholder="请输入用户名" />
        </n-form-item>
        <n-form-item label="密码">
          <n-input v-model:value="password" type="password" placeholder="请输入密码" show-password-on="click" />
        </n-form-item>
        <n-button type="primary" block :loading="loading" @click="onSubmit">登 录</n-button>
      </n-form>
    </div>
  </div>
</template>

<style scoped>
/* GlassCard.vue 的 .glass-card 是 scoped 样式,此处用设计 token 复刻玻璃卡片(global.scss :root 变量) */
.glass-card {
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
  border-radius: 12px;
  backdrop-filter: blur(12px);
}
</style>
