<template>
  <div class="device-log-page page">
    <PageHeader :title="'设备日志'" desc="聚合报文、命令、事件与状态变更，按发生时间倒序">
      <template #title>
        <div class="detail-heading">
          <el-button link class="detail-heading__back" @click="goBack">
            <svg-icon name="device" :size="16" />
            返回
          </el-button>
          <span class="detail-heading__name">设备日志</span>
          <span v-if="deviceName" class="detail-heading__device">{{ deviceName }}</span>
        </div>
      </template>
      <template #actions>
        <span class="live-toggle">
          <el-switch v-model="liveEnabled" size="small" />
          <span class="live-toggle__label">实时</span>
        </span>
        <el-button :loading="exporting" :disabled="!deviceKey" @click="handleExport">
          <svg-icon name="download" :size="14" />
          导出 CSV
        </el-button>
        <el-button :loading="loading" @click="handleQuery">
          <svg-icon name="refresh" :size="14" />
          刷新
        </el-button>
      </template>
    </PageHeader>

    <el-alert
      v-if="pendingNew > 0"
      type="info"
      :closable="false"
      show-icon
      class="live-alert"
    >
      <template #default>
        有 {{ pendingNew }} 条新日志
        <el-button link type="primary" size="small" @click="refreshLive">立即刷新</el-button>
      </template>
    </el-alert>

    <div class="filter-card card">
      <el-form label-position="top" @submit.prevent="handleQuery">
        <div class="filter-row">
          <el-form-item label="日志类型">
            <el-select
              v-model="form.types"
              multiple
              collapse-tags
              clearable
              placeholder="全部类型"
            >
              <el-option
                v-for="option in TYPE_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="关键字">
            <el-input
              v-model="form.keyword"
              placeholder="Topic / 标识符 / 错误信息 / 载荷"
              clearable
              @keyup.enter="handleQuery"
            />
          </el-form-item>
        </div>

        <el-form-item :label="`时间范围（跨度上限 ${MAX_RANGE_DAYS} 天，默认近 24 小时）`">
          <el-date-picker
            v-model="form.range"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>

        <div class="filter-actions">
          <el-button type="primary" :loading="loading" @click="handleQuery">
            <svg-icon name="search" :size="14" />
            查询
          </el-button>
          <el-button @click="reset">重置</el-button>
        </div>
      </el-form>
    </div>

    <div class="result-card card">
      <div class="result-header">
        <h3 class="card-title">日志时间线</h3>
        <span class="result-count">共 {{ total }} 条记录</span>
      </div>

      <DeviceLogTimeline
        :items="items"
        :total="total"
        :loading="loading"
        :page="pageNum"
        :size="pageSize"
        @page-change="goToPage"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDeviceStore } from '@/stores/device'
import { useUiStore } from '@/stores/ui'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import DeviceLogTimeline from '@/components/device/DeviceLogTimeline.vue'
import { exportApi } from '@/api/export'
import { downloadResponse } from '@/utils/download'
import { useDeviceLog } from '@/composables/useDeviceLog'

/** 与后端 `app.device-log.max-range-days` 默认值保持一致，仅做提前提示。 */
const MAX_RANGE_DAYS = 31

const TYPE_OPTIONS = [
  { value: 'MESSAGE', label: '报文' },
  { value: 'COMMAND', label: '命令' },
  { value: 'EVENT', label: '事件' },
  { value: 'STATUS', label: '状态' }
]

const route = useRoute()
const router = useRouter()
const deviceStore = useDeviceStore()

const deviceId = computed(() => route.params.id)
const deviceName = ref('')
const deviceKey = ref('')
const exporting = ref(false)

const ui = useUiStore()
const liveEnabled = ref(false)
const pendingNew = ref(0)
let lastEventId = 0
let liveTimer = null

const { form, items, total, loading, pageNum, pageSize, submit, reset, refresh, goToPage } =
  useDeviceLog(deviceId)

/** 实时事件只发「有更新」信号，这里按当前页决定自动刷新还是提示新日志。 */
watch(
  () => ui.events.length,
  () => {
    for (const event of ui.events) {
      if (event.id <= lastEventId) continue
      lastEventId = event.id
      if (!liveEnabled.value) continue
      if (event.eventName !== 'device-log' || event.deviceKey !== deviceKey.value) continue
      if (pageNum.value === 1) {
        scheduleLiveRefresh()
      } else {
        pendingNew.value += 1
      }
    }
  }
)

watch(liveEnabled, (enabled) => {
  pendingNew.value = 0
  if (enabled) {
    // 打开开关时从当前末尾开始，避免把历史事件一次性倒灌成刷新风暴
    lastEventId = ui.events.at(-1)?.id || 0
  }
})

function scheduleLiveRefresh() {
  if (liveTimer) return
  liveTimer = setTimeout(() => {
    liveTimer = null
    if (liveEnabled.value && pageNum.value === 1) refresh()
  }, 800)
}

function refreshLive() {
  pendingNew.value = 0
  refresh()
}

onUnmounted(() => {
  if (liveTimer) clearTimeout(liveTimer)
})

/** 兼容日期控件的字符串与 Date 两种取值。 */
function toDate(value) {
  if (!value) return null
  if (value instanceof Date) return value
  return new Date(String(value).replace(' ', 'T'))
}

function handleQuery() {
  const [start, end] = form.range ?? []
  const startAt = toDate(start)
  const endAt = toDate(end)
  if (startAt && endAt) {
    const spanDays = (endAt - startAt) / (24 * 60 * 60 * 1000)
    if (spanDays > MAX_RANGE_DAYS) {
      ElMessage.warning(`时间跨度不能超过 ${MAX_RANGE_DAYS} 天，请缩小范围后再查询`)
      return
    }
  }
  submit()
}

function goBack() {
  router.push(`/workbench/devices/${deviceId.value}`)
}

/** 导出当前筛选条件下的完整日志；与表格查询共用同一套参数口径。 */
async function handleExport() {
  if (!deviceKey.value) return
  const [start, end] = form.range ?? []
  const startAt = toDate(start)
  const endAt = toDate(end)
  if (startAt && endAt) {
    const spanDays = (endAt - startAt) / (24 * 60 * 60 * 1000)
    if (spanDays > MAX_RANGE_DAYS) {
      ElMessage.warning(`时间跨度不能超过 ${MAX_RANGE_DAYS} 天，请缩小范围后再导出`)
      return
    }
  }
  const params = {}
  if (form.types?.length) params.types = form.types.join(',')
  if (start) params.startTime = start
  if (end) params.endTime = end
  if (form.keyword) params.keyword = form.keyword
  exporting.value = true
  try {
    const response = await exportApi.deviceLogs(deviceKey.value, params)
    const name = downloadResponse(response, `device-logs-${deviceId.value}.csv`)
    ElMessage.success(`已导出 ${name}`)
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  const device = await deviceStore.fetchDeviceById(deviceId.value)
  deviceName.value = device?.deviceName || ''
  deviceKey.value = device?.deviceKey || ''
})
</script>

<style scoped>
.detail-heading {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  min-width: 0;
}

.detail-heading__back {
  flex-shrink: 0;
}

.detail-heading__name {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.detail-heading__device {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.live-toggle {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.live-toggle__label {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.live-alert {
  margin-bottom: var(--spacing-md);
}

.filter-card {
  margin-bottom: var(--grid-gutter);
}

.filter-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--grid-gutter);
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.result-count {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

@media (max-width: 768px) {
  .filter-row {
    grid-template-columns: 1fr;
  }
}
</style>
