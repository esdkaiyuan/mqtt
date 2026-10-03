<template>
  <el-drawer v-model="visible" title="批量操作结果" size="560px">
    <template v-if="result">
      <div class="batch-summary">
        <span class="batch-summary__item">目标 {{ result.total }} 台</span>
        <span class="batch-summary__item batch-summary__item--ok">成功 {{ result.succeeded }} 台</span>
        <span class="batch-summary__item batch-summary__item--fail">失败 {{ result.failed }} 台</span>
      </div>

      <el-table :data="result.items" style="width: 100%">
        <el-table-column label="设备" min-width="150">
          <template #default="{ row }">
            <div class="batch-device">
              <span class="batch-device__name">{{ row.deviceName || row.deviceKey }}</span>
              <span class="batch-device__key">{{ row.deviceKey }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.success ? 'success' : 'danger'">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="160">
          <template #default="{ row }">
            <span v-if="row.success" class="batch-status">{{ row.status || '已完成' }}</span>
            <span v-else class="batch-error">{{ row.error || '操作失败' }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState description="暂无批量结果" :image-size="80" />
        </template>
      </el-table>
    </template>

    <EmptyState v-else description="暂无批量结果" :image-size="80" />
  </el-drawer>
</template>

<script setup>
import EmptyState from '@/components/common/EmptyState.vue'

defineProps({
  result: {
    type: Object,
    default: null
  }
})

const visible = defineModel('visible', { type: Boolean })
</script>

<style scoped>
.batch-summary {
  display: flex;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
}

.batch-summary__item--ok {
  color: var(--color-success);
}

.batch-summary__item--fail {
  color: var(--color-danger);
}

.batch-device {
  display: flex;
  flex-direction: column;
}

.batch-device__name {
  color: var(--color-text-primary);
}

.batch-device__key {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.batch-status {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-regular);
}

.batch-error {
  font-size: var(--font-size-xs);
  color: var(--color-danger);
}
</style>