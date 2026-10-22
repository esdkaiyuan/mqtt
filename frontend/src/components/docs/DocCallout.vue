<template>
  <aside :class="['doc-callout', `doc-callout--${type}`]">
    <div class="doc-callout__mark" aria-hidden="true">{{ mark }}</div>
    <div class="doc-callout__body">
      <div v-if="title" class="doc-callout__title">{{ title }}</div>
      <div class="doc-callout__content"><slot /></div>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: {
    type: String,
    default: 'note',
    validator: (value) => ['note', 'tip', 'warning', 'danger'].includes(value)
  },
  title: { type: String, default: '' }
})

const mark = computed(() => ({
  note: 'i',
  tip: '✓',
  warning: '!',
  danger: '×'
}[props.type] || 'i'))
</script>

<style scoped>
.doc-callout {
  display: flex;
  gap: var(--spacing-sm);
  margin: var(--spacing-lg) 0;
  padding: var(--spacing-md);
  border: 1px solid var(--border-color-light);
  border-left-width: 3px;
  border-radius: var(--border-radius);
  background: var(--color-bg);
}

.doc-callout--note {
  border-left-color: var(--color-primary);
}

.doc-callout--tip {
  border-left-color: var(--color-success);
}

.doc-callout--warning {
  border-left-color: var(--color-warning);
}

.doc-callout--danger {
  border-left-color: var(--color-danger);
}

.doc-callout__mark {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  margin-top: 1px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-family-mono);
  font-size: 12px;
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
  background: var(--color-primary-light);
}

.doc-callout--tip .doc-callout__mark {
  color: var(--color-success);
  background: var(--color-success-light);
}

.doc-callout--warning .doc-callout__mark {
  color: var(--color-warning);
  background: var(--color-warning-light);
}

.doc-callout--danger .doc-callout__mark {
  color: var(--color-danger);
  background: var(--color-danger-light);
}

.doc-callout__body {
  min-width: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
  line-height: 1.8;
}

.doc-callout__title {
  margin-bottom: 2px;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.doc-callout__content :deep(p) {
  margin: 0;
}

.doc-callout__content :deep(ul) {
  margin: 0;
  padding-left: var(--spacing-lg);
}

.doc-callout__content :deep(code) {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  padding: 1px 5px;
  border-radius: var(--border-radius-sm);
  background: var(--color-white);
  color: var(--color-text-primary);
}
</style>
