<template>
  <div class="shadow-overview-page page">
    <PageHeader title="设备影子" desc="多设备 desired / reported / delta 横向对比，便于排查配置下发与设备状态不一致">
      <template #actions>
        <el-button :loading="loading" @click="refetch">
          <svg-icon name="refresh" :size="14" />
          刷新
        </el-button>
      </template>
    </PageHeader>

    <div class="shadow-summary card">
      <div class="shadow-summary__metrics">
        <div class="metric">
          <span class="metric__value">{{ rows.length }}</span>
          <span class="metric__label">设备</span>
        </div>
        <div class="metric">
          <span class="metric__value">{{ withDelta }}</span>
          <span class="metric__label">存在差异</span>
        </div>
        <div class="metric">
          <span class="metric__value">{{ modeledCount }}</span>
          <span class="metric__label">已建模</span>
        </div>
      </div>

      <EmptyState
        v-if="!loading && rows.length === 0"
        description="当前账号还没有设备，创建设备并上报后即可看到影子"
      />

      <el-table
        v-else
        :data="rows"
        v-loading="loading"
        row-key="deviceId"
        style="width: 100%"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="shadow-detail">
              <div v-for="section in SECTIONS" :key="section.key" class="shadow-detail__item">
                <h4>{{ section.label }}</h4>
                <pre>{{ pretty(row[section.key]) }}</pre>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="设备" min-width="180">
          <template #default="{ row }">
            <router-link class="shadow-device-link" :to="`/workbench/devices/${row.deviceId}#device-shadow-section`">
              {{ row.deviceName || row.deviceKey }}
            </router-link>
            <div class="shadow-device-key">{{ row.deviceKey }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ONLINE' ? 'success' : 'info'">
              {{ row.status || '—' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="物模型" width="100">
          <template #default="{ row }">
            <span :class="['model-badge', { 'model-badge--off': !row.modeled }]">
              {{ row.modeled ? '已建模' : '未建模' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="影子版本" width="100" />
        <el-table-column label="差异字段" width="100">
          <template #default="{ row }">
            <span :class="['delta-count', { 'delta-count--none': deltaCount(row) === 0 }]">
              {{ deltaCount(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goDevice(row)">查看设备</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import SvgIcon from '@/components/Icon.vue'
import { deviceApi } from '@/api/device'
import { unwrapResult } from '@/utils/result'

const SECTIONS = [
  { key: 'desired', label: '期望值 desired' },
  { key: 'reported', label: '上报值 reported' },
  { key: 'delta', label: '差异 delta' }
]

const router = useRouter()

const summaryQuery = useQuery({
  queryKey: ['device-shadow-summary'],
  queryFn: async () => unwrapResult(await deviceApi.getShadowSummary(), [])
})

const rows = computed(() => summaryQuery.data.value || [])
const loading = computed(() => summaryQuery.isFetching.value)
const withDelta = computed(() => rows.value.filter((row) => deltaCount(row) > 0).length)
const modeledCount = computed(() => rows.value.filter((row) => row.modeled).length)

function deltaCount(row) {
  return Object.keys(row.delta || {}).length
}

function pretty(value) {
  return JSON.stringify(value || {}, null, 2)
}

function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

function goDevice(row) {
  router.push(`/workbench/devices/${row.deviceId}#device-shadow-section`)
}

function refetch() {
  summaryQuery.refetch()
}
</script>

<style scoped>
.shadow-summary {
  padding: var(--spacing-lg);
}

.shadow-summary__metrics {
  display: flex;
  gap: var(--spacing-2xl);
  margin-bottom: var(--spacing-lg);
}

.metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.metric__value {
  font-size: 24px;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.metric__label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.shadow-device-link {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
  text-decoration: none;
}

.shadow-device-link:hover {
  text-decoration: underline;
}

.shadow-device-key {
  margin-top: 2px;
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.model-badge {
  font-size: var(--font-size-xs);
  color: var(--color-success);
}

.model-badge--off {
  color: var(--color-text-tertiary);
}

.delta-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--color-warning-light, #fffaeb);
  color: var(--color-warning, #b54708);
  font-size: var(--font-size-xs);
}

.delta-count--none {
  background: var(--color-bg);
  color: var(--color-text-tertiary);
}

.shadow-detail {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--spacing-lg);
  padding: var(--spacing-md) var(--spacing-lg);
  background: var(--color-bg);
}

.shadow-detail__item h4 {
  margin-bottom: var(--spacing-xs);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-secondary);
}

.shadow-detail__item pre {
  margin: 0;
  max-height: 220px;
  overflow: auto;
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  line-height: 1.6;
  color: var(--color-text-primary);
  white-space: pre-wrap;
  word-break: break-all;
}

@media (max-width: 900px) {
  .shadow-detail {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
