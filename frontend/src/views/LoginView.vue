<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NForm, NFormItem, NInput } from 'naive-ui'
import { Icon } from '@iconify/vue'
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
  <div class="relative flex min-h-screen items-center justify-center overflow-hidden px-4">
    <!-- 局部氛围光晕(叠加在 body 全局氛围之上) -->
    <div class="pointer-events-none absolute -top-24 left-1/2 h-72 w-72 -translate-x-1/2 rounded-full bg-cyan-400/15 blur-3xl dark:bg-cyan-400/10" />
    <div class="pointer-events-none absolute -bottom-32 right-1/5 h-80 w-80 rounded-full bg-violet-400/15 blur-3xl dark:bg-violet-400/10" />

    <div class="glass-card relative w-full max-w-sm !p-10">
      <div class="mb-10 flex flex-col items-center gap-4">
        <span class="nb-brand-badge h-14 w-14">
          <Icon icon="mdi:brain" class="h-7 w-7 text-white" />
        </span>
        <div class="text-center">
          <h1 class="text-2xl font-semibold tracking-tight text-gray-900 dark:text-gray-100">NeuralBase</h1>
          <p class="mt-1.5 text-xs text-gray-500 dark:text-gray-400">个人 AI 知识中枢 · 构建你的第二大脑</p>
        </div>
      </div>
      <n-form label-placement="top" @keyup.enter="onSubmit">
        <n-form-item label="用户名">
          <n-input v-model:value="username" placeholder="请输入用户名" size="large" />
        </n-form-item>
        <n-form-item label="密码">
          <n-input v-model:value="password" type="password" placeholder="请输入密码" show-password-on="click" size="large" />
        </n-form-item>
        <n-button type="primary" block size="large" :loading="loading" class="!mt-2" @click="onSubmit">
          登 录
        </n-button>
      </n-form>
      <p class="mt-8 text-center text-[11px] tracking-wide text-gray-400 dark:text-gray-600">
        知识库 · AI 对话 · 八股文库 · AI 日报
      </p>
    </div>
  </div>
</template>
