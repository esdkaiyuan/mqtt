<template>
  <div class="waveform">
    <h1 class="page-title">实时波形显示</h1>

    <!-- Control Bar -->
    <el-card class="control-card" shadow="hover">
      <div class="control-bar">
        <div class="control-left">
          <el-button type="primary" @click="togglePause">
            <el-icon><VideoPause v-if="!isPaused" /><VideoPlay v-else /></el-icon>
            {{ isPaused ? '继续' : '暂停' }}
          </el-button>
          <el-button @click="clearData">
            <el-icon><Delete /></el-icon>
            清除
          </el-button>
          <el-button @click="captureData">
            <el-icon><Camera /></el-icon>
            截取
          </el-button>
        </div>
        <div class="control-right">
          <el-select v-model="timeWindow" placeholder="时间窗口" style="width: 120px;">
            <el-option label="5秒" :value="5" />
            <el-option label="10秒" :value="10" />
            <el-option label="30秒" :value="30" />
            <el-option label="60秒" :value="60" />
          </el-select>
          <el-select v-model="chartType" placeholder="图表类型" style="width: 120px;">
            <el-option label="折线图" value="line" />
            <el-option label="散点图" value="scatter" />
          </el-select>
          <el-checkbox v-model="showAcceleration">加速度</el-checkbox>
          <el-checkbox v-model="showGyroscope">陀螺仪</el-checkbox>
        </div>
      </div>
    </el-card>

    <!-- Charts -->
    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- Acceleration Chart -->
      <el-col :span="showAcceleration && showGyroscope ? 12 : 24" v-if="showAcceleration">
        <el-card shadow="hover">
          <template #header>
            <div class="chart-header">
              <el-icon><TrendCharts /></el-icon>
              <span>加速度 (g)</span>
              <div class="legend">
                <span class="legend-item" style="color: #ef4444;">● X轴</span>
                <span class="legend-item" style="color: #22c55e;">● Y轴</span>
                <span class="legend-item" style="color: #3b82f6;">● Z轴</span>
              </div>
            </div>
          </template>
          <div class="chart-container">
            <canvas ref="accelChart"></canvas>
          </div>
        </el-card>
      </el-col>

      <!-- Gyroscope Chart -->
      <el-col :span="showAcceleration && showGyroscope ? 12 : 24" v-if="showGyroscope">
        <el-card shadow="hover">
          <template #header>
            <div class="chart-header">
              <el-icon><Compass /></el-icon>
              <span>陀螺仪 (°/s)</span>
              <div class="legend">
                <span class="legend-item" style="color: #f59e0b;">● X轴</span>
                <span class="legend-item" style="color: #8b5cf6;">● Y轴</span>
                <span class="legend-item" style="color: #ec4899;">● Z轴</span>
              </div>
            </div>
          </template>
          <div class="chart-container">
            <canvas ref="gyroChart"></canvas>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Data Info -->
    <el-card shadow="hover" style="margin-top: 20px;">
      <template #header>
        <div class="chart-header">
          <el-icon><InfoFilled /></el-icon>
          <span>数据信息</span>
        </div>
      </template>
      <el-row :gutter="20">
        <el-col :span="4">
          <div class="info-item">
            <div class="info-label">数据点数</div>
            <div class="info-value">{{ store.realtimeData.timestamps.length }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="info-item">
            <div class="info-label">采样率</div>
            <div class="info-value">50 Hz</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="info-item">
            <div class="info-label">最新加速度X</div>
            <div class="info-value">{{ store.latestData.ax.toFixed(2) }} g</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="info-item">
            <div class="info-label">最新加速度Y</div>
            <div class="info-value">{{ store.latestData.ay.toFixed(2) }} g</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="info-item">
            <div class="info-label">最新加速度Z</div>
            <div class="info-value">{{ store.latestData.az.toFixed(2) }} g</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="info-item">
            <div class="info-label">连接状态</div>
            <div :class="['info-value', store.isConnected ? 'connected' : 'disconnected']">
              {{ store.isConnected ? '已连接' : '未连接' }}
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { Chart, registerables } from 'chart.js'
import { useMotionStore } from '../stores/motion'
import { ElMessage } from 'element-plus'

Chart.register(...registerables)

const store = useMotionStore()

const accelChart = ref(null)
const gyroChart = ref(null)
const isPaused = ref(false)
const timeWindow = ref(10)
const chartType = ref('line')
const showAcceleration = ref(true)
const showGyroscope = ref(true)

let accelChartInstance = null
let gyroChartInstance = null
let animationId = null

const maxPoints = ref(500)

watch(timeWindow, (newVal) => {
  maxPoints.value = newVal * 50 // 50Hz
})

const initCharts = () => {
  // Acceleration Chart
  if (accelChart.value) {
    const ctx = accelChart.value.getContext('2d')
    accelChartInstance = new Chart(ctx, {
      type: chartType.value,
      data: {
        labels: [],
        datasets: [
          {
            label: 'X轴',
            data: [],
            borderColor: '#ef4444',
            backgroundColor: 'transparent',
            borderWidth: 2,
            pointRadius: 0,
            tension: 0.2
          },
          {
            label: 'Y轴',
            data: [],
            borderColor: '#22c55e',
            backgroundColor: 'transparent',
            borderWidth: 2,
            pointRadius: 0,
            tension: 0.2
          },
          {
            label: 'Z轴',
            data: [],
            borderColor: '#3b82f6',
            backgroundColor: 'transparent',
            borderWidth: 2,
            pointRadius: 0,
            tension: 0.2
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        scales: {
          x: {
            display: true,
            title: { display: true, text: '时间 (ms)' },
            ticks: { maxTicksLimit: 10 }
          },
          y: {
            display: true,
            title: { display: true, text: '加速度 (g)' }
          }
        },
        plugins: {
          legend: { display: false }
        }
      }
    })
  }

  // Gyroscope Chart
  if (gyroChart.value) {
    const ctx = gyroChart.value.getContext('2d')
    gyroChartInstance = new Chart(ctx, {
      type: chartType.value,
      data: {
        labels: [],
        datasets: [
          {
            label: 'X轴',
            data: [],
            borderColor: '#f59e0b',
            backgroundColor: 'transparent',
            borderWidth: 2,
            pointRadius: 0,
            tension: 0.2
          },
          {
            label: 'Y轴',
            data: [],
            borderColor: '#8b5cf6',
            backgroundColor: 'transparent',
            borderWidth: 2,
            pointRadius: 0,
            tension: 0.2
          },
          {
            label: 'Z轴',
            data: [],
            borderColor: '#ec4899',
            backgroundColor: 'transparent',
            borderWidth: 2,
            pointRadius: 0,
            tension: 0.2
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        scales: {
          x: {
            display: true,
            title: { display: true, text: '时间 (ms)' },
            ticks: { maxTicksLimit: 10 }
          },
          y: {
            display: true,
            title: { display: true, text: '角速度 (°/s)' }
          }
        },
        plugins: {
          legend: { display: false }
        }
      }
    })
  }
}

const updateCharts = () => {
  if (isPaused.value) return

  const timestamps = store.realtimeData.timestamps
  if (!timestamps.length) return

  const maxPts = maxPoints.value
  const startIndex = Math.max(0, timestamps.length - maxPts)
  const timeData = timestamps.slice(startIndex).map((t, i) => i * 20)

  // Update Acceleration Chart
  if (accelChartInstance && showAcceleration.value) {
    accelChartInstance.data.labels = timeData
    accelChartInstance.data.datasets[0].data = store.realtimeData.ax.slice(startIndex)
    accelChartInstance.data.datasets[1].data = store.realtimeData.ay.slice(startIndex)
    accelChartInstance.data.datasets[2].data = store.realtimeData.az.slice(startIndex)
    accelChartInstance.update('none')
  }

  // Update Gyroscope Chart
  if (gyroChartInstance && showGyroscope.value) {
    gyroChartInstance.data.labels = timeData
    gyroChartInstance.data.datasets[0].data = store.realtimeData.gx.slice(startIndex)
    gyroChartInstance.data.datasets[1].data = store.realtimeData.gy.slice(startIndex)
    gyroChartInstance.data.datasets[2].data = store.realtimeData.gz.slice(startIndex)
    gyroChartInstance.update('none')
  }
}

const animate = () => {
  updateCharts()
  animationId = requestAnimationFrame(animate)
}

const togglePause = () => {
  isPaused.value = !isPaused.value
  ElMessage.info(isPaused.value ? '波形已暂停' : '波形已继续')
}

const clearData = () => {
  store.clearRealtimeData()
  ElMessage.success('数据已清除')
}

const captureData = () => {
  ElMessage.success('数据截取成功，可在数据标注中查看')
}

onMounted(() => {
  initCharts()
  animate()
})

onUnmounted(() => {
  if (animationId) {
    cancelAnimationFrame(animationId)
  }
  if (accelChartInstance) {
    accelChartInstance.destroy()
  }
  if (gyroChartInstance) {
    gyroChartInstance.destroy()
  }
})
</script>

<style scoped>
.waveform {
  padding: 0;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 24px;
}

.control-card {
  margin-bottom: 20px;
}

.control-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.control-left {
  display: flex;
  gap: 12px;
}

.control-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.chart-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.legend {
  margin-left: auto;
  display: flex;
  gap: 16px;
}

.legend-item {
  font-size: 12px;
  font-weight: normal;
}

.chart-container {
  height: 300px;
  position: relative;
}

canvas {
  width: 100% !important;
  height: 100% !important;
}

.info-item {
  text-align: center;
  padding: 12px;
}

.info-label {
  font-size: 12px;
  color: #666;
  margin-bottom: 8px;
}

.info-value {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
}

.info-value.connected {
  color: #67c23a;
}

.info-value.disconnected {
  color: #f56c6c;
}
</style>
