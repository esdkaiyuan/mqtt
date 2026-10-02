<template>
  <el-table
    v-loading="loading"
    :data="records"
    class="result-table"
    row-key="id"
    empty-text="暂无数据"
  >
    <el-table-column label="时间" width="180">
      <template #default="{ row }">{{ formatTime(row.timestamp) }}</template>
    </el-table-column>
    <el-table-column label="设备" width="180">
      <template #default="{ row }">{{ row.deviceName || row.deviceId }}</template>
    </el-table-column>
    <el-table-column label="Topic" min-width="200">
      <template #default="{ row }">
        <span class="topic-cell">{{ row.topic }}</span>
      </template>
    </el-table-column>
    <el-table-column label="数据" min-width="280">
      <template #default="{ row }">
        <pre class="data-payload">{{ formatPayload(row.payload) }}</pre>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
defineProps({
  records: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

function formatTime(timeStr) {
  if (!timeStr) return '-'
  return new Date(timeStr).toLocaleString('zh-CN')
}

function formatPayload(payload) {
  if (!payload) return ''
  try {
    const obj = JSON.parse(payload)
    return JSON.stringify(obj, null, 2)
  } catch {
    return payload
  }
}
</script>

<style scoped>
.result-table {
  width: 100%;
}

.topic-cell {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.data-payload {
  font-size: var(--font-size-xs);
  font-family: var(--font-family-mono);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 120px;
  overflow-y: auto;
  margin: 0;
}
</style>