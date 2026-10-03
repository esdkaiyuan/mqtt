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
        <el-button :loading="loading" @click="handleQuery">
          <svg-icon name="refresh" :size="14" />
          刷新
        </el-button>
      </template>
    </PageHeader>

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
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDeviceStore } from '@/stores/device'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import DeviceLogTimeline from '@/components/device/DeviceLogTimeline.vue'
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

const { form, items, total, loading, pageNum, pageSize, submit, reset, goToPage } =
  useDeviceLog(deviceId)

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

onMounted(async () => {
  const device = await deviceStore.fetchDeviceById(deviceId.value)
  deviceName.value = device?.deviceName || ''
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
