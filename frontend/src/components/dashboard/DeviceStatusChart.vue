<template>
  <chart-card title="设备状态分布" :subtitle="subtitle">
    <echart-chart :option="option" />
  </chart-card>
</template>

<script setup>
import { computed } from 'vue'
import { CHART_COLORS } from '@/utils/theme'
import ChartCard from './ChartCard.vue'
import EchartChart from './EchartChart.vue'

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  }
})

const STATUS_LABELS = { ONLINE: '在线', OFFLINE: '离线', INACTIVE: '未激活' }
const STATUS_COLORS = {
  ONLINE: CHART_COLORS.success,
  OFFLINE: CHART_COLORS.danger,
  INACTIVE: CHART_COLORS.textTertiary
}

const subtitle = computed(() => (props.data.length ? '' : '暂无数据'))

const option = computed(() => {
  if (!props.data.length) return null
  return {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      bottom: 0,
      textStyle: { fontSize: 12, color: CHART_COLORS.textRegular }
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 6,
          borderColor: CHART_COLORS.white,
          borderWidth: 2
        },
        label: {
          show: true,
          formatter: '{b}\n{c}',
          fontSize: 12,
          color: CHART_COLORS.textPrimary
        },
        emphasis: {
          label: { fontSize: 14, fontWeight: 'bold' },
          itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.1)' }
        },
        data: props.data.map((item) => ({
          name: STATUS_LABELS[item.status] || item.status,
          value: Number(item.count) || 0,
          itemStyle: {
            color: STATUS_COLORS[item.status] || CHART_COLORS.textTertiary
          }
        }))
      }
    ]
  }
})
</script>
