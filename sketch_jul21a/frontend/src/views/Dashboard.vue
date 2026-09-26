<template>
  <div class="dashboard">
    <h1 class="page-title">仪表板</h1>

    <!-- Stats Cards -->
    <StatsCards />

    <!-- Content Row -->
    <el-row :gutter="20">
      <!-- Connection Status Card -->
      <el-col :span="8">
        <el-card class="status-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><Connection /></el-icon>
              <span>连接状态</span>
            </div>
          </template>
          <div class="status-content">
            <div class="status-indicator">
              <div :class="['indicator-dot', store.isConnected ? 'connected' : 'disconnected']"></div>
              <span class="status-text">
                {{ store.isConnected ? '设备已连接' : '设备未连接' }}
              </span>
            </div>
            <div class="device-details" v-if="store.deviceInfo.deviceId">
              <p><strong>设备ID:</strong> {{ store.deviceInfo.deviceId }}</p>
              <p><strong>设备状态:</strong> {{ store.deviceInfo.online ? '在线' : '离线' }}</p>
              <p><strong>累计样本:</strong> {{ store.deviceInfo.totalRecords }}</p>
              <p><strong>最后上报:</strong> {{ formatTime(store.deviceInfo.lastSeen) }}</p>
            </div>
            <el-button type="primary" @click="handleConnect" class="connect-btn">
              {{ store.isConnected ? '重新连接' : '连接设备' }}
            </el-button>
          </div>
        </el-card>
      </el-col>

      <!-- Real-time Data Preview -->
      <el-col :span="16">
        <el-card class="preview-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><TrendCharts /></el-icon>
              <span>实时数据预览</span>
              <el-tag :type="store.isConnected ? 'success' : 'info'" size="small" class="ml-auto">
                {{ store.isConnected ? '实时' : '离线' }}
              </el-tag>
            </div>
          </template>
          <div class="chart-container">
            <RealTimeChart :max-points="100" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Recent Falls Table -->
    <el-card class="falls-card" shadow="hover" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <el-icon><Warning /></el-icon>
          <span>最近摔倒事件</span>
          <el-button type="primary" size="small" link class="ml-auto" @click="$router.push('/annotation')">
            查看全部
          </el-button>
        </div>
      </template>
      <DataTable
        :data="recentFalls"
        :columns="fallColumns"
        :actions="fallActions"
        :show-pagination="false"
        @action="handleFallAction"
      />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useMotionStore } from '../stores/motion'
import { wsService, DEFAULT_DEVICE_ID } from '../services/websocket'
import StatsCards from '../components/StatsCards.vue'
import RealTimeChart from '../components/RealTimeChart.vue'
import DataTable from '../components/DataTable.vue'

const router = useRouter()
const store = useMotionStore()

const recentFalls = computed(() => {
  return store.fallEvents.slice(0, 5).map(event => ({
    ...event,
    displayTime: formatTime(event.timestamp),
    displayConfidence: `${Math.round((event.confidence || 0) * 100)}%`
  }))
})

const fallColumns = [
  { prop: 'displayTime', label: '时间', width: '180' },
  { prop: 'type', label: '摔倒类型', width: '120' },
  { prop: 'displayConfidence', label: '置信度', width: '100' }
]

const fallActions = [
  { key: 'view', label: '查看', type: 'primary' },
  { key: 'annotate', label: '标注', type: 'warning' }
]

const formatTime = (value) => {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}

const handleConnect = () => {
  ElMessageBox.prompt('请输入设备ID', '连接设备', {
    confirmButtonText: '连接',
    cancelButtonText: '取消',
    inputPattern: /^\S+$/,
    inputErrorMessage: '请输入有效的设备ID',
    inputValue: wsService.deviceId || DEFAULT_DEVICE_ID
  }).then(({ value }) => {
    wsService.connect(value)
    store.setDeviceInfo({ deviceId: value })
    ElMessage.success('正在连接...')
    store.loadDeviceInfo(value)
  }).catch(() => {})
}

const handleFallAction = ({ key, row }) => {
  if (key === 'view') {
    ElMessage.info('查看事件详情')
  } else if (key === 'annotate') {
    router.push('/annotation')
  }
}

onMounted(() => {
  store.loadSummary()
  store.loadDeviceInfo(wsService.deviceId || DEFAULT_DEVICE_ID)
})
</script>

<style scoped>
.dashboard {
  padding: 0;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 24px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.status-card {
  height: 100%;
}

.status-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 12px;
}

.indicator-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

.indicator-dot.connected {
  background-color: #67c23a;
  box-shadow: 0 0 8px rgba(103, 194, 58, 0.5);
}

.indicator-dot.disconnected {
  background-color: #f56c6c;
  box-shadow: 0 0 8px rgba(245, 108, 108, 0.5);
}

@keyframes pulse {
  0% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.1);
    opacity: 0.8;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

.status-text {
  font-size: 16px;
  font-weight: 500;
}

.device-details {
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.8;
}

.device-details p {
  margin: 0;
}

.connect-btn {
  width: 100%;
}

.preview-card {
  height: 100%;
}

.chart-container {
  height: 250px;
}

.falls-card {
  margin-top: 20px;
}

.ml-auto {
  margin-left: auto;
}
</style>
