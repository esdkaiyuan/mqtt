<template>
  <div class="device-log-timeline">
    <div v-loading="loading" class="device-log-timeline__body">
      <el-timeline v-if="items.length">
        <el-timeline-item
          v-for="item in items"
          :key="item.logId"
          :type="nodeType(item.logType)"
          :hollow="item.correlationRole === 'REPLY'"
          :timestamp="formatTime(item.occurredAt)"
          placement="top"
        >
          <div class="log-item">
            <div class="log-item__head">
              <el-tag :type="typeTag(item.logType)" size="small" effect="plain">
                {{ typeLabel(item.logType) }}
              </el-tag>
              <span class="log-item__title">{{ item.title || item.logId }}</span>
              <el-tag
                v-if="item.correlationRole === 'REPLY'"
                type="success"
                size="small"
                effect="plain"
              >
                回执
              </el-tag>
              <span v-if="item.direction" class="log-item__direction">{{ item.direction }}</span>
            </div>

            <div v-if="facts(item).length" class="log-item__facts">
              <span v-for="fact in facts(item)" :key="fact.label" class="log-item__fact">
                <span class="log-item__fact-label">{{ fact.label }}</span>
                <span class="log-item__fact-value">{{ fact.value }}</span>
              </span>
            </div>

            <pre v-if="hasPayload(item)" class="log-item__payload">{{ pretty(item.payload) }}</pre>

            <div v-if="item.errorMessage" class="log-item__error">
              失败原因：{{ item.errorMessage }}
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>

      <EmptyState v-else description="当前筛选范围内没有日志" />
    </div>

    <div v-if="total > 0" class="device-log-timeline__pagination">
      <el-pagination
        layout="total, prev, pager, next"
        :current-page="page"
        :page-size="size"
        :total="total"
        background
        @current-change="$emit('page-change', $event)"
      />
    </div>
  </div>
</template>

<script setup>
import EmptyState from '@/components/common/EmptyState.vue'

defineProps({
  items: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  page: { type: Number, default: 1 },
  size: { type: Number, default: 20 }
})

defineEmits(['page-change'])

const TYPE_LABEL = { MESSAGE: '报文', COMMAND: '命令', EVENT: '事件', STATUS: '状态' }
const TYPE_TAG = { MESSAGE: 'info', COMMAND: 'primary', EVENT: 'warning', STATUS: 'success' }
const CALL_TYPE_LABEL = { sync: '同步', async: '异步' }

function typeLabel(logType) {
  return TYPE_LABEL[logType] || logType || '未知'
}

function typeTag(logType) {
  return TYPE_TAG[logType] || 'info'
}

/** 时间轴节点颜色与类型标签保持一致。 */
function nodeType(logType) {
  return TYPE_TAG[logType] || 'info'
}

/** 罗列该条目携带的来源专属字段，缺失的来源自然不会有对应项。 */
function facts(item) {
  const out = []
  if (item.identifier) out.push({ label: '标识符', value: item.identifier })
  if (item.topic) out.push({ label: 'Topic', value: item.topic })
  if (item.status) out.push({ label: '状态', value: item.status })
  if (item.callType) {
    out.push({ label: '调用', value: CALL_TYPE_LABEL[item.callType] || item.callType })
  }
  if (item.qos !== null && item.qos !== undefined) out.push({ label: 'QoS', value: String(item.qos) })
  if (item.commandId) out.push({ label: '命令 ID', value: item.commandId })
  if (item.attemptCount !== null && item.attemptCount !== undefined) {
    out.push({ label: '重试', value: String(item.attemptCount) })
  }
  return out
}

function hasPayload(item) {
  return item.payload !== null && item.payload !== undefined && item.payload !== ''
}

function formatTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN')
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
.device-log-timeline__body {
  min-height: 120px;
}

.log-item {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.log-item__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
}

.log-item__title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  word-break: break-all;
}

.log-item__direction {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.log-item__facts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-xs) var(--spacing-md);
}

.log-item__fact {
  display: inline-flex;
  align-items: baseline;
  gap: var(--spacing-xs);
  font-size: var(--font-size-xs);
}

.log-item__fact-label {
  color: var(--color-text-tertiary);
}

.log-item__fact-value {
  font-family: var(--font-family-mono);
  color: var(--color-text-regular);
  word-break: break-all;
}

.log-item__payload {
  margin: 0;
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-bg);
  border-radius: var(--border-radius-sm);
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-regular);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 220px;
  overflow: auto;
}

.log-item__error {
  font-size: var(--font-size-xs);
  color: var(--color-danger);
  word-break: break-all;
}

.device-log-timeline__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--spacing-md);
}
</style>
