<template>
  <div class="three-d-view">
    <h1 class="page-title">3D姿态可视化</h1>

    <!-- Control Panel -->
    <el-card class="control-card" shadow="hover">
      <div class="control-bar">
        <div class="control-left">
          <el-button type="primary" @click="resetRotation">
            <el-icon><RefreshRight /></el-icon>
            重置姿态
          </el-button>
          <el-button @click="toggleAutoRotate">
            <el-icon><Aim /></el-icon>
            {{ autoRotate ? '停止旋转' : '自动旋转' }}
          </el-button>
          <el-button @click="toggleAxes">
            <el-icon><Coordinate /></el-icon>
            {{ showAxes ? '隐藏坐标轴' : '显示坐标轴' }}
          </el-button>
        </div>
        <div class="control-right">
          <el-select v-model="viewAngle" placeholder="视角" style="width: 120px;">
            <el-option label="正面" value="front" />
            <el-option label="侧面" value="side" />
            <el-option label="顶部" value="top" />
            <el-option label="自由" value="free" />
          </el-select>
          <el-slider v-model="zoom" :min="1" :max="10" :step="0.5" style="width: 150px;" />
        </div>
      </div>
    </el-card>

    <!-- 3D Scene -->
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="18">
        <el-card shadow="hover" class="scene-card">
          <div class="scene-container">
            <ThreeScene />
          </div>
        </el-card>
      </el-col>

      <!-- Data Panel -->
      <el-col :span="6">
        <el-card shadow="hover" class="data-panel">
          <template #header>
            <div class="card-header">
              <el-icon><DataLine /></el-icon>
              <span>传感器数据</span>
            </div>
          </template>

          <div class="data-section">
            <h4 class="section-title">加速度 (g)</h4>
            <div class="data-grid">
              <div class="data-item">
                <span class="data-label" style="color: #ef4444;">X:</span>
                <span class="data-value">{{ store.latestData.ax.toFixed(2) }}</span>
              </div>
              <div class="data-item">
                <span class="data-label" style="color: #22c55e;">Y:</span>
                <span class="data-value">{{ store.latestData.ay.toFixed(2) }}</span>
              </div>
              <div class="data-item">
                <span class="data-label" style="color: #3b82f6;">Z:</span>
                <span class="data-value">{{ store.latestData.az.toFixed(2) }}</span>
              </div>
            </div>
          </div>

          <el-divider />

          <div class="data-section">
            <h4 class="section-title">陀螺仪 (°/s)</h4>
            <div class="data-grid">
              <div class="data-item">
                <span class="data-label" style="color: #f59e0b;">X:</span>
                <span class="data-value">{{ store.latestData.gx.toFixed(2) }}</span>
              </div>
              <div class="data-item">
                <span class="data-label" style="color: #8b5cf6;">Y:</span>
                <span class="data-value">{{ store.latestData.gy.toFixed(2) }}</span>
              </div>
              <div class="data-item">
                <span class="data-label" style="color: #ec4899;">Z:</span>
                <span class="data-value">{{ store.latestData.gz.toFixed(2) }}</span>
              </div>
            </div>
          </div>

          <el-divider />

          <div class="data-section">
            <h4 class="section-title">旋转角度</h4>
            <div class="data-grid">
              <div class="data-item">
                <span class="data-label">俯仰:</span>
                <span class="data-value">{{ pitch.toFixed(1) }}°</span>
              </div>
              <div class="data-item">
                <span class="data-label">横滚:</span>
                <span class="data-value">{{ roll.toFixed(1) }}°</span>
              </div>
              <div class="data-item">
                <span class="data-label">偏航:</span>
                <span class="data-value">{{ yaw.toFixed(1) }}°</span>
              </div>
            </div>
          </div>

          <el-divider />

          <div class="data-section">
            <h4 class="section-title">连接状态</h4>
            <div class="connection-status">
              <div :class="['status-dot', store.isConnected ? 'connected' : 'disconnected']"></div>
              <span>{{ store.isConnected ? '已连接' : '未连接' }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Instructions -->
    <el-card shadow="hover" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <el-icon><QuestionFilled /></el-icon>
          <span>操作说明</span>
        </div>
      </template>
      <el-row :gutter="20">
        <el-col :span="8">
          <div class="instruction-item">
            <el-icon :size="24" color="#409eff"><Mouse /></el-icon>
            <div>
              <h4>鼠标拖动</h4>
              <p>左键拖动旋转视角</p>
            </div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="instruction-item">
            <el-icon :size="24" color="#67c23a"><ZoomIn /></el-icon>
            <div>
              <h4>滚轮缩放</h4>
              <p>滚动鼠标滚轮缩放场景</p>
            </div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="instruction-item">
            <el-icon :size="24" color="#f56c6c"><Pointer /></el-icon>
            <div>
              <h4>实时同步</h4>
              <p>立方体跟随传感器数据旋转</p>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useMotionStore } from '../stores/motion'
import ThreeScene from '../components/ThreeScene.vue'
import { ElMessage } from 'element-plus'

const store = useMotionStore()

const pitch = ref(0)
const roll = ref(0)
const yaw = ref(0)
const autoRotate = ref(false)
const showAxes = ref(true)
const viewAngle = ref('free')
const zoom = ref(3)

const resetRotation = () => {
  pitch.value = 0
  roll.value = 0
  yaw.value = 0
  ElMessage.success('姿态已重置')
}

const toggleAutoRotate = () => {
  autoRotate.value = !autoRotate.value
  ElMessage.info(autoRotate.value ? '自动旋转已开启' : '自动旋转已关闭')
}

const toggleAxes = () => {
  showAxes.value = !showAxes.value
  ElMessage.info(showAxes.value ? '坐标轴已显示' : '坐标轴已隐藏')
}
</script>

<style scoped>
.three-d-view {
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
}

.scene-card {
  height: 100%;
}

.scene-container {
  height: 500px;
  border-radius: 8px;
  overflow: hidden;
}

.data-panel {
  height: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.data-section {
  margin-bottom: 0;
}

.section-title {
  font-size: 13px;
  color: #666;
  margin: 0 0 12px 0;
}

.data-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.data-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.data-label {
  font-size: 13px;
  color: #666;
  font-weight: 500;
}

.data-value {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  font-family: 'Monaco', 'Consolas', monospace;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.status-dot.connected {
  background: #67c23a;
  box-shadow: 0 0 8px rgba(103, 194, 58, 0.5);
}

.status-dot.disconnected {
  background: #f56c6c;
  box-shadow: 0 0 8px rgba(245, 108, 108, 0.5);
}

.instruction-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.instruction-item h4 {
  margin: 0 0 4px 0;
  font-size: 14px;
  color: #1a1a1a;
}

.instruction-item p {
  margin: 0;
  font-size: 12px;
  color: #666;
}
</style>
