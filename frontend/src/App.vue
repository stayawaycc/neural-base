<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import { darkTheme, lightTheme, NConfigProvider } from 'naive-ui'
import { darkThemeOverrides, lightThemeOverrides } from '@/styles/theme'
import { useThemeStore } from '@/stores/theme'
import { applyFeedbackTheme } from '@/utils/feedback'
import AppHeader from '@/components/AppHeader.vue'
import BottomTabBar from '@/components/BottomTabBar.vue'

const route = useRoute()
const isLogin = computed(() => route.name === 'login')
const themeStore = useThemeStore()

watchEffect(() => applyFeedbackTheme(themeStore.isDark))
</script>

<template>
  <n-config-provider
    :theme="themeStore.isDark ? darkTheme : lightTheme"
    :theme-overrides="themeStore.isDark ? darkThemeOverrides : lightThemeOverrides"
  >
    <template v-if="!isLogin">
      <AppHeader />
      <main class="mx-auto max-w-7xl px-4 pb-20 pt-24 md:pb-10">
        <!-- 注意:不包 <transition> —— out-in 与无 key 的懒加载路由组件组合会导致
             新视图永不进入/旧视图永不卸载(SPA 跳转空白)。页面切换动画留到「动画打磨」子项目用带 key 的方式重做。 -->
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </main>
      <BottomTabBar />
    </template>
    <router-view v-else />
  </n-config-provider>
</template>
