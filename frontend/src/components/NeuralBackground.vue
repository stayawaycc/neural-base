<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useThemeStore } from '@/stores/theme'

// 背景粒子网络:canvas 轻量自绘(无第三方库),深浅色两套配色,尊重 reduced-motion
const themeStore = useThemeStore()
const canvasRef = ref<HTMLCanvasElement>()

type Particle = { x: number; y: number; vx: number; vy: number; r: number; violet: boolean }

let ctx: CanvasRenderingContext2D | null = null
let raf = 0
let particles: Particle[] = []
let w = 0
let h = 0

const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
const LINK_DIST = 120

function palette() {
  return themeStore.isDark
    ? { cyan: '34,211,238', violet: '167,139,250' }
    : { cyan: '8,145,178', violet: '99,102,241' }
}

function resize() {
  const canvas = canvasRef.value
  if (!canvas) return
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  w = window.innerWidth
  h = window.innerHeight
  canvas.width = w * dpr
  canvas.height = h * dpr
  canvas.style.width = `${w}px`
  canvas.style.height = `${h}px`
  ctx = canvas.getContext('2d')
  ctx?.setTransform(dpr, 0, 0, dpr, 0, 0)
  const count = Math.min(Math.floor((w * h) / 30000), 65)
  particles = Array.from({ length: count }, () => ({
    x: Math.random() * w,
    y: Math.random() * h,
    vx: (Math.random() - 0.5) * 0.35,
    vy: (Math.random() - 0.5) * 0.35,
    r: Math.random() * 1.5 + 0.8,
    violet: Math.random() > 0.8,
  }))
}

// 绘制一帧(粒子 + 距离连线)
function draw() {
  if (!ctx || !canvasRef.value) return
  ctx.clearRect(0, 0, w, h)
  const { cyan, violet } = palette()
  for (const p of particles) {
    p.x += p.vx
    p.y += p.vy
    if (p.x < 0 || p.x > w) p.vx *= -1
    if (p.y < 0 || p.y > h) p.vy *= -1
    const rgb = p.violet ? violet : cyan
    ctx.beginPath()
    ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
    ctx.fillStyle = `rgba(${rgb},0.5)`
    ctx.fill()
  }
  for (let i = 0; i < particles.length; i++) {
    for (let j = i + 1; j < particles.length; j++) {
      const a = particles[i]
      const b = particles[j]
      const dx = a.x - b.x
      const dy = a.y - b.y
      const d2 = dx * dx + dy * dy
      if (d2 < LINK_DIST * LINK_DIST) {
        const alpha = (1 - Math.sqrt(d2) / LINK_DIST) * 0.22
        ctx.beginPath()
        ctx.moveTo(a.x, a.y)
        ctx.lineTo(b.x, b.y)
        ctx.strokeStyle = `rgba(${cyan},${alpha})`
        ctx.lineWidth = 0.6
        ctx.stroke()
      }
    }
  }
}

function loop() {
  draw()
  raf = requestAnimationFrame(loop)
}

function onVisibility() {
  if (document.hidden) {
    cancelAnimationFrame(raf)
  } else if (!reduced) {
    raf = requestAnimationFrame(loop)
  }
}

onMounted(() => {
  resize()
  window.addEventListener('resize', resize)
  document.addEventListener('visibilitychange', onVisibility)
  draw() // 先同步画一帧静态画面(后台 tab rAF 被节流时也有粒子)
  if (!reduced) raf = requestAnimationFrame(loop)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  window.removeEventListener('resize', resize)
  document.removeEventListener('visibilitychange', onVisibility)
})
</script>

<template>
  <canvas ref="canvasRef" class="pointer-events-none fixed inset-0 -z-10 opacity-70" aria-hidden="true" />
</template>
