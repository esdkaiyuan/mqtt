<template>
  <div class="rule-executions">
    <div class="filter-bar">
      <el-select
        :model-value="filters.ruleId"
        placeholder="全部规则"
        clearable
        filterable
        class="filter-item filter-item--wide"
        @update:model-value="(value) => setFilter('ruleId', value)"
      >
        <el-option
          v-for="rule in rules"
          :key="rule.id"
          :label="rule.name"
          :value="rule.id"
        />
      </el-select>
      <el-select
        :model-value="filters.deviceId"
        placeholder="全部设备"
        clearable
        filterable
        class="filter-item"
        @update:model-value="(value) => setFilter('deviceId', value)"
      >
        <el-option
          v-for="device in devices"
          :key="device.id"
          :label="device.deviceName || device.deviceKey"
          :value="device.id"
        />
      </el-select>
      <el-select
        :model-value="filters.status"
        placeholder="全部状态"
        clearable
        class="filter-item"
        @update:model-value="(value) => setFilter('status', value)"
      >
        <el-option
          v-for="option in EXECUTION_STATUS_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <div class="filter-actions">
        <el-button @click="resetFilters">重置</el-button>
        <el-button :loading="loading" @click="refresh">
          <svg-icon name="refresh" :size="14" />
          刷新
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="hasPending"
      class="pending-hint"
      type="info"
      :closable="false"
      show-icon
      title="存在待执行记录，可点击「刷新」查看最新状态"
    />

    <div v-if="loading && records.length === 0" class="loading-overlay">加载中...</div>

    <EmptyState
      v-else-if="records.length === 0"
      description="暂无执行记录"
    />

    <template v-else>
      <div class="execution-table card">
        <el-table :data="records" style="width: 100%">
          <el-table-column prop="ruleName" label="规则" min-width="130" />
          <el-table-column label="设备" min-width="120">
            <template #default="{ row }">
              <router-link class="execution-device-link" :to="`/workbench/devices/${row.deviceId}`">
                {{ row.deviceKey }}
              </router-link>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="80">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">
                {{ SOURCE_TYPE_LABELS[row.sourceType] || row.sourceType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="触发值" min-width="120">
            <template #default="{ row }">
              <span class="execution-mono">{{ row.triggerValue || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="动作" width="130">
            <template #default="{ row }">
              {{ ACTION_TYPE_LABELS[row.actionType] || row.actionType }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="EXECUTION_STATUS_TAG_TYPES[row.status] || 'info'">
                {{ EXECUTION_STATUS_LABELS[row.status] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="尝试" width="70" align="center">
            <template #default="{ row }">{{ row.attemptCount }}</template>
          </el-table-column>
          <el-table-column label="失败原因" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorMessage || '—' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button
                v-if="row.status === 'FAILED'"
                link
                type="primary"
                :loading="retryingId === row.id"
                @click="handleRetry(row)"
              >
                重试
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <EmptyState description="暂无执行记录" :image-size="80" />
          </template>
        </el-table>
      </div>

      <div class="execution-pagination">
        <el-pagination
          layout="total, sizes, prev, pager, next"
          :current-page="currentPage"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          background
          @current-change="goToPage"
          @size-change="changePageSize"
        />
      </div>
    </template>

    <el-drawer v-model="detailVisible" title="执行记录详情" size="480px">
      <div v-if="detail" class="execution-detail">
        <div class="detail-row">
          <span class="detail-label">规则</span>
          <span class="detail-value">{{ detail.ruleName }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">设备</span>
          <span class="detail-value">{{ detail.deviceName || detail.deviceKey }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">来源</span>
          <span class="detail-value">{{ SOURCE_TYPE_LABELS[detail.sourceType] || detail.sourceType }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">触发标识符</span>
          <span class="detail-value detail-value--mono">{{ detail.identifier }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">触发值</span>
          <span class="detail-value detail-value--mono">{{ detail.triggerValue || '—' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">动作</span>
          <span class="detail-value">{{ ACTION_TYPE_LABELS[detail.actionType] || detail.actionType }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">状态</span>
          <span class="detail-value">{{ EXECUTION_STATUS_LABELS[detail.status] || detail.status }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">尝试次数</span>
          <span class="detail-value">{{ detail.attemptCount }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">下次重试</span>
          <span class="detail-value">{{ formatDateTime(detail.nextAttemptAt) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">失败原因</span>
          <span class="detail-value">{{ detail.errorMessage || '—' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">创建时间</span>
          <span class="detail-value">{{ formatDateTime(detail.createdAt) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">结束时间</span>
          <span class="detail-value">{{ formatDateTime(detail.finishedAt) }}</span>
        </div>
        <div class="detail-block">
          <span class="detail-label">转发载荷快照</span>
          <pre class="detail-payload">{{ detail.forwardPayload || '（无）' }}</pre>
        </div>
      </div>

      <template #footer>
        <el-button
          v-if="detail && detail.status === 'FAILED'"
          type="primary"
          :loading="retryingId === detail.id"
          @click="handleRetry(detail)"
        >
          手动重试
        </el-button>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SvgIcon from '@/components/Icon.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { deviceApi } from '@/api/device'
import { ruleApi } from '@/api/rule'
import { unwrapResult } from '@/utils/result'
import { useRuleExecutionsQuery, useExecutionRetryMutation } from '@/composables/useRules'
import {
  SOURCE_TYPE_LABELS,
  ACTION_TYPE_LABELS,
  EXECUTION_STATUS_LABELS,
  EXECUTION_STATUS_TAG_TYPES,
  EXECUTION_STATUS_OPTIONS,
  formatDateTime
} from '@/utils/rule'

const {
  filters,
  currentPage,
  pageSize,
  records,
  total,
  loading,
  hasPending,
  setFilter,
  resetFilters,
  goToPage,
  changePageSize,
  refresh
} = useRuleExecutionsQuery()

const { retryExecution } = useExecutionRetryMutation()

const rules = ref([])
const devices = ref([])
const detailVisible = ref(false)
const detail = ref(null)
const retryingId = ref(null)

function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

async function handleRetry(row) {
  try {
    await ElMessageBox.confirm('确认手动重试该执行记录？将立即重新执行动作。', '手动重试', {
      confirmButtonText: '重试',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  retryingId.value = row.id
  try {
    await retryExecution(row.id)
    ElMessage.success('已提交重试')
    if (detail.value?.id === row.id) detailVisible.value = false
  } catch {
    // 失败原因由拦截器提示（非 FAILED 记录会返回 409）
  } finally {
    retryingId.value = null
  }
}

async function loadOptions() {
  try {
    const page = unwrapResult(await ruleApi.listRules({ pageNum: 1, pageSize: 100 }), { records: [] })
    rules.value = page.records || []
  } catch {
    rules.value = []
  }
  try {
    const page = unwrapResult(await deviceApi.getList({ page: 1, size: 200 }), { records: [] })
    devices.value = page.records || []
  } catch {
    devices.value = []
  }
}

onMounted(loadOptions)
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--grid-gutter);
  padding: var(--spacing-md);
  flex-wrap: wrap;
  background: var(--color-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius);
}

.filter-item {
  width: 150px;
}

.filter-item--wide {
  width: 200px;
}

.filter-actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-left: auto;
}

.pending-hint {
  margin-bottom: var(--grid-gutter);
}

.execution-table {
  padding: var(--spacing-sm);
}

.execution-mono {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-sm);
}

.execution-device-link {
  color: var(--color-primary);
  text-decoration: none;
}

.execution-device-link:hover {
  text-decoration: underline;
}

.execution-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--grid-gutter);
}

.execution-detail {
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

.detail-payload {
  margin: 0;
  padding: var(--spacing-sm);
  max-height: 240px;
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