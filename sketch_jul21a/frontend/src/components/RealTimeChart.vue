<template>
  <div class="realtime-chart">
    <canvas ref="chartCanvas"></canvas>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { Chart, registerables } from 'chart.js'
import { useMotionStore } from '../stores/motion'

Chart.register(...registerables)

const props = defineProps({
  maxPoints: {
    type: Number,
    default: 200
  },
  refreshInterval: {
    type: Number,
    default: 50
  }
})

const chartCanvas = ref(null)
const store = useMotionStore()
let chart = null
let animationId = null

const datasets = [
  { key: 'ax', label: '加速度 X', color: '#ef4444', data: [] },
  { key: 'ay', label: '加速度 Y', color: '#22c55e', data: [] },
  { key: 'az', label: '加速度 Z', color: '#3b82f6', data: [] },
  { key: 'gx', label: '陀螺仪 X', color: '#f59e0b', data: [] },
  { key: 'gy', label: '陀螺仪 Y', color: '#8b5cf6', data: [] },
  { key: 'gz', label: '陀螺仪 Z', color: '#ec4899', data: [] }
]

const initChart = () => {
  if (!chartCanvas.value) return

  const ctx = chartCanvas.value.getContext('2d')

  chart = new Chart(ctx, {
    type: 'line',
    data: {
      labels: [],
      datasets: datasets.map(ds => ({
        label: ds.label,
        data: [],
        borderColor: ds.color,
        backgroundColor: 'transparent',
        borderWidth: 2,
        pointRadius: 0,
        tension: 0.2,
        fill: false
      }))
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      scales: {
        x: {
          display: true,
          title: {
            display: true,
            text: '时间 (ms)',
            color: '#666'
          },
          ticks: {
            maxTicksLimit: 10,
            color: '#999'
          },
          grid: {
            color: '#f0f0f0'
          }
        },
        y: {
          display: true,
          title: {
            display: true,
            text: '数值',
            color: '#666'
          },
          ticks: {
            color: '#999'
          },
          grid: {
            color: '#f0f0f0'
          }
        }
      },
      plugins: {
        legend: {
          position: 'top',
          labels: {
            usePointStyle: true,
            padding: 20,
            color: '#666'
          }
        },
        tooltip: {
          mode: 'index',
          intersect: false,
          backgroundColor: 'rgba(0, 0, 0, 0.8)',
          titleColor: '#fff',
          bodyColor: '#fff',
          borderColor: '#ddd',
          borderWidth: 1
        }
      },
      interaction: {
        mode: 'nearest',
        axis: 'x',
        intersect: false
      }
    }
  })
}

const updateChart = () => {
  if (!chart || !store.realtimeData.timestamps.length) return

  const timestamps = store.realtimeData.timestamps
  const maxPts = props.maxPoints

  // Get last N points
  const startIndex = Math.max(0, timestamps.length - maxPts)
  const timeData = timestamps.slice(startIndex).map((t, i) => i * 20) // 20ms intervals

  chart.data.labels = timeData

  datasets.forEach((ds, index) => {
    chart.data.datasets[index].data = store.realtimeData[ds.key].slice(startIndex)
  })

  chart.update('none')
}

const startAnimation = () => {
  const animate = () => {
    updateChart()
    animationId = requestAnimationFrame(animate)
  }
  animate()
}

onMounted(() => {
  initChart()
  startAnimation()
})

onUnmounted(() => {
  if (animationId) {
    cancelAnimationFrame(animationId)
  }
  if (chart) {
    chart.destroy()
  }
})
</script>

<style scoped>
.realtime-chart {
  width: 100%;
  height: 100%;
  position: relative;
}

canvas {
  width: 100% !important;
  height: 100% !important;
}
</style>
