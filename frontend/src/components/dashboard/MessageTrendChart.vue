<template>
  <chart-card :title="`消息量趋势（近${days}天）`" :subtitle="subtitle" full-width>
    <echart-chart :option="option" height="280px" />
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
  },
  days: {
    type: Number,
    default: 7
  }
})

const subtitle = computed(() => (props.data.length ? '' : '暂无数据'))

const option = computed(() => {
  if (!props.data.length) return null
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => `${params[0].axisValue}<br/>消息量: <strong>${params[0].value}</strong>`
    },
    grid: { left: 60, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: props.data.map((item) => item.date),
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
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 3, color: CHART_COLORS.primary },
        itemStyle: { color: CHART_COLORS.primary, borderWidth: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(22, 93, 255, 0.15)' },
            { offset: 1, color: 'rgba(22, 93, 255, 0)' }
          ])
        },
        data: props.data.map((item) => Number(item.count) || 0)
      }
    ]
  }
})
</script>
