<template>
  <div class="device-command-history">
    <h3 class="card-title">命令记录</h3>
    <p class="card-desc">每次下发的完整生命周期，按创建时间倒序</p>

    <el-table v-loading="loading" :data="records" border stripe size="small">
      <el-table-column label="时间" min-width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ typeLabel(row.commandType) }}</template>
      </el-table-column>
      <el-table-column label="标识符" min-width="140">
        <template #default="{ row }">
          <span class="device-command-history__mono">{{ row.identifier || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="100">
        <template #default="{ row }">{{ formatDuration(row) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <EmptyState description="暂无命令记录，下发一次命令后自动出现" :image-size="80" />
      </template>
    </el-table>

    <div v-if="total > 0" class="device-command-history__pagination">
      <el-pagination
        layout="prev, pager, next"
        :current-page="page"
        :page-size="size"
        :total="total"
        background
        @current-change="$emit('page-change', $event)"
      />
    </div>

    <el-dialog v-model="detailVisible" title="命令详情" width="560px">
      <div v-if="detail" class="command-detail">
        <div class="command-detail__row">
          <span class="command-detail__label">命令 ID</span>
          <span class="command-detail__mono">{{ detail.commandId }}</span>
        </div>
        <div class="command-detail__row">
          <span class="command-detail__label">状态</span>
          <el-tag :type="statusTag(detail.status)" size="small">{{ detail.status }}</el-tag>
        </div>
        <div class="command-detail__row">
          <span class="command-detail__label">调用方式</span>
          <span>{{ detail.callType === 'sync' ? '同步' : '异步' }}</span>
        </div>
        <div v-if="detail.errorMessage" class="command-detail__row">
          <span class="command-detail__label">失败原因</span>
          <span class="command-detail__error">{{ detail.errorMessage }}</span>
        </div>
        <div class="command-detail__block">
          <span class="command-detail__label">请求参数</span>
          <pre class="command-detail__json">{{ pretty(detail.params) }}</pre>
        </div>
        <div class="command-detail__block">
          <span class="command-detail__label">回执结果</span>
          <pre class="command-detail__json">{{ pretty(detail.result) }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import EmptyState from '@/components/common/EmptyState.vue'

defineProps({
  records: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  page: { type: Number, default: 1 },
  size: { type: Number, default: 10 }
})

defineEmits(['page-change'])

const detailVisible = ref(false)
const detail = ref(null)

function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

function statusTag(status) {
  const map = { ACKED: 'success', FAILED: 'danger', TIMEOUT: 'warning', SENT: 'primary' }
  return map[status] || 'info'
}

function typeLabel(type) {
  const map = { property_set: '属性设置', service: '服务调用' }
  return map[type] || type || '—'
}

function formatTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN')
}

/** 耗时取终态时间与创建时间之差；未到终态时无耗时。 */
function formatDuration(row) {
  if (!row.finishedAt || !row.createdAt) return '—'
  const ms = new Date(row.finishedAt) - new Date(row.createdAt)
  if (Number.isNaN(ms) || ms < 0) return '—'
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`
}

/** JSON 文本美化；无法解析时原样透出，避免丢失信息。 */
function pretty(value) {
  if (value === null || value === undefined || value === '') return '—'
  try {
    return JSON.stringify(typeof value === 'string' ? JSON.parse(value) : value, null, 2)
  } catch {
    return String(value)
  }
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

.device-command-history__mono {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.device-command-history__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--spacing-md);
}

.command-detail {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.command-detail__row {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.command-detail__block {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.command-detail__label {
  flex-shrink: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.command-detail__mono {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  word-break: break-all;
}

.command-detail__error {
  font-size: var(--font-size-sm);
  color: var(--color-danger);
}

.command-detail__json {
  margin: 0;
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-bg);
  border-radius: var(--border-radius-sm);
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-regular);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 240px;
  overflow: auto;
}
</style>