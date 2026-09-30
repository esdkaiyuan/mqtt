<template>
  <div class="history-query-page page-container">
    <div class="page-header">
      <h2>历史数据查询</h2>
    </div>

    <QueryForm
      v-model:device-id="form.deviceId"
      v-model:topic="form.topic"
      v-model:start-time="form.startTime"
      v-model:end-time="form.endTime"
      :device-options="deviceOptions"
      :loading="loading"
      @submit="submit"
      @reset="reset"
    />

    <div v-if="hasSearched" class="result-section">
      <div class="result-header">
        <h3 class="card-title">查询结果</h3>
        <span class="result-count">共 {{ total }} 条记录</span>
      </div>

      <ResultTable :records="records" :loading="loading" />

      <PagePagination
        :page="currentPage"
        :page-size="pageSize"
        :total-pages="totalPages"
        @go="goToPage"
        @change-size="changePageSize"
      />
    </div>

    <div v-else class="initial-state empty-state">
      <svg-icon name="history" :size="48" color="#E0E0E0" />
      <p class="empty-state-text">选择查询条件后点击"查询"</p>
    </div>
  </div>
</template>

<script setup>
import SvgIcon from '@/components/Icon.vue'
import QueryForm from '@/components/history/QueryForm.vue'
import ResultTable from '@/components/history/ResultTable.vue'
import PagePagination from '@/components/history/PagePagination.vue'
import { useHistoryQuery } from '@/composables/useHistoryQuery'

const {
  form,
  deviceOptions,
  hasSearched,
  records,
  total,
  totalPages,
  loading,
  currentPage,
  pageSize,
  submit,
  reset,
  goToPage,
  changePageSize
} = useHistoryQuery()
</script>

<style scoped>
.result-section {
  margin-top: var(--spacing-lg);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-gray-dark);
  margin-bottom: var(--spacing-md);
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px solid var(--border-color-light);
}

.result-count {
  font-size: var(--font-size-sm);
  color: var(--color-gray-text);
}

.initial-state {
  margin-top: var(--spacing-xl);
}

.empty-state-text {
  font-size: var(--font-size-sm);
  color: var(--color-gray-text);
  margin-top: var(--spacing-sm);
}
</style>
