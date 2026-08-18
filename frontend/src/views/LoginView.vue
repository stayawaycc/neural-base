<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NForm, NFormItem, NInput } from 'naive-ui'
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
  <div class="login-page relative flex min-h-screen items-center justify-center overflow-hidden px-5">
    <div class="glass-card relative w-full max-w-[380px] px-8 py-12">
      <div class="mb-10 flex flex-col items-center gap-5">
        <span class="nb-brand-badge h-16 w-16">
          <Icon icon="mdi:brain" class="h-8 w-8" />
        </span>
        <div class="text-center">
          <div class="cy-mono mb-2 flex items-center justify-center gap-1.5 text-[11px]" style="color: var(--nb-text-faint)">
            <Icon icon="mdi:lock-outline" class="h-3.5 w-3.5" />
            [SEC] AUTH_GATE // ACCESS REQUIRED
          </div>
          <h1
            class="cy-glitch text-[32px] font-bold leading-none"
            data-text="NeuralBase"
            style="color: var(--nb-text-primary); font-family: var(--nb-font-mono); letter-spacing: 0.06em"
          >
            NeuralBase
          </h1>
          <p class="mt-3 text-[14px] leading-relaxed" style="color: var(--nb-text-secondary)">
            个人 AI 知识中枢 · 终端接入
          </p>
        </div>
      </div>
      <n-form label-placement="top" @keyup.enter="onSubmit">
        <n-form-item label="用户名">
          <n-input v-model:value="username" placeholder="USERNAME" size="large" />
        </n-form-item>
        <n-form-item label="密码">
          <n-input v-model:value="password" type="password" placeholder="PASSWORD" show-password-on="click" size="large" />
        </n-form-item>
        <button
          type="button"
          class="nb-btn nb-btn--primary !mt-3 w-full"
          :disabled="loading"
          @click="onSubmit"
        >
          {{ loading ? '// CONNECTING...' : '> 登 录' }}
        </button>
      </n-form>
      <p class="mt-10 text-center cy-mono text-[10px]" style="color: var(--nb-text-faint)">
        KB // CHAT // EXAM // FEED
      </p>
    </div>
  </div>
</template>

<style scoped>
:deep(.n-form-item-label) {
  font-family: var(--nb-font-mono);
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
:deep(.n-form-item-label__text) {
  color: var(--nb-text-secondary);
}
</style>
