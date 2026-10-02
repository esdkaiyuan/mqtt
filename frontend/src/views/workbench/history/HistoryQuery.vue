<template>
  <div class="history-query-page page">
    <PageHeader title="历史数据" desc="按设备、Topic 与时间范围查询历史消息" />

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

      <div class="result-pagination">
        <el-pagination
          layout="total, sizes, prev, pager, next"
          :current-page="currentPage"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          background
          @current-change="goToPage"
          @size-change="changePageSize"
        />
      </div>
    </div>

    <EmptyState v-else description="选择查询条件后点击「查询」" />
  </div>
</template>

<script setup>
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import QueryForm from '@/components/history/QueryForm.vue'
import ResultTable from '@/components/history/ResultTable.vue'
import { useHistoryQuery } from '@/composables/useHistoryQuery'

const {
  form,
  deviceOptions,
  hasSearched,
  records,
  total,
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
  margin-top: var(--grid-gutter);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px solid var(--color-border-light);
}

.result-count {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.result-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--grid-gutter);
}
</style>