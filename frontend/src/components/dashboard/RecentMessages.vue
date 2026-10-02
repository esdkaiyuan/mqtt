<template>
  <div class="recent-messages card">
    <div class="recent-header">
      <h3 class="chart-title">最近消息</h3>
      <router-link to="/workbench/messages" class="btn-link">查看全部</router-link>
    </div>
    <div v-if="messages.length === 0" class="empty-state-small">
      <svg-icon name="message" :size="32" color="var(--color-border)" />
      <p>暂无消息数据</p>
    </div>
    <div v-else class="recent-table-wrapper">
      <table class="recent-table">
        <thead>
          <tr>
            <th>Topic</th>
            <th>方向</th>
            <th>载荷预览</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="msg in messages" :key="msg.id">
            <td class="topic-cell">{{ msg.topic }}</td>
            <td>
              <span :class="['direction-badge', msg.direction?.toLowerCase()]">
                {{ msg.direction === 'PUBLISH' ? '发送' : '接收' }}
              </span>
            </td>
            <td class="payload-cell">{{ truncatePayload(msg.payload) }}</td>
            <td class="time-cell">{{ formatTime(msg.receivedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import SvgIcon from '@/components/Icon.vue'

defineProps({
  messages: {
    type: Array,
    default: () => []
  }
})

function formatTime(timeStr) {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function truncatePayload(payload) {
  if (!payload) return ''
  try {
    const obj = JSON.parse(payload)
    payload = JSON.stringify(obj)
  } catch {
    // 非 JSON 载荷按原文展示
  }
  return payload.length > 50 ? payload.slice(0, 50) + '...' : payload
}
</script>

<style scoped>
.recent-messages {
  padding: var(--spacing-lg);
}

.recent-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.chart-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.btn-link {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
  text-decoration: none;
}

.btn-link:hover {
  color: var(--color-primary-hover);
}

.recent-table-wrapper {
  overflow-x: auto;
}

.recent-table {
  width: 100%;
  border-collapse: collapse;
}

.recent-table th {
  background: var(--color-bg);
  padding: 10px 14px;
  text-align: left;
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--color-text-tertiary);
  border-bottom: 1px solid var(--border-color-light);
}

.recent-table td {
  padding: 12px 14px;
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
  border-bottom: 1px solid var(--border-color-light);
}

.recent-table tbody tr:hover td {
  background: var(--color-bg);
}

.topic-cell {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-primary);
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.payload-cell {
  max-width: 250px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.time-cell {
  white-space: nowrap;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.direction-badge {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: var(--font-size-xs);
  font-weight: 500;
}

.direction-badge.publish {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.direction-badge.subscribe {
  background: var(--color-bg);
  color: var(--color-text-tertiary);
}

.empty-state-small {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-xl);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
