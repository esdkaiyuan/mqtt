<template>
  <div v-loading="loading" class="board-panel">
    <div v-if="error" class="board-panel__error">
      <span class="board-panel__error-text">面板数据加载失败</span>
      <el-button link type="primary" size="small" @click="refetch()">重试</el-button>
    </div>
    <PropertyTrendChart
      v-else
      :title="panel.title || '未命名面板'"
      :series="series"
      :device-names="deviceNames"
      :chart-type="panel.chartType"
      :aggregation="panel.aggregation"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import PropertyTrendChart from '@/components/chart/PropertyTrendChart.vue'
import { propertyHistoryApi } from '@/api/propertyHistory'
import { unwrapResult } from '@/utils/result'
import { buildPropertyHistoryParams, rangeOfHours } from '@/composables/usePropertyHistory'

const EMPTY = []

const props = defineProps({
  /** DashboardConfig.Panel：title / deviceIds / identifier / bucket / rangeDays / chartType / aggregation */
  panel: { type: Object, required: true },
  /** 设备 id → 设备名，用于图例；缺省时图表回退为「设备 {id}」 */
  deviceNames: { type: Object, default: () => ({}) }
})

/**
 * 时间窗在组件创建时确定一次（面板配置变更后由父级以 key 重建，重新取当前时刻），
 * 避免每轮重渲染都把窗口往后推导致缓存键抖动。
 */
const range = rangeOfHours((props.panel.rangeDays || 1) * 24)

const params = computed(() =>
  buildPropertyHistoryParams(props.panel.deviceIds, [props.panel.identifier], {
    range,
    bucket: props.panel.bucket
  })
)

/** 单面板独立查询 / 独立缓存：键只取面板自身参数，一个面板失败不影响其他面板。 */
const panelQuery = useQuery({
  queryKey: computed(() => ['board-panel-history', params.value]),
  queryFn: async () => unwrapResult(await propertyHistoryApi.query(params.value), EMPTY),
  enabled: computed(() => Boolean(props.panel.deviceIds?.length && props.panel.identifier))
})

const series = computed(() => panelQuery.data.value ?? EMPTY)
const error = computed(() => panelQuery.isError.value)
const loading = computed(() => panelQuery.isFetching.value)
const refetch = panelQuery.refetch
</script>

<style scoped>
.board-panel {
  min-height: 200px;
}

.board-panel__error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  min-height: 200px;
  color: var(--color-text-tertiary);
}

.board-panel__error-text {
  font-size: var(--font-size-sm);
}
</style>
