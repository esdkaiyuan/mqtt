<template>
  <div class="device-property-panel">
    <h3 class="card-title">属性</h3>
    <p class="card-desc">物模型解析出的属性最新值，每个标识符仅保留最近一次上报</p>

    <el-table v-loading="loading" :data="properties" border stripe size="small">
      <el-table-column prop="identifier" label="标识符" min-width="150">
        <template #default="{ row }">
          <span class="device-property-panel__mono">{{ row.identifier }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="dataType" label="数据类型" width="120" />
      <el-table-column label="当前值" min-width="160">
        <template #default="{ row }">
          <span class="device-property-panel__value">{{ row.valueText }}</span>
        </template>
      </el-table-column>
      <el-table-column label="上报时间" min-width="180">
        <template #default="{ row }">{{ formatTime(row.reportedAt) }}</template>
      </el-table-column>
      <template #empty>
        <EmptyState description="暂无属性数据，设备按物模型上报后自动出现" :image-size="80" />
      </template>
    </el-table>
  </div>
</template>

<script setup>
import EmptyState from '@/components/common/EmptyState.vue'

defineProps({
  properties: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

function formatTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN')
}
</script>

<style scoped>
.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-sm);
}

.card-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-bottom: var(--spacing-md);
}

.device-property-panel__mono {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.device-property-panel__value {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-primary);
}
</style>