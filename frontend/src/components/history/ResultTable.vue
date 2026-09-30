<template>
  <div class="result-body">
    <div v-if="loading" class="loading-overlay">查询中...</div>

    <div v-else-if="records.length === 0" class="empty-state">
      <svg-icon name="history" :size="48" color="#E0E0E0" />
      <p class="empty-state-text">暂无数据</p>
    </div>

    <div v-else class="history-table-wrapper card">
      <table class="history-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>设备</th>
            <th>Topic</th>
            <th>数据</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in records" :key="record.id">
            <td class="time-cell">{{ formatTime(record.timestamp) }}</td>
            <td class="device-cell">{{ record.deviceName || record.deviceId }}</td>
            <td class="topic-cell">{{ record.topic }}</td>
            <td class="data-cell">
              <pre class="data-payload">{{ formatPayload(record.payload) }}</pre>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import SvgIcon from '@/components/Icon.vue'

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
.empty-state-text {
  font-size: var(--font-size-sm);
  color: var(--color-gray-text);
  margin-top: var(--spacing-sm);
}

.history-table-wrapper {
  overflow-x: auto;
  padding: 0;
}

.history-table {
  width: 100%;
  border-collapse: collapse;
}

.history-table th {
  background: var(--color-gray-light);
  padding: 12px 16px;
  text-align: left;
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-gray-dark);
  border-bottom: 1px solid var(--color-border-color);
}

.history-table td {
  padding: 12px 16px;
  font-size: var(--font-size-sm);
  color: var(--color-gray-dark);
  border-bottom: 1px solid var(--border-color-light);
}

.history-table tr:hover td {
  background: var(--color-gray-light);
}

.time-cell {
  white-space: nowrap;
  color: var(--color-gray-text);
}

.device-cell {
  white-space: nowrap;
}

.topic-cell {
  font-family: 'Courier New', monospace;
  font-size: var(--font-size-xs);
}

.data-cell {
  max-width: 400px;
}

.data-payload {
  font-size: var(--font-size-xs);
  font-family: 'Courier New', monospace;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 120px;
  overflow-y: auto;
  margin: 0;
}
</style>
