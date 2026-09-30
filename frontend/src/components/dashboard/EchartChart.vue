<template>
  <div ref="chartRef" class="chart-container" :style="{ height }"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import echarts from '@/utils/echarts'

/**
 * ECharts 通用容器：只负责实例的创建/更新/销毁与自适应。
 * option 传 null 表示清空画布（对应「暂无数据」状态）。
 */
const props = defineProps({
  option: {
    type: Object,
    default: null
  },
  height: {
    type: String,
    default: '300px'
  }
})

const chartRef = ref(null)
let chart = null

function applyOption() {
  if (!chart) return
  if (!props.option) {
    chart.clear()
    return
  }
  chart.setOption(props.option, true)
}

function resize() {
  chart?.resize()
}

onMounted(() => {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  applyOption()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})

watch(
  () => props.option,
  () => nextTick(applyOption)
)
</script>

<style scoped>
.chart-container {
  width: 100%;
}
</style>
