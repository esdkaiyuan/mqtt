<template>
  <div class="navbar">
    <div class="navbar-left">
      <h2 class="page-title">{{ currentTitle }}</h2>
    </div>
    <div class="navbar-right">
      <div class="device-info" v-if="store.deviceInfo.deviceId">
        <el-tag type="info" effect="plain" size="small">
          <el-icon><Monitor /></el-icon>
          {{ store.deviceInfo.deviceId }}
        </el-tag>
        <el-tag type="info" effect="plain" size="small">
          累计 {{ store.deviceInfo.totalRecords ?? 0 }} 条
        </el-tag>
      </div>
      <el-tag :type="connectionStatusType" effect="dark" class="connection-tag">
        <el-icon class="connection-icon"><Connection /></el-icon>
        {{ connectionStatusText }}
      </el-tag>
      <el-dropdown trigger="click">
        <el-button type="primary" plain>
          <el-icon><Setting /></el-icon>
          设置
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="handleConnect">
              <el-icon><Connection /></el-icon>
              连接设备
            </el-dropdown-item>
            <el-dropdown-item @click="handleDisconnect">
              <el-icon><Close /></el-icon>
              断开连接
            </el-dropdown-item>
            <el-dropdown-item divided @click="handleClearData">
              <el-icon><Delete /></el-icon>
              清除实时数据
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useMotionStore } from '../stores/motion'
import { wsService, DEFAULT_DEVICE_ID } from '../services/websocket'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const store = useMotionStore()

const currentTitle = computed(() => {
  return route.meta.title || '摔倒检测系统'
})

const connectionStatusType = computed(() => {
  return store.isConnected ? 'success' : 'danger'
})

const connectionStatusText = computed(() => {
  return store.isConnected ? '已连接' : '未连接'
})

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
    store.loadDeviceInfo(value)
    ElMessage.success('正在连接...')
  }).catch(() => {})
}

const handleDisconnect = () => {
  wsService.disconnect()
  ElMessage.info('已断开连接')
}

const handleClearData = () => {
  ElMessageBox.confirm('确定要清除本地缓存的实时数据吗？已入库的数据不会被删除。', '确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    store.clearRealtimeData()
    // Counters come from the database, so re-read them instead of zeroing.
    await store.loadSummary()
    ElMessage.success('实时缓存已清除')
  }).catch(() => {})
}

onMounted(() => {
  store.loadDeviceInfo(wsService.deviceId || DEFAULT_DEVICE_ID)
})
</script>

<style scoped>
.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  height: 100%;
}

.navbar-left {
  display: flex;
  align-items: center;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.device-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.device-info .el-tag {
  display: flex;
  align-items: center;
  gap: 4px;
}

.connection-tag {
  display: flex;
  align-items: center;
  gap: 6px;
}

.connection-icon {
  font-size: 14px;
}
</style>