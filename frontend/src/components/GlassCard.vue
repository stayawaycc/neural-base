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

<style scoped>
.glass-card {
  background: var(--nb-card-bg);
  border: 1px solid var(--nb-border);
  border-radius: 12px;
  backdrop-filter: blur(12px);
  transition:
    transform 0.3s ease,
    border-color 0.3s ease,
    box-shadow 0.3s ease;
}
.glass-card--padded {
  padding: 16px;
}
.glass-card--hover:hover {
  transform: translateY(-2px);
  border-color: var(--nb-accent);
  box-shadow: 0 8px 32px -8px var(--nb-accent);
}
.glass-card--cyan {
  --nb-accent: #22d3ee;
}
.glass-card--violet {
  --nb-accent: #a78bfa;
}
.glass-card--amber {
  --nb-accent: #fbbf24;
}
.glass-card--emerald {
  --nb-accent: #34d399;
}
</style>
