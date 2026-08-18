<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
</script>

<template>
  <div class="pointer-events-none fixed inset-0 -z-10 overflow-hidden" aria-hidden="true">
    <!-- 霓虹光晕(极淡,双色) -->
    <div class="cy-orb cy-orb--neon" :class="{ 'cy-orb--light': !themeStore.isDark }" />
    <div class="cy-orb cy-orb--alert" :class="{ 'cy-orb--light': !themeStore.isDark }" />
    <!-- 底部地平线光 -->
    <div class="cy-horizon" :class="{ 'cy-horizon--light': !themeStore.isDark }" />
  </div>
</template>

<style scoped>
.cy-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  will-change: transform;
}
/* 电光蓝:左上 */
.cy-orb--neon {
  top: -24%;
  left: -16%;
  width: 56vw;
  height: 56vw;
  background: radial-gradient(
    circle at 40% 40%,
    rgba(0, 240, 255, 0.07) 0%,
    rgba(0, 240, 255, 0.025) 40%,
    transparent 70%
  );
  animation: cy-drift-neon 26s ease-in-out infinite alternate;
}
/* 警告橙:右下 */
.cy-orb--alert {
  bottom: -26%;
  right: -18%;
  width: 52vw;
  height: 52vw;
  background: radial-gradient(
    circle at 55% 55%,
    rgba(255, 107, 0, 0.05) 0%,
    rgba(255, 107, 0, 0.018) 40%,
    transparent 70%
  );
  animation: cy-drift-alert 32s ease-in-out infinite alternate;
}
/* 浅色变体:更深但更淡 */
.cy-orb--light.cy-orb--neon {
  background: radial-gradient(circle at 40% 40%, rgba(0, 163, 192, 0.1) 0%, rgba(0, 163, 192, 0.03) 40%, transparent 70%);
}
.cy-orb--light.cy-orb--alert {
  background: radial-gradient(circle at 55% 55%, rgba(217, 95, 0, 0.07) 0%, rgba(217, 95, 0, 0.02) 40%, transparent 70%);
}

/* 底部地平线霓虹光带 */
.cy-horizon {
  position: absolute;
  bottom: -18%;
  left: 50%;
  transform: translateX(-50%);
  width: 120vw;
  height: 30vh;
  background: radial-gradient(
    ellipse at 50% 100%,
    rgba(0, 240, 255, 0.045) 0%,
    transparent 55%
  );
}
.cy-horizon--light {
  background: radial-gradient(ellipse at 50% 100%, rgba(0, 163, 192, 0.06) 0%, transparent 55%);
}

@keyframes cy-drift-neon {
  0% { transform: translate3d(0, 0, 0) scale(1); }
  50% { transform: translate3d(3vw, 2vh, 0) scale(1.05); }
  100% { transform: translate3d(-2vw, -1vh, 0) scale(0.96); }
}
@keyframes cy-drift-alert {
  0% { transform: translate3d(0, 0, 0) scale(1); }
  50% { transform: translate3d(-3vw, -2vh, 0) scale(1.04); }
  100% { transform: translate3d(2vw, 1vh, 0) scale(0.97); }
}

@media (prefers-reduced-motion: reduce) {
  .cy-orb {
    animation: none;
  }
}
</style>
