<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import { darkTheme, lightTheme, NConfigProvider } from 'naive-ui'
import { gsap } from 'gsap'
import { darkThemeOverrides, lightThemeOverrides } from '@/styles/theme'
import { useThemeStore } from '@/stores/theme'
import { applyFeedbackTheme } from '@/utils/feedback'
import AppHeader from '@/components/AppHeader.vue'
import BottomTabBar from '@/components/BottomTabBar.vue'
import NeuralBackground from '@/components/NeuralBackground.vue'

const route = useRoute()
const isLogin = computed(() => route.name === 'login')
const themeStore = useThemeStore()

watchEffect(() => applyFeedbackTheme(themeStore.isDark))

// GSAP 页面转场(JS hooks):入场上浮渐显;离开立即完成,避免双视图高度翻倍
const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
function onEnter(el: Element, done: () => void) {
  if (reduced) {
    done()
    return
  }
  gsap.fromTo(
    el,
    { opacity: 0, y: 14 },
    { opacity: 1, y: 0, duration: 0.35, ease: 'power2.out', onComplete: done },
  )
}
function onLeave(_el: Element, done: () => void) {
  done()
}
</script>

<template>
  <n-config-provider
    :theme="themeStore.isDark ? darkTheme : lightTheme"
    :theme-overrides="themeStore.isDark ? darkThemeOverrides : lightThemeOverrides"
  >
    <NeuralBackground />
    <template v-if="!isLogin">
      <AppHeader />
      <main class="mx-auto max-w-7xl px-4 pb-20 pt-24 md:pb-10">
        <router-view v-slot="{ Component }">
          <transition name="page" :css="false" @enter="onEnter" @leave="onLeave">
            <component :is="Component" :key="route.fullPath" />
          </transition>
        </router-view>
      </main>
      <BottomTabBar />
    </template>
    <router-view v-else />
  </n-config-provider>
</template>
