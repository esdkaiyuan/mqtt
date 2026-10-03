<template>
  <chart-card :title="title" :subtitle="subtitle" full-width>
    <echart-chart :option="option" :height="height" />
  </chart-card>
</template>

<script setup>
import { computed } from 'vue'
import echarts from '@/utils/echarts'
import { CHART_COLORS } from '@/utils/theme'
import ChartCard from '@/components/dashboard/ChartCard.vue'
import EchartChart from '@/components/dashboard/EchartChart.vue'

const props = defineProps({
  /** 图表标题（承载属性名 / 面板标题）。 */
  title: { type: String, default: '属性趋势' },
  /** 序列集合：PropertySeriesVO[]（deviceId / identifier / dataType / numeric / points）。 */
  series: { type: Array, default: () => [] },
  /** deviceId → 设备名，用于图例与 tooltip。 */
  deviceNames: { type: Object, default: () => ({}) },
  /** 图型：line / bar。 */
  chartType: { type: String, default: 'line' },
  /** 聚合口径：avg / min / max / count。 */
  aggregation: { type: String, default: 'avg' },
  height: { type: String, default: '320px' }
})

/** 多序列配色，取自设计系统图表色板。 */
const SERIES_COLORS = [
  CHART_COLORS.primary,
  CHART_COLORS.success,
  CHART_COLORS.warning,
  CHART_COLORS.danger,
  CHART_COLORS.primaryHover
]

/** 任一条序列有数据点即视为有数据；全空时清画布，由副标题承载空态文案。 */
const hasData = computed(() => props.series.some((item) => item.points?.length))

const subtitle = computed(() => (hasData.value ? '' : '该时间段无上报数据'))

/** 所有序列桶起点的并集，升序，作为共享类目轴。 */
const axisTimes = computed(() => {
  const times = new Set()
  props.series.forEach((item) => {
    ;(item.points || []).forEach((point) => times.add(point.time))
  })
  return Array.from(times).sort()
})

/**
 * 非数值型属性（numeric=false）或显式选择 count 时只画样本数；
 * 数值型按所选聚合取 min / max / avg，桶内无样本时留空形成断点。
 */
function pickValue(point, seriesItem) {
  if (!point) return null
  if (!seriesItem.numeric || props.aggregation === 'count') {
    return point.count ?? null
  }
  const value = point[props.aggregation]
  return value === undefined || value === null ? null : Number(value)
}

function seriesName(item) {
  return item.label || props.deviceNames[item.deviceId] || `设备 ${item.deviceId}`
}

const option = computed(() => {
  if (!hasData.value) return null
  const times = axisTimes.value
  const isBar = props.chartType === 'bar'
  const multi = props.series.length > 1

  const dataSeries = props.series.map((item, index) => {
    const color = SERIES_COLORS[index % SERIES_COLORS.length]
    const pointMap = new Map((item.points || []).map((point) => [point.time, point]))
    const base = {
      name: seriesName(item),
      type: isBar ? 'bar' : 'line',
      data: times.map((time) => pickValue(pointMap.get(time), item))
    }

    if (isBar) {
      base.itemStyle = { color }
      base.barMaxWidth = 24
    } else {
      base.smooth = true
      base.symbol = 'circle'
      base.symbolSize = 6
      base.connectNulls = true
      base.lineStyle = { width: 3, color }
      base.itemStyle = { color, borderWidth: 2 }
      // 面积渐变仅用于单序列，多序列叠加会互相遮挡
      if (!multi) {
        base.areaStyle = {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(22, 93, 255, 0.15)' },
            { offset: 1, color: 'rgba(22, 93, 255, 0)' }
          ])
        }
      }
    }
    return base
  })

  return {
    tooltip: { trigger: 'axis', axisPointer: { type: isBar ? 'shadow' : 'line' } },
    legend: {
      show: multi,
      top: 0,
      type: 'scroll',
      textStyle: { fontSize: 11, color: CHART_COLORS.textTertiary }
    },
    grid: { left: 60, right: 20, top: multi ? 40 : 20, bottom: 40 },
    dataZoom: [{ type: 'inside', throttle: 50 }],
    xAxis: {
      type: 'category',
      boundaryGap: isBar,
      data: times,
      axisLine: { lineStyle: { color: CHART_COLORS.border } },
      axisLabel: { fontSize: 11, color: CHART_COLORS.textTertiary }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: CHART_COLORS.borderLight } },
      axisLabel: { fontSize: 11, color: CHART_COLORS.textTertiary }
    },
    series: dataSeries
  }
})
</script>
