<template>
  <div class="navbar">
    <div class="navbar-left">
      <h2 class="page-title">{{ currentTitle }}</h2>
    </div>
    <div class="navbar-right">
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
              清除数据
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useMotionStore } from '../stores/motion'
import { wsService } from '../services/websocket'
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
  ElMessageBox.prompt('请输入WebSocket地址', '连接设备', {
    confirmButtonText: '连接',
    cancelButtonText: '取消',
    inputPattern: /^ws:\/\/.+/,
    inputErrorMessage: '请输入有效的WebSocket地址 (ws://...)',
    inputValue: 'ws://localhost:8080/ws'
  }).then(({ value }) => {
    wsService.connect(value)
    ElMessage.success('正在连接...')
  }).catch(() => {})
}

const handleDisconnect = () => {
  wsService.disconnect()
  ElMessage.info('已断开连接')
}

const handleClearData = () => {
  ElMessageBox.confirm('确定要清除所有实时数据吗？', '确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    store.clearRealtimeData()
    store.resetStats()
    ElMessage.success('数据已清除')
  }).catch(() => {})
}
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

.connection-tag {
  display: flex;
  align-items: center;
  gap: 6px;
}

.connection-icon {
  font-size: 14px;
}
</style>
