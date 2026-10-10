<template>
  <div class="orch-node orch-node--trigger" :class="{ 'orch-node--selected': selected }">
    <Handle type="target" :position="Position.Left" :connectable="false" />
    <div class="orch-node__head">
      <span class="orch-node__title">{{ data.title || '触发' }}</span>
      <span
        v-if="data.status"
        class="orch-node__badge"
        :class="`orch-node__badge--${data.status.type || 'info'}`"
      >
        {{ data.status.label }}
      </span>
    </div>
    <p class="orch-node__summary">{{ data.summary || '—' }}</p>
    <Handle type="source" :position="Position.Right" :connectable="false" />
  </div>
</template>

<script setup>
import { Handle, Position } from '@vue-flow/core'

defineProps({
  data: {
    type: Object,
    default: () => ({})
  },
  selected: {
    type: Boolean,
    default: false
  }
})
</script>

<style scoped>
.orch-node {
  min-width: 180px;
  max-width: 260px;
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-primary);
  border-radius: var(--border-radius);
  background: var(--color-card);
  box-shadow: var(--shadow-card);
  font-family: var(--font-family);
}

.orch-node--selected {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-card-hover);
}

.orch-node__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.orch-node__title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.orch-node__badge {
  padding: 0 var(--spacing-xs);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-xs);
  line-height: 18px;
  color: var(--color-text-regular);
  background: var(--color-border-light);
}

.orch-node__badge--success {
  color: var(--color-success);
  background: var(--color-success-light);
}

.orch-node__badge--warning {
  color: var(--color-warning);
  background: var(--color-warning-light);
}

.orch-node__badge--danger {
  color: var(--color-danger);
  background: var(--color-danger-light);
}

.orch-node__badge--primary {
  color: var(--color-primary);
  background: var(--color-primary-light);
}

.orch-node__summary {
  margin: var(--spacing-xs) 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  word-break: break-all;
}
</style>