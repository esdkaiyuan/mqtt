<template>
  <div class="upgrade-task-detail-page page">
    <PageHeader :title="title" :desc="desc">
      <template #actions>
        <el-button @click="goBack">返回</el-button>
        <el-button
          type="primary"
          :disabled="!canWrite || !detail || detail.status === 'SUCCESS'"
          :loading="retrying"
          @click="handleRetry"
        >
          <svg-icon name="refresh" :size="16" />
          重投未成功
        </el-button>
      </template>
    </PageHeader>

    <div v-if="loading && !detail" class="loading-overlay">加载中...</div>

    <EmptyState v-else-if="loadError && !detail" description="任务加载失败，请稍后重试">
      <el-button size="small" @click="refetchDetail()">重试</el-button>
    </EmptyState>

    <template v-else-if="detail">
      <div class="stat-grid">
        <div class="stat-card card">
          <span class="stat-card__label">目标总数</span>
          <span class="stat-card__value">{{ detail.totalCount ?? 0 }}</span>
        </div>
        <div class="stat-card card">
          <span class="stat-card__label">已下发</span>
          <span class="stat-card__value">{{ detail.dispatchedCount ?? 0 }}</span>
        </div>
        <div class="stat-card card">
          <span class="stat-card__label">升级成功</span>
          <span class="stat-card__value stat-card__value--success">
            {{ detail.successCount ?? 0 }}
          </span>
        </div>
        <div class="stat-card card">
          <span class="stat-card__label">升级失败</span>
          <span class="stat-card__value stat-card__value--danger">
            {{ detail.failedCount ?? 0 }}
          </span>
        </div>
      </div>

      <div class="detail-meta card">
        <div class="detail-meta__row">
          <span class="detail-meta__label">任务状态</span>
          <el-tag :type="statusTagType(detail.status)" disable-transitions>
            {{ statusText(detail.status) }}
          </el-tag>
        </div>
        <div class="detail-meta__row">
          <span class="detail-meta__label">固件版本</span>
          <span class="detail-meta__value detail-meta__value--mono">
            {{ detail.version || '—' }}
          </span>
        </div>
        <div class="detail-meta__row">
          <span class="detail-meta__label">所属产品</span>
          <span class="detail-meta__value">{{ detail.productName || '—' }}</span>
        </div>
        <div class="detail-meta__row">
          <span class="detail-meta__label">创建时间</span>
          <span class="detail-meta__value">{{ formatDateTime(detail.createdAt) }}</span>
        </div>
      </div>

      <div class="target-snapshot card">
        <h3 class="target-snapshot__title">目标快照</h3>
        <div
          v-for="dimension in targetDimensions"
          :key="dimension.key"
          class="target-snapshot__row"
        >
          <span class="target-snapshot__label">{{ dimension.label }}</span>
          <div class="target-snapshot__items">
            <el-tag
              v-for="item in dimension.items"
              :key="item"
              type="info"
              disable-transitions
              class="target-snapshot__tag"
            >
              {{ item }}
            </el-tag>
          </div>
        </div>
        <p v-if="!targetDimensions.length" class="target-snapshot__empty">
          该任务未记录目标快照
        </p>
      </div>

      <div class="record-table-wrap">
        <div class="record-toolbar">
          <el-select
            v-model="recordStatus"
            class="record-toolbar__filter"
            placeholder="全部状态"
            clearable
          >
            <el-option
              v-for="option in STATUS_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <div class="record-toolbar__actions">
            <el-button :disabled="recordsLoading" @click="reloadRecords">
              <svg-icon name="refresh" :size="16" />
              刷新
            </el-button>
          </div>
        </div>

        <el-table v-loading="recordsLoading" :data="records" border stripe>
          <el-table-column
            prop="deviceName"
            label="设备名称"
            min-width="160"
            show-overflow-tooltip
          />
          <el-table-column label="设备标识" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="record-table__mono">{{ row.deviceKey || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="目标版本" width="130">
            <template #default="{ row }">
              <span class="record-table__mono">{{ row.version || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="进度" min-width="180">
            <template #default="{ row }">
              <el-progress
                :percentage="progressOf(row)"
                :stroke-width="10"
                :status="recordProgressStatus(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="recordStatusTagType(row.status)" disable-transitions>
                {{ recordStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="信息" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.message || '—' }}</template>
          </el-table-column>
          <el-table-column label="最近回传" width="170">
            <template #default="{ row }">{{ formatDateTime(row.lastReportAt) }}</template>
          </el-table-column>
          <template #empty>
            <EmptyState description="暂无升级记录" />
          </template>
        </el-table>

        <div class="record-pagination">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :total="recordsTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            background
            @size-change="handleSizeChange"
          />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { otaApi } from '@/api/ota'
import { productApi } from '@/api/product'
import { useDeviceGroupTreeQuery, useTagListQuery } from '@/composables/useDeviceGroups'
import { unwrapResult } from '@/utils/result'
import { useAuthStore } from '@/stores/auth'

/** 任务状态 → 中文文案。 */
const STATUS_TEXT = {
  RUNNING: '进行中',
  SUCCESS: '成功',
  PARTIAL: '部分成功',
  FAILED: '失败'
}
/** 任务状态 → el-tag 类型（RUNNING 蓝 / SUCCESS 绿 / PARTIAL 橙 / FAILED 红）。 */
const STATUS_TAG_TYPE = {
  RUNNING: 'primary',
  SUCCESS: 'success',
  PARTIAL: 'warning',
  FAILED: 'danger'
}
/** 记录状态 → 中文文案。 */
const RECORD_STATUS_TEXT = {
  PENDING: '待下发',
  DISPATCHED: '已下发',
  DOWNLOADING: '下载中',
  FLASHING: '刷写中',
  SUCCESS: '成功',
  FAILED: '失败',
  TIMEOUT: '超时'
}
/** 记录状态 → el-tag 类型。 */
const RECORD_STATUS_TAG_TYPE = {
  PENDING: 'info',
  DISPATCHED: 'primary',
  DOWNLOADING: 'primary',
  FLASHING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger',
  TIMEOUT: 'info'
}
/** 记录状态过滤下拉（顺序与状态机一致）。 */
const STATUS_OPTIONS = Object.keys(RECORD_STATUS_TEXT).map((value) => ({
  value,
  label: RECORD_STATUS_TEXT[value]
}))

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()

const canWrite = computed(() => authStore.hasRole(['ADMIN', 'OPERATOR']))

const id = computed(() => route.params.id)
const detailQueryKey = computed(() => ['ota-task', id.value])

const detailQuery = useQuery({
  queryKey: detailQueryKey,
  queryFn: async () => unwrapResult(await otaApi.taskDetail(id.value), null)
})
const detail = computed(() => detailQuery.data.value ?? null)
const loading = computed(() => detailQuery.isFetching.value)
const loadError = computed(() => detailQuery.isError.value)
const { refetch: refetchDetail } = detailQuery

const title = computed(() => detail.value?.name || '任务详情')
const desc = computed(() => {
  if (!detail.value) return ''
  const parts = []
  if (detail.value.version) parts.push(`固件 ${detail.value.version}`)
  if (detail.value.productName) parts.push(`产品 ${detail.value.productName}`)
  return parts.join(' · ')
})

const page = ref(1)
const size = ref(10)
const recordStatus = ref('')

const recordsQuery = useQuery({
  queryKey: computed(() => ['ota-task-records', id.value, recordStatus.value, page.value, size.value]),
  queryFn: async () =>
    unwrapResult(
      await otaApi.taskRecords(id.value, {
        status: recordStatus.value || undefined,
        page: page.value,
        size: size.value
      }),
      { records: [], total: 0 }
    )
})
const records = computed(() => recordsQuery.data.value?.records ?? [])
const recordsTotal = computed(() => recordsQuery.data.value?.total ?? 0)
const recordsLoading = computed(() => recordsQuery.isFetching.value)
const reloadRecords = () => recordsQuery.refetch()

/** 目标快照所需的产品 / 分组 / 标签名称映射。 */
const productsQuery = useQuery({
  queryKey: ['ota-product-options'],
  queryFn: async () => unwrapResult(await productApi.getList(), [])
})
const { tree: groupTree } = useDeviceGroupTreeQuery()
const { tags } = useTagListQuery()

const productNames = computed(() =>
  Object.fromEntries((productsQuery.data.value ?? []).map((item) => [item.id, item.productName]))
)

const groupNames = computed(() => {
  const map = {}
  const walk = (nodes) => {
    nodes.forEach((node) => {
      map[node.id] = node.name
      if (node.children?.length) walk(node.children)
    })
  }
  walk(groupTree.value ?? [])
  return map
})

const tagNames = computed(() =>
  Object.fromEntries((tags.value ?? []).map((item) => [item.id, item.name]))
)

/** 把目标快照的四个维度渲染成「已选名称」列表（无用例的维度不展示）。 */
const targetDimensions = computed(() => {
  const target = detail.value?.target
  if (!target) return []
  const dimensions = []
  const productIds = target.productIds ?? []
  const groupIds = target.groupIds ?? []
  const tagIds = target.tagIds ?? []
  const deviceIds = target.deviceIds ?? []

  if (productIds.length) {
    dimensions.push({
      key: 'product',
      label: '产品',
      items: productIds.map((item) => productNames.value[item] || `产品 ${item}`)
    })
  }
  if (groupIds.length) {
    dimensions.push({
      key: 'group',
      label: '分组',
      items: groupIds.map((item) => groupNames.value[item] || `分组 ${item}`)
    })
  }
  if (tagIds.length) {
    dimensions.push({
      key: 'tag',
      label: '标签',
      items: tagIds.map((item) => tagNames.value[item] || `标签 ${item}`)
    })
  }
  if (deviceIds.length) {
    dimensions.push({
      key: 'device',
      label: '手选设备',
      items: [`共 ${deviceIds.length} 台（明细见下方记录表）`]
    })
  }
  return dimensions
})

const retrying = ref(false)

watch(recordStatus, () => {
  page.value = 1
})

function handleSizeChange() {
  page.value = 1
}

function goBack() {
  router.push({ name: 'UpgradeTasks' })
}

function statusText(status) {
  return STATUS_TEXT[status] || status || '—'
}

function statusTagType(status) {
  return STATUS_TAG_TYPE[status] || 'info'
}

function recordStatusText(status) {
  return RECORD_STATUS_TEXT[status] || status || '—'
}

function recordStatusTagType(status) {
  return RECORD_STATUS_TAG_TYPE[status] || 'info'
}

/** 进度条按后端回传的 0~100 取值，越界时收敛。 */
function progressOf(row) {
  const progress = Number(row.progress ?? 0)
  if (Number.isNaN(progress)) return 0
  return Math.min(100, Math.max(0, Math.round(progress)))
}

function recordProgressStatus(row) {
  if (row.status === 'SUCCESS') return 'success'
  if (row.status === 'FAILED') return 'exception'
  if (row.status === 'TIMEOUT') return 'warning'
  return ''
}

async function handleRetry() {
  try {
    await ElMessageBox.confirm(
      `确定要重投任务「${detail.value?.name}」中未成功的记录吗？`,
      '确认重投',
      { confirmButtonText: '重投', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  retrying.value = true
  try {
    await otaApi.retryTask(id.value)
    ElMessage.success('已触发重投')
    await queryClient.invalidateQueries({ queryKey: ['ota-task', id.value] })
    await queryClient.invalidateQueries({ queryKey: ['ota-task-records', id.value] })
  } catch {
    // 任务已全部成功（6238）时拦截器已提示后端文案，这里不再重复弹窗
  } finally {
    retrying.value = false
  }
}

function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN')
}
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--grid-gutter);
  margin-bottom: var(--grid-gutter);
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  padding: var(--spacing-lg);
}

.stat-card__label {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.stat-card__value {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.stat-card__value--success {
  color: var(--color-success);
}

.stat-card__value--danger {
  color: var(--color-danger);
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-xl);
  padding: var(--spacing-lg);
  margin-bottom: var(--grid-gutter);
}

.detail-meta__row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.detail-meta__label {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.detail-meta__value {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.detail-meta__value--mono {
  font-family: var(--font-family-mono);
}

.target-snapshot {
  padding: var(--spacing-lg);
  margin-bottom: var(--grid-gutter);
}

.target-snapshot__title {
  margin-bottom: var(--spacing-md);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.target-snapshot__row {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
}

.target-snapshot__label {
  flex-shrink: 0;
  width: 72px;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  line-height: 24px;
}

.target-snapshot__items {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
}

.target-snapshot__tag {
  max-width: 100%;
}

.target-snapshot__empty {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.record-table-wrap {
  padding: var(--spacing-md);
  background: var(--color-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius);
}

.record-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.record-toolbar__filter {
  width: 180px;
}

.record-toolbar__actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-left: auto;
}

.record-table__mono {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.record-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--grid-gutter);
}

@media (max-width: 900px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
