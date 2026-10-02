<template>
  <div class="alert-list-page page">
    <PageHeader title="告警列表" desc="查看并处理本人设备的阈值 / 离线 / 事件告警">
      <template #actions>
        <el-button :loading="loading" @click="refresh">
          <svg-icon name="refresh" :size="14" />
          刷新
        </el-button>
      </template>
    </PageHeader>

    <div class="filter-bar">
      <el-select
        :model-value="filters.status"
        placeholder="全部状态"
        clearable
        class="filter-item"
        @update:model-value="setStatus"
      >
        <el-option
          v-for="option in STATUS_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-select
        :model-value="filters.sourceType"
        placeholder="全部来源"
        clearable
        class="filter-item"
        @update:model-value="(value) => setFilter('sourceType', value || '')"
      >
        <el-option
          v-for="option in SOURCE_TYPE_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-select
        :model-value="filters.severity"
        placeholder="全部级别"
        clearable
        class="filter-item"
        @update:model-value="(value) => setFilter('severity', value || '')"
      >
        <el-option
          v-for="option in SEVERITY_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-checkbox
        :model-value="openOnly"
        class="filter-open-only"
        @update:model-value="setOpenOnly"
      >
        仅看未恢复
      </el-checkbox>
      <div class="filter-actions">
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </div>

    <div v-if="loading && records.length === 0" class="loading-overlay">加载中...</div>

    <EmptyState
      v-else-if="records.length === 0"
      description="暂无告警记录"
    />

    <template v-else>
      <div class="alert-table card">
        <el-table :data="records" style="width: 100%" :row-class-name="rowClassName">
          <el-table-column label="设备" min-width="130">
            <template #default="{ row }">
              <router-link class="alert-device-link" :to="`/workbench/devices/${row.deviceId}`">
                {{ row.deviceKey }}
              </router-link>
            </template>
          </el-table-column>
          <el-table-column prop="ruleName" label="规则" min-width="140" />
          <el-table-column label="来源" width="90">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">
                {{ SOURCE_TYPE_LABELS[row.sourceType] || row.sourceType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="级别" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="SEVERITY_TAG_TYPES[row.severity] || 'info'">
                {{ SEVERITY_LABELS[row.severity] || row.severity }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <span :class="['alert-status', `alert-status--${row.status.toLowerCase()}`]">
                {{ STATUS_LABELS[row.status] || row.status }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="告警内容" min-width="180" show-overflow-tooltip />
          <el-table-column label="触发次数" width="90" align="center">
            <template #default="{ row }">{{ row.triggerCount }}</template>
          </el-table-column>
          <el-table-column label="最近触发" width="170">
            <template #default="{ row }">{{ formatDateTime(row.lastTriggeredAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button
                v-if="row.status === 'TRIGGERED'"
                link
                type="primary"
                @click="handleAck(row)"
              >
                确认
              </el-button>
              <el-button
                v-if="row.status !== 'RECOVERED'"
                link
                type="success"
                @click="handleRecover(row)"
              >
                恢复
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <EmptyState description="暂无告警记录" :image-size="80" />
          </template>
        </el-table>
      </div>

      <div class="alert-pagination">
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

    <el-drawer v-model="detailVisible" title="告警详情" size="440px">
      <div v-if="detail" class="alert-detail">
        <div class="detail-row">
          <span class="detail-label">告警内容</span>
          <span class="detail-value">{{ detail.title }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">设备</span>
          <span class="detail-value">{{ detail.deviceKey }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">规则</span>
          <span class="detail-value">{{ detail.ruleName }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">来源</span>
          <span class="detail-value">{{ SOURCE_TYPE_LABELS[detail.sourceType] || detail.sourceType }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">级别</span>
          <span class="detail-value">{{ SEVERITY_LABELS[detail.severity] || detail.severity }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">状态</span>
          <span class="detail-value">{{ STATUS_LABELS[detail.status] || detail.status }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">触发值</span>
          <span class="detail-value detail-value--mono">{{ detail.triggerValue || '—' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">触发次数</span>
          <span class="detail-value">{{ detail.triggerCount }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">首次触发</span>
          <span class="detail-value">{{ formatDateTime(detail.firstTriggeredAt) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">最近触发</span>
          <span class="detail-value">{{ formatDateTime(detail.lastTriggeredAt) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">最近通知</span>
          <span class="detail-value">{{ formatDateTime(detail.notifiedAt) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">确认时间</span>
          <span class="detail-value">{{ formatDateTime(detail.acknowledgedAt) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">恢复时间</span>
          <span class="detail-value">{{ formatDateTime(detail.recoveredAt) }}</span>
        </div>
      </div>

      <template #footer>
        <el-button
          v-if="detail && detail.status === 'TRIGGERED'"
          :loading="acknowledging"
          @click="handleAck(detail)"
        >
          确认
        </el-button>
        <el-button
          v-if="detail && detail.status !== 'RECOVERED'"
          type="success"
          :loading="recovering"
          @click="handleRecover(detail)"
        >
          恢复
        </el-button>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { alertApi } from '@/api/alert'
import { unwrapResult } from '@/utils/result'
import {
  useAlertListQuery,
  useAckAlertMutation,
  useRecoverAlertMutation
} from '@/composables/useAlerts'
import {
  SOURCE_TYPE_LABELS,
  SOURCE_TYPE_OPTIONS,
  SEVERITY_LABELS,
  SEVERITY_TAG_TYPES,
  SEVERITY_OPTIONS,
  STATUS_LABELS,
  STATUS_OPTIONS,
  formatDateTime
} from '@/utils/alert'

const {
  filters,
  openOnly,
  currentPage,
  pageSize,
  records,
  total,
  loading,
  setStatus,
  setOpenOnly,
  setFilter,
  resetFilters,
  goToPage,
  changePageSize,
  refresh
} = useAlertListQuery()

const { acknowledging, ackAlert } = useAckAlertMutation()
const { recovering, recoverAlert } = useRecoverAlertMutation()

const route = useRoute()
const detailVisible = ref(false)
const detail = ref(null)

function rowClassName({ row }) {
  return row.status === 'RECOVERED' ? 'alert-row--recovered' : 'alert-row--open'
}

function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

/** 顶栏通知点击跳转携带 alertId，据此拉取完整记录并打开详情抽屉 */
async function openDetailById(id) {
  try {
    const record = unwrapResult(await alertApi.getDetail(id), null)
    if (record) openDetail(record)
  } catch {
    // 记录不存在或越权由 axios 拦截器统一提示
  }
}

onMounted(() => {
  if (route.query.alertId) openDetailById(route.query.alertId)
})

watch(
  () => route.query.alertId,
  (id) => {
    if (id) openDetailById(id)
  }
)

async function handleAck(row) {
  try {
    await ackAlert(row.id)
    ElMessage.success('告警已确认')
  } catch {
    // 失败原因由 axios 拦截器统一提示
  }
}

async function handleRecover(row) {
  try {
    await ElMessageBox.confirm('确认将该告警标记为已恢复？', '人工置恢复', {
      confirmButtonText: '恢复',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await recoverAlert(row.id)
    ElMessage.success('告警已恢复')
    if (detail.value?.id === row.id) detailVisible.value = false
  } catch {
    // 失败原因由拦截器提示
  }
}
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
  width: 160px;
}

.filter-open-only {
  height: 32px;
}

.filter-actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-left: auto;
}

.alert-table {
  padding: var(--spacing-sm);
}

.alert-table :deep(.alert-row--open) td {
  background: var(--color-danger-bg, rgba(245, 108, 108, 0.06));
}

.alert-device-link {
  color: var(--color-primary);
  text-decoration: none;
}

.alert-device-link:hover {
  text-decoration: underline;
}

.alert-status {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
}

.alert-status--triggered {
  color: var(--color-danger, #f56c6c);
}

.alert-status--acknowledged {
  color: var(--color-warning, #e6a23c);
}

.alert-status--recovered {
  color: var(--color-text-tertiary);
}

.alert-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--grid-gutter);
}

.alert-detail {
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
</style>