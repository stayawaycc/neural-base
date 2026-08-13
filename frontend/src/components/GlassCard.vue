<script setup lang="ts">
withDefaults(
  defineProps<{
    accent?: 'cyan' | 'violet' | 'amber' | 'emerald'
    hoverable?: boolean
    padded?: boolean
  }>(),
  { accent: undefined, hoverable: false, padded: true },
)
</script>

<template>
  <div
    class="glass-card"
    :class="[
      accent ? `glass-card--${accent}` : '',
      hoverable ? 'glass-card--hover' : '',
      padded ? 'glass-card--padded' : '',
    ]"
  >
    <slot />
  </div>
</template>
