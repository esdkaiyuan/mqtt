<template>
  <div v-if="totalPages > 1" class="pagination">
    <button class="btn-page" :disabled="page === 1" @click="emit('go', page - 1)">上一页</button>
    <span class="page-info">第 {{ page }} / {{ totalPages }} 页</span>
    <button class="btn-page" :disabled="page === totalPages" @click="emit('go', page + 1)">
      下一页
    </button>
    <div class="page-size-selector">
      <label>每页</label>
      <select class="form-input page-size-select" :value="pageSize" @change="onChangeSize">
        <option :value="10">10条</option>
        <option :value="20">20条</option>
        <option :value="50">50条</option>
      </select>
      <label>条</label>
    </div>
  </div>
</template>

<script setup>
defineProps({
  page: {
    type: Number,
    required: true
  },
  totalPages: {
    type: Number,
    default: 1
  },
  pageSize: {
    type: Number,
    default: 20
  }
})

const emit = defineEmits(['go', 'change-size'])

function onChangeSize(event) {
  emit('change-size', Number(event.target.value))
}
</script>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-md);
  margin-top: var(--spacing-lg);
  padding: var(--spacing-md);
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

.page-size-selector {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-sm);
  color: var(--color-gray-text);
}

.page-size-select {
  width: 70px;
  padding: 4px 8px;
}
</style>
