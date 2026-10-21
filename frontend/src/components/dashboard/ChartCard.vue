<template>
  <div :class="['chart-card', 'card', { 'full-width': fullWidth, 'chart-card--dark': dark }]">
    <div class="chart-header">
      <h3 class="chart-title">{{ title }}</h3>
      <span v-if="subtitle" class="chart-subtitle">{{ subtitle }}</span>
    </div>
    <slot />
  </div>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    required: true
  },
  subtitle: {
    type: String,
    default: ''
  },
  fullWidth: {
    type: Boolean,
    default: false
  },
  /** 大屏深色模式（T-46）：透明深色卡片 + 浅色标题。 */
  dark: {
    type: Boolean,
    default: false
  }
})
</script>

<style scoped>
.chart-card {
  padding: var(--spacing-lg);
}

.chart-card.full-width {
  margin-bottom: var(--spacing-lg);
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.chart-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.chart-subtitle {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.chart-card.chart-card--dark {
  background: rgba(15, 23, 42, 0.72);
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: none;
}

.chart-card--dark .chart-title {
  color: #e2e8f0;
}

.chart-card--dark .chart-subtitle {
  color: #94a3b8;
}
</style>
