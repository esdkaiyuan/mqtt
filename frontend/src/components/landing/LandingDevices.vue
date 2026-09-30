<template>
  <LandingSection
    id="devices"
    theme="gray"
    title="广泛的设备兼容性"
    desc="支持各类工业级和消费级物联网设备接入"
  >
    <div class="device-grid">
      <div class="device-card" v-for="device in devices" :key="device.name">
        <div class="device-card-icon">{{ device.icon }}</div>
        <div class="device-card-name">{{ device.name }}</div>
        <div class="device-card-desc">{{ device.desc }}</div>
      </div>
    </div>
  </LandingSection>
</template>

<script setup>
import LandingSection from './LandingSection.vue'

const devices = [
  { icon: '📡', name: 'DTU', desc: '串口转4G无线传输' },
  { icon: '🖥', name: 'RTU', desc: '远程终端控制单元' },
  { icon: '🌡', name: '传感器', desc: '温湿度/压力/流量' },
  { icon: '🏭', name: 'PLC', desc: '可编程逻辑控制器' },
  { icon: '📷', name: '摄像头', desc: '工业视频监控' },
  { icon: '🌐', name: '网关', desc: '协议转换与数据汇聚' },
]
</script>

<style scoped>
.device-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 20px;
}

.device-card {
  background: #fff;
  border-radius: 12px;
  padding: 32px 20px;
  text-align: center;
  transition: all 0.3s ease;
  border: 1px solid #F2F3F5;
}

.device-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  transform: translateY(-2px);
}

.device-card-icon {
  font-size: 36px;
  margin-bottom: 12px;
}

.device-card-name {
  font-size: 15px;
  font-weight: 600;
  color: #1D2129;
  margin-bottom: 6px;
}

.device-card-desc {
  font-size: 12px;
  color: #86909C;
}

@media (max-width: 1024px) {
  .device-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .device-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
