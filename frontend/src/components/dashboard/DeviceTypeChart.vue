<template>
  <chart-card title="设备类型分布" :subtitle="subtitle">
    <echart-chart :option="option" />
  </chart-card>
</template>

<script setup>
import { computed } from 'vue'
import echarts from '@/utils/echarts'
import { CHART_COLORS } from '@/utils/theme'
import ChartCard from './ChartCard.vue'
import EchartChart from './EchartChart.vue'

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  }
})

const TYPE_LABELS = { sensor: '传感器', gateway: '网关', actuator: '执行器', other: '其他' }

const subtitle = computed(() => (props.data.length ? '' : '暂无数据'))

const option = computed(() => {
  if (!props.data.length) return null
  return {
    tooltip: {
      trigger: 'axis',
      formatter: '{b}: {c} 台'
    },
    grid: { left: 60, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: props.data.map((item) => TYPE_LABELS[item.device_type] || item.device_type),
      axisLine: { lineStyle: { color: CHART_COLORS.border } },
      axisLabel: { fontSize: 12, color: CHART_COLORS.textRegular }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: CHART_COLORS.borderLight } },
      axisLabel: { fontSize: 12, color: CHART_COLORS.textTertiary }
    },
    series: [
      {
        type: 'bar',
        data: props.data.map((item) => Number(item.count) || 0),
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: CHART_COLORS.primaryHover },
            { offset: 1, color: CHART_COLORS.primary }
          ])
        },
        barWidth: 40,
        label: {
          show: true,
          position: 'top',
          fontSize: 11,
          color: CHART_COLORS.textRegular
        }
      }
    ]
  }
})
</script>
