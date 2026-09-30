<template>
  <chart-card title="设备类型分布" :subtitle="subtitle">
    <echart-chart :option="option" />
  </chart-card>
</template>

<script setup>
import { computed } from 'vue'
import echarts from '@/utils/echarts'
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
      axisLine: { lineStyle: { color: '#E5E6EB' } },
      axisLabel: { fontSize: 12, color: '#4E5969' }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#F2F3F5' } },
      axisLabel: { fontSize: 12, color: '#86909C' }
    },
    series: [
      {
        type: 'bar',
        data: props.data.map((item) => Number(item.count) || 0),
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#4080FF' },
            { offset: 1, color: '#165DFF' }
          ])
        },
        barWidth: 40,
        label: {
          show: true,
          position: 'top',
          fontSize: 11,
          color: '#4E5969'
        }
      }
    ]
  }
})
</script>
