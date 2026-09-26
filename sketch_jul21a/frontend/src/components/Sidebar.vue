<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <div class="logo-container">
        <el-icon class="logo-icon" :size="32"><Monitor /></el-icon>
        <div class="logo-text">
          <h1 class="logo-title">摔倒检测</h1>
          <p class="logo-subtitle">实时监控系统</p>
        </div>
      </div>
    </div>

    <el-menu
      :default-active="activeMenu"
      class="sidebar-menu"
      background-color="transparent"
      text-color="#b0c4de"
      active-text-color="#ffffff"
      router
    >
      <el-menu-item index="/dashboard" class="menu-item">
        <el-icon><DataBoard /></el-icon>
        <span>仪表板</span>
      </el-menu-item>

      <el-menu-item index="/waveform" class="menu-item">
        <el-icon><TrendCharts /></el-icon>
        <span>实时波形</span>
      </el-menu-item>

      <el-menu-item index="/3d-view" class="menu-item">
        <el-icon><View /></el-icon>
        <span>3D姿态</span>
      </el-menu-item>

      <el-menu-item index="/annotation" class="menu-item">
        <el-icon><Edit /></el-icon>
        <span>数据标注</span>
      </el-menu-item>

      <el-menu-item index="/export" class="menu-item">
        <el-icon><Download /></el-icon>
        <span>数据导出</span>
      </el-menu-item>
    </el-menu>

    <div class="sidebar-footer">
      <div class="device-info" v-if="store.deviceInfo.deviceId">
        <el-icon><Cpu /></el-icon>
        <span class="device-id">{{ store.deviceInfo.deviceId }}</span>
      </div>
      <div class="version-info">v1.0.0</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useMotionStore } from '../stores/motion'

const route = useRoute()
const store = useMotionStore()

const activeMenu = computed(() => {
  return route.path
})
</script>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo-container {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  color: #60a5fa;
}

.logo-text {
  display: flex;
  flex-direction: column;
}

.logo-title {
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
  margin: 0;
  line-height: 1.2;
}

.logo-subtitle {
  font-size: 11px;
  color: #94a3b8;
  margin: 2px 0 0 0;
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  padding: 12px 0;
}

.menu-item {
  margin: 4px 12px;
  border-radius: 8px;
  height: 48px;
  line-height: 48px;
}

.menu-item:hover {
  background-color: rgba(255, 255, 255, 0.08) !important;
}

.menu-item.is-active {
  background-color: rgba(96, 165, 250, 0.2) !important;
  color: #ffffff !important;
}

.sidebar-footer {
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.device-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #94a3b8;
  font-size: 12px;
  margin-bottom: 8px;
}

.device-id {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-info {
  font-size: 11px;
  color: #64748b;
  text-align: center;
}
</style>
