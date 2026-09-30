<template>
  <div v-if="totalPages > 1" class="pagination">
    <button class="btn-page" :disabled="page <= 1" @click="emit('go', page - 1)">上一页</button>
    <span class="page-info">第 {{ page }} / {{ totalPages }} 页</span>
    <button class="btn-page" :disabled="page >= totalPages" @click="emit('go', page + 1)">下一页</button>
  </div>
</template>

<script setup>
defineProps({
  page: { type: Number, required: true },
  totalPages: { type: Number, default: 1 }
})

const emit = defineEmits(['go'])
</script>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
}

.btn-page {
  padding: 6px 16px;
  background: var(--color-white);
  color: var(--color-gray-dark);
  border: 1px solid var(--color-border-color);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-sm);
  cursor: pointer;
  font-family: var(--font-family);
}

.btn-page:hover:not(:disabled) {
  background: var(--color-gray-light);
}

.btn-page:disabled {
  color: var(--color-gray-text);
  cursor: not-allowed;
}

.page-info {
  font-size: var(--font-size-sm);
  color: var(--color-gray-text);
}
</style>
