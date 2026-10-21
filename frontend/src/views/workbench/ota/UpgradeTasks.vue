<template>
  <div class="upgrade-tasks-page page">
    <PageHeader title="升级任务" desc="按产品 / 分组 / 标签批次下发 OTA 固件并跟踪进度">
      <template #actions>
        <el-button type="primary" :disabled="!canWrite" @click="openCreateDialog">
          <svg-icon name="add" :size="16" />
          新建任务
        </el-button>
      </template>
    </PageHeader>

    <div class="filter-bar">
      <div class="filter-actions">
        <el-button :disabled="loading" @click="reload">
          <svg-icon name="refresh" :size="16" />
          刷新
        </el-button>
      </div>
    </div>

    <div class="task-table-wrap">
      <el-table v-loading="loading" :data="tasks" border stripe>
        <el-table-column prop="name" label="任务名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="固件版本" width="130">
          <template #default="{ row }">
            <span class="task-table__mono">{{ row.version || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="所属产品" min-width="140" show-overflow-tooltip />
        <el-table-column label="升级进度" min-width="200">
          <template #default="{ row }">
            <div class="task-progress">
              <el-progress
                :percentage="progressOf(row)"
                :stroke-width="8"
                :status="progressStatus(row)"
                :show-text="false"
              />
              <span class="task-progress__text">
                成功 {{ row.successCount ?? 0 }} / 共 {{ row.totalCount ?? 0 }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="失败" width="80">
          <template #default="{ row }">
            <span :class="{ 'task-table__failed': (row.failedCount ?? 0) > 0 }">
              {{ row.failedCount ?? 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="跳过" width="80">
          <template #default="{ row }">
            <span :class="{ 'task-table__muted': (row.skippedCount ?? 0) > 0 }">
              {{ row.skippedCount ?? 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="下发策略" width="130">
          <template #default="{ row }">
            <span v-if="row.batchMode === 'BATCHED'" class="task-table__mono">
              灰度 {{ row.currentBatchNo ?? 0 }}/{{ row.batchTotal ?? 1 }}
            </span>
            <span v-else class="task-table__muted">一次性全量</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" disable-transitions>
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'RUNNING' && row.batchMode === 'BATCHED'"
              link
              type="warning"
              :disabled="!canWrite"
              @click="pauseTask(row)"
            >
              暂停
            </el-button>
            <el-button
              v-if="row.status === 'PAUSED'"
              link
              type="primary"
              :disabled="!canWrite"
              @click="resumeTask(row)"
            >
              恢复
            </el-button>
            <el-button
              v-if="row.status === 'RUNNING' && row.batchMode === 'BATCHED'"
              link
              type="primary"
              :disabled="!canWrite || !hasPendingBatch(row)"
              @click="advanceTask(row)"
            >
              推进下一批
            </el-button>
            <el-button
              link
              type="primary"
              :disabled="!canWrite || row.status === 'SUCCESS' || row.status === 'ABORTED'"
              @click="retryTask(row)"
            >
              重投
            </el-button>
            <el-button
              link
              type="danger"
              :disabled="!canWrite || isActive(row)"
              @click="removeTask(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState description="暂无升级任务，点击「新建任务」创建" />
        </template>
      </el-table>

      <div class="task-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <el-dialog
      v-model="showCreateDialog"
      title="新建升级任务"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top" class="dialog-form" @submit.prevent>
        <el-form-item label="任务名称" required>
          <el-input
            v-model="form.name"
            maxlength="64"
            show-word-limit
            placeholder="如：1.0.0 全量升级"
            @keyup.enter="handleCreate"
          />
        </el-form-item>
        <el-form-item label="固件包" required>
          <el-select
            v-model="form.firmwareId"
            class="dialog-form__control"
            placeholder="请选择固件包"
            @change="handleFirmwareChange"
          >
            <el-option
              v-for="item in firmwareOptions"
              :key="item.id"
              :label="`${item.productName} · ${item.version}`"
              :value="item.id"
            />
          </el-select>
          <p v-if="selectedFirmware" class="dialog-form__hint">
            所属产品：{{ selectedFirmware.productName }}（目标设备须与该产品一致）
          </p>
        </el-form-item>
        <el-form-item label="目标范围" required>
          <el-radio-group v-model="form.targetMode" :disabled="!form.firmwareId">
            <el-radio-button value="product">整个产品</el-radio-button>
            <el-radio-button value="group">指定分组</el-radio-button>
            <el-radio-button value="tag">指定标签</el-radio-button>
            <el-radio-button value="device">手动选设备</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.targetMode === 'group'" label="选择分组">
          <el-tree-select
            v-model="form.groupIds"
            :data="groupTree"
            :props="TREE_PROPS"
            node-key="id"
            multiple
            show-checkbox
            check-strictly
            :render-after-expand="false"
            default-expand-all
            class="dialog-form__control"
            placeholder="可多选，含子分组"
          />
        </el-form-item>
        <el-form-item v-else-if="form.targetMode === 'tag'" label="选择标签">
          <el-select v-model="form.tagIds" multiple class="dialog-form__control" placeholder="可多选">
            <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-else-if="form.targetMode === 'device'" label="选择设备">
          <el-select
            v-model="form.deviceIds"
            multiple
            filterable
            class="dialog-form__control"
            placeholder="可多选"
          >
            <el-option
              v-for="device in deviceOptions"
              :key="device.id"
              :label="device.deviceName || device.deviceKey"
              :value="device.id"
            />
          </el-select>
          <p class="dialog-form__hint">仅列出与固件同产品的设备（最多展示 100 台）</p>
        </el-form-item>
        <p v-else class="dialog-form__hint">
          将对「{{ selectedFirmware?.productName || '该产品' }}」下的全部设备下发升级。
        </p>
        <el-divider content-position="left">下发策略</el-divider>
        <el-form-item label="灰度方式">
          <el-radio-group v-model="form.batchMode">
            <el-radio-button value="ALL">一次性全量</el-radio-button>
            <el-radio-button value="BATCHED">分批灰度</el-radio-button>
          </el-radio-group>
          <p class="dialog-form__hint">
            分批灰度只下发首批，确认本批成功率后再推进下一批；一次性全量与改造前行为一致。
          </p>
        </el-form-item>
        <template v-if="form.batchMode === 'BATCHED'">
          <el-form-item label="每批台数" required>
            <el-input-number
              v-model="form.batchSize"
              :min="1"
              :max="100000"
              controls-position="right"
              class="dialog-form__control"
            />
            <p class="dialog-form__hint">按设备顺序切批，最后一批装余数。</p>
          </el-form-item>
          <el-form-item label="批次推进间隔">
            <el-select v-model="form.batchIntervalMs" class="dialog-form__control">
              <el-option label="仅手动推进" :value="0" />
              <el-option label="10 分钟" :value="600000" />
              <el-option label="30 分钟" :value="1800000" />
              <el-option label="1 小时" :value="3600000" />
              <el-option label="6 小时" :value="21600000" />
              <el-option label="24 小时" :value="86400000" />
            </el-select>
            <p class="dialog-form__hint">
              自动推进以「上一批全部收口」为前提：前一批未验证完不会铺下一批。
            </p>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { otaApi } from '@/api/ota'
import { deviceApi } from '@/api/device'
import { useDeviceGroupTreeQuery, useTagListQuery } from '@/composables/useDeviceGroups'
import { unwrapResult } from '@/utils/result'
import { useAuthStore } from '@/stores/auth'

/** 任务状态 → 中文文案（T-32 增 PAUSED / ABORTED）。 */
const STATUS_TEXT = {
  RUNNING: '进行中',
  PAUSED: '已暂停',
  ABORTED: '已终止',
  SUCCESS: '成功',
  PARTIAL: '部分成功',
  FAILED: '失败'
}
/** 任务状态 → el-tag 类型（RUNNING 蓝 / PAUSED 灰 / ABORTED 深灰 / SUCCESS 绿 / PARTIAL 橙 / FAILED 红）。 */
const STATUS_TAG_TYPE = {
  RUNNING: 'primary',
  PAUSED: 'warning',
  ABORTED: 'info',
  SUCCESS: 'success',
  PARTIAL: 'warning',
  FAILED: 'danger'
}
/** 进行中（含暂停）——决定「删除」是否可用。 */
const ACTIVE_STATUSES = ['RUNNING', 'PAUSED']
/** 分组树选择器字段映射（与设备列表保持一致）。 */
const TREE_PROPS = { label: 'name', children: 'children' }

const authStore = useAuthStore()
const router = useRouter()
const queryClient = useQueryClient()

const canWrite = computed(() => authStore.hasRole(['ADMIN', 'OPERATOR']))

const page = ref(1)
const size = ref(10)
const tasksQueryKey = ['ota-tasks']

const tasksQuery = useQuery({
  queryKey: computed(() => ['ota-tasks', page.value, size.value]),
  queryFn: async () =>
    unwrapResult(await otaApi.listTasks({ page: page.value, size: size.value }), {
      records: [],
      total: 0
    })
})

const tasks = computed(() => tasksQuery.data.value?.records ?? [])
const total = computed(() => tasksQuery.data.value?.total ?? 0)
const loading = computed(() => tasksQuery.isFetching.value)

const { tree: groupTree } = useDeviceGroupTreeQuery()
const { tags } = useTagListQuery()

const firmwaresQuery = useQuery({
  queryKey: ['ota-firmwares'],
  queryFn: async () => unwrapResult(await otaApi.listFirmwares(), [])
})
const firmwareOptions = computed(() => firmwaresQuery.data.value ?? [])

const devicesQuery = useQuery({
  queryKey: ['ota-device-options'],
  queryFn: async () => unwrapResult(await deviceApi.getList({ pageNum: 1, pageSize: 100 }), { records: [] })
})

const showCreateDialog = ref(false)
const creating = ref(false)

const EMPTY_FORM = {
  name: '',
  firmwareId: null,
  targetMode: 'product',
  groupIds: [],
  tagIds: [],
  deviceIds: [],
  // T-32 灰度：默认一次性全量，与改造前行为一致
  batchMode: 'ALL',
  batchSize: 10,
  batchIntervalMs: 0
}
const form = reactive({ ...EMPTY_FORM })

const selectedFirmware = computed(
  () => firmwareOptions.value.find((item) => item.id === form.firmwareId) || null
)

/** 手选设备仅展示与固件同产品的设备，避免提交后被后端静默剔除。 */
const deviceOptions = computed(() => {
  const records = devicesQuery.data.value?.records ?? []
  const productId = selectedFirmware.value?.productId
  if (!productId) return records
  return records.filter((device) => device.productId === productId)
})

watch(showCreateDialog, (open) => {
  if (!open) return
  Object.assign(form, EMPTY_FORM, { groupIds: [], tagIds: [], deviceIds: [] })
})

function openCreateDialog() {
  showCreateDialog.value = true
}

/** 切换固件会改变所属产品，连带清空其他维度的已选目标。 */
function handleFirmwareChange() {
  form.groupIds = []
  form.tagIds = []
  form.deviceIds = []
}

function reload() {
  return tasksQuery.refetch()
}

function handleSizeChange() {
  page.value = 1
}

function invalidateTasks() {
  return queryClient.invalidateQueries({ queryKey: tasksQueryKey })
}

function goDetail(row) {
  router.push({ name: 'UpgradeTaskDetail', params: { id: row.id } })
}

function statusText(status) {
  return STATUS_TEXT[status] || status || '—'
}

function statusTagType(status) {
  return STATUS_TAG_TYPE[status] || 'info'
}

/** 进度条按 success/total 计算（设计文档 §8.2）。 */
function progressOf(row) {
  const total = Number(row.totalCount ?? 0)
  if (total <= 0) return 0
  return Math.round((Number(row.successCount ?? 0) / total) * 100)
}

function progressStatus(row) {
  if (row.status === 'SUCCESS') return 'success'
  if (row.status === 'FAILED') return 'exception'
  if (row.status === 'PARTIAL') return 'warning'
  // 暂停 / 终止都不再推进，用灰色表达「停在这里」而不是「失败」
  return ''
}

/** 按当前目标范围组装 BatchTargetRequest（后端取四维并集）。 */
function buildTarget() {
  if (form.targetMode === 'product') {
    return { productIds: [selectedFirmware.value?.productId] }
  }
  if (form.targetMode === 'group') {
    return { groupIds: [...form.groupIds] }
  }
  if (form.targetMode === 'tag') {
    return { tagIds: [...form.tagIds] }
  }
  return { deviceIds: [...form.deviceIds] }
}

function targetIsEmpty() {
  if (form.targetMode === 'product') return !selectedFirmware.value?.productId
  if (form.targetMode === 'group') return form.groupIds.length === 0
  if (form.targetMode === 'tag') return form.tagIds.length === 0
  return form.deviceIds.length === 0
}

async function handleCreate() {
  const name = form.name.trim()
  if (!name) {
    ElMessage.warning('请填写任务名称')
    return
  }
  if (name.length > 64) {
    ElMessage.warning('任务名称不能超过 64 个字符')
    return
  }
  if (!form.firmwareId) {
    ElMessage.warning('请选择固件包')
    return
  }
  if (targetIsEmpty()) {
    ElMessage.warning('请至少选择一个升级目标')
    return
  }
  if (form.batchMode === 'BATCHED' && !(form.batchSize >= 1)) {
    ElMessage.warning('每批台数必须大于 0')
    return
  }

  creating.value = true
  try {
    const payload = { name, firmwareId: form.firmwareId, target: buildTarget() }
    if (form.batchMode === 'BATCHED') {
      payload.batchMode = 'BATCHED'
      payload.batchSize = form.batchSize
      payload.batchIntervalMs = form.batchIntervalMs
    }
    const detail = unwrapResult(await otaApi.createTask(payload), null)
    const skipped = detail?.conflictSkippedCount ?? 0
    // 被互斥剔除的设备必须显式告知：静默少发几台，运维会以为升级覆盖不全
    ElMessage.success(
      skipped > 0
        ? `升级任务已创建；${skipped} 台设备正在其他升级任务中，已自动跳过`
        : '升级任务已创建'
    )
    showCreateDialog.value = false
    await invalidateTasks()
  } catch {
    // 命中业务码 6231 / 6237 / 6238 / 6293 / 6294 时拦截器已提示后端文案，这里不再重复弹窗
  } finally {
    creating.value = false
  }
}

/** 任务是否处于「进行中」（含暂停），决定删除按钮是否可用。 */
function isActive(row) {
  return ACTIVE_STATUSES.includes(row.status)
}

/** 是否还有未下发的批次可推进：已下发的批号小于总批数即说明后面还有批次。 */
function hasPendingBatch(row) {
  return Number(row.currentBatchNo ?? 0) < Number(row.batchTotal ?? 1)
}

async function pauseTask(row) {
  try {
    await ElMessageBox.confirm(
      `暂停后任务「${row.name}」不再开新批，已下发的设备会继续升级完成。确定暂停吗？`,
      '确认暂停',
      { confirmButtonText: '暂停', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await otaApi.pauseTask(row.id)
    ElMessage.success('任务已暂停')
    await invalidateTasks()
  } catch {
    // 6238 由拦截器提示
  }
}

async function resumeTask(row) {
  try {
    await otaApi.resumeTask(row.id)
    ElMessage.success('任务已恢复')
    await invalidateTasks()
  } catch {
    // 6238 由拦截器提示
  }
}

async function advanceTask(row) {
  try {
    const sent = unwrapResult(await otaApi.advanceTask(row.id), 0)
    ElMessage.success(sent > 0 ? `已下发 ${sent} 台` : '下一批没有待下发的设备')
    await invalidateTasks()
  } catch {
    // 6238 由拦截器提示
  }
}

async function retryTask(row) {
  try {
    await ElMessageBox.confirm(
      `确定要重投任务「${row.name}」中未成功的记录吗？`,
      '确认重投',
      { confirmButtonText: '重投', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  try {
    await otaApi.retryTask(row.id)
    ElMessage.success('已触发重投')
    await invalidateTasks()
  } catch {
    // 任务已全部成功（6238）时拦截器已提示后端文案，这里不再重复弹窗
  }
}

async function removeTask(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除任务「${row.name}」及其逐台记录吗？此操作不可恢复。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  try {
    await otaApi.removeTask(row.id)
    ElMessage.success('任务已删除')
    await invalidateTasks()
  } catch {
    // 任务运行中（6238）时拦截器已提示后端文案，这里不再重复弹窗
  }
}

function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN')
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--grid-gutter);
  padding: var(--spacing-md);
  background: var(--color-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius);
}

.filter-actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-left: auto;
}

.task-table-wrap {
  padding: var(--spacing-md);
  background: var(--color-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius);
}

.task-table__mono {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.task-table__failed {
  font-weight: var(--font-weight-medium);
  color: var(--color-danger);
}

/* 跳过数与「一次性全量」是弱信息，用三级文字色弱化，避免与失败数抢读 */
.task-table__muted {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.task-progress {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2xs);
}

.task-progress__text {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.task-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--grid-gutter);
}

.dialog-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.dialog-form__control {
  width: 100%;
}

.dialog-form__hint {
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
</style>
