<template>
  <div :class="['chart-card', 'card', { 'full-width': fullWidth }]">
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
</style>
