<template>
  <el-drawer
    :model-value="modelValue"
    title="执行详情"
    size="560px"
    :close-on-click-modal="false"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <div v-if="loading && !execution" class="execution-drawer__loading">加载中...</div>

    <EmptyState v-else-if="!execution" description="暂无执行详情" :image-size="80" />

    <div v-else class="execution-drawer">
      <section class="execution-drawer__section">
        <h4 class="execution-drawer__title">触发快照</h4>

        <div class="detail-row">
          <span class="detail-label">场景</span>
          <span class="detail-value">{{ execution.sceneName || '—' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">触发源</span>
          <span class="detail-value">
            <el-tag
              size="small"
              effect="plain"
              :type="TRIGGER_SOURCE_TAG_TYPES[execution.triggerType] || 'info'"
            >
              {{ TRIGGER_SOURCE_LABELS[execution.triggerType] || execution.triggerType || '—' }}
            </el-tag>
          </span>
        </div>
        <div class="detail-row">
          <span class="detail-label">触发方式</span>
          <span class="detail-value">
            {{ EXECUTION_TRIGGER_SOURCE_LABELS[execution.triggerSource] || execution.triggerSource || '—' }}
          </span>
        </div>
        <div class="detail-row">
          <span class="detail-label">触发设备</span>
          <span class="detail-value">{{ execution.triggerDeviceName || execution.triggerDeviceKey || '—' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">触发标识符</span>
          <span class="detail-value detail-value--mono">{{ execution.triggerIdentifier || '—' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">触发值</span>
          <span class="detail-value detail-value--mono">{{ execution.triggerValue ?? '—' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">状态</span>
          <span class="detail-value">
            <el-tag size="small" :type="EXECUTION_STATUS_TAG_TYPES[execution.status] || 'info'">
              {{ EXECUTION_STATUS_LABELS[execution.status] || execution.status || '—' }}
            </el-tag>
          </span>
        </div>
        <div class="detail-row">
          <span class="detail-label">步骤进度</span>
          <span class="detail-value">{{ execution.finishedSteps ?? 0 }} / {{ execution.totalSteps ?? 0 }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">开始时间</span>
          <span class="detail-value">{{ formatDateTime(execution.startedAt) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">结束时间</span>
          <span class="detail-value">{{ formatDateTime(execution.finishedAt) }}</span>
        </div>
        <div v-if="execution.errorMessage" class="detail-block">
          <span class="detail-label">失败原因</span>
          <span class="detail-error">{{ execution.errorMessage }}</span>
        </div>
      </section>

      <section class="execution-drawer__section">
        <h4 class="execution-drawer__title">步骤时间线</h4>

        <EmptyState v-if="steps.length === 0" description="暂无步骤明细" :image-size="80" />

        <el-timeline v-else class="step-timeline">
          <el-timeline-item
            v-for="step in steps"
            :key="step.id"
            :type="STEP_STATUS_TAG_TYPES[step.status] || 'info'"
            :timestamp="formatDateTime(step.scheduledAt || step.startedAt)"
            placement="top"
          >
            <div class="step-card">
              <div class="step-card__head">
                <span class="step-card__seq">第 {{ step.seq }} 步</span>
                <span class="step-card__action">
                  {{ ACTION_TYPE_LABELS[step.actionType] || step.actionType || '—' }}
                </span>
                <el-tag size="small" :type="STEP_STATUS_TAG_TYPES[step.status] || 'info'">
                  {{ STEP_STATUS_LABELS[step.status] || step.status }}
                </el-tag>
              </div>

              <div class="step-card__meta">
                <span>延时 {{ step.delaySeconds ?? 0 }}s</span>
                <span>尝试 {{ step.attemptCount ?? 0 }} 次</span>
                <span>耗时 {{ formatDuration(step.startedAt, step.finishedAt) }}</span>
              </div>
              <div class="step-card__times">
                <span>开始 {{ formatDateTime(step.startedAt) }}</span>
                <span>结束 {{ formatDateTime(step.finishedAt) }}</span>
              </div>

              <div v-if="step.errorMessage" class="step-card__error">{{ step.errorMessage }}</div>

              <div v-if="step.forwardPayload" class="step-card__payload">
                <span class="detail-label">载荷快照</span>
                <pre class="detail-payload">{{ step.forwardPayload }}</pre>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </section>
    </div>

    <template #footer>
      <el-button :loading="loading" @click="refresh">
        <svg-icon name="refresh" :size="14" />
        刷新
      </el-button>
      <el-button
        v-if="execution && execution.status === 'FAILED'"
        type="primary"
        :loading="retrying"
        @click="handleRetry"
      >
        手动重试
      </el-button>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SvgIcon from '@/components/Icon.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import {
  useSceneExecutionDetailQuery,
  useSceneExecutionRetryMutation
} from '@/composables/useSceneExecutions'
import {
  TRIGGER_SOURCE_LABELS,
  TRIGGER_SOURCE_TAG_TYPES,
  EXECUTION_STATUS_LABELS,
  EXECUTION_STATUS_TAG_TYPES,
  STEP_STATUS_LABELS,
  STEP_STATUS_TAG_TYPES,
  ACTION_TYPE_LABELS,
  formatDateTime
} from '@/utils/scene'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  executionId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue', 'retried'])

/** 触发方式（AUTO/MANUAL）与「触发源」（PROPERTY/EVENT/TIMER）不同口径，单独映射。 */
const EXECUTION_TRIGGER_SOURCE_LABELS = { AUTO: '自动', MANUAL: '手动' }

const executionId = computed(() => props.executionId)
const { execution, steps, loading, refresh } = useSceneExecutionDetailQuery(executionId)
const { retrying, retryExecution } = useSceneExecutionRetryMutation()

/** 耗时：起止时刻差值，缺失或异常回退占位符。 */
function formatDuration(start, end) {
  if (!start || !end) return '—'
  const ms = new Date(end).getTime() - new Date(start).getTime()
  if (Number.isNaN(ms) || ms < 0) return '—'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

async function handleRetry() {
  if (!execution.value) return
  try {
    await ElMessageBox.confirm('确认手动重试该执行记录？将立即重新执行未完成步骤。', '手动重试', {
      confirmButtonText: '重试',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await retryExecution(execution.value.id)
    ElMessage.success('已提交重试')
    emit('retried')
  } catch {
    // 失败原因由拦截器提示（非 FAILED 记录会返回 409）
  }
}
</script>

<style scoped>
.execution-drawer__loading {
  padding: var(--spacing-lg);
  text-align: center;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.execution-drawer {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.execution-drawer__title {
  margin: 0 0 var(--spacing-sm);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-text-primary);
}

.execution-drawer__section {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.detail-row {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-md);
  font-size: var(--font-size-sm);
}

.detail-block {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  font-size: var(--font-size-sm);
}

.detail-label {
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

.detail-value {
  color: var(--color-text-primary);
  text-align: right;
  word-break: break-all;
}

.detail-value--mono {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.detail-error {
  color: var(--color-danger);
  word-break: break-all;
}

.step-timeline {
  padding-left: var(--spacing-xs);
}

.step-card {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.step-card__head {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.step-card__seq {
  font-weight: 600;
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
}

.step-card__action {
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
}

.step-card__meta,
.step-card__times {
  display: flex;
  gap: var(--spacing-md);
  flex-wrap: wrap;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.step-card__error {
  font-size: var(--font-size-xs);
  color: var(--color-danger);
  word-break: break-all;
}

.step-card__payload {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.detail-payload {
  margin: 0;
  padding: var(--spacing-sm);
  max-height: 200px;
  overflow: auto;
  background: var(--color-bg);
  border-radius: var(--border-radius-sm);
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-regular);
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
