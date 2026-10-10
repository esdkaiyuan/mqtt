<template>
  <aside class="orch-palette">
    <p class="orch-palette__title">可拖入节点</p>
    <ul class="orch-palette__list">
      <li v-for="item in items" :key="item.type">
        <button
          type="button"
          class="orch-palette__item"
          :class="{
            'orch-palette__item--disabled': item.disabled,
            'orch-palette__item--highlight': item.highlight
          }"
          :disabled="item.disabled"
          :draggable="!item.disabled"
          @click="handleAdd(item)"
          @dragstart="handleDragStart($event, item)"
        >
          <span class="orch-palette__label">{{ item.label }}</span>
          <span class="orch-palette__desc">{{ item.description }}</span>
        </button>
      </li>
    </ul>
  </aside>
</template>

<script setup>
import { NODE_DND_MIME } from '@/utils/orchestration'

defineProps({
  items: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['add'])

function handleAdd(item) {
  if (item.disabled) return
  emit('add', { type: item.type })
}

function handleDragStart(event, item) {
  if (item.disabled) return
  event.dataTransfer?.setData(NODE_DND_MIME, item.type)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}
</script>

<style scoped>
.orch-palette {
  flex: 0 0 168px;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-sm);
  background: var(--color-card);
}

.orch-palette__title {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.orch-palette__list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  margin: 0;
  padding: 0;
  list-style: none;
}

.orch-palette__item {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2xs);
  width: 100%;
  padding: var(--spacing-sm);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-sm);
  background: var(--color-card);
  text-align: left;
  cursor: grab;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.orch-palette__item:hover:not(:disabled) {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-card);
}

.orch-palette__item--highlight {
  border-color: var(--color-warning);
  background: var(--color-warning-light);
}

.orch-palette__item--disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.orch-palette__label {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.orch-palette__desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
</style>