<template>
  <SiteSection
    id="devices"
    theme="gray"
    title="广泛的设备兼容性"
    desc="支持各类工业级和消费级物联网设备接入"
  >
    <div class="device-grid">
      <div class="device-card" v-for="device in devices" :key="device.name">
        <div class="device-card__icon">
          <svg-icon :name="device.icon" :size="22" :color="'var(--color-primary)'" />
        </div>
        <div class="device-card__name">{{ device.name }}</div>
        <div class="device-card__desc">{{ device.desc }}</div>
      </div>
    </div>
  </SiteSection>
</template>

<script setup>
import SiteSection from './SiteSection.vue'
import SvgIcon from '@/components/Icon.vue'

const devices = [
  { icon: 'radio', name: 'DTU', desc: '串口转 4G 无线传输' },
  { icon: 'hard-drive', name: 'RTU', desc: '远程终端控制单元' },
  { icon: 'sensor', name: '传感器', desc: '温湿度 / 压力 / 流量' },
  { icon: 'cpu', name: 'PLC', desc: '可编程逻辑控制器' },
  { icon: 'camera', name: '摄像头', desc: '工业视频监控' },
  { icon: 'wifi', name: '网关', desc: '协议转换与数据汇聚' }
]
</script>

<style scoped>
.device-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: var(--spacing-xl);
}

.device-card {
  background: var(--color-card);
  border-radius: var(--border-radius-md);
  padding: var(--spacing-3xl) var(--spacing-xl);
  text-align: center;
  transition: all var(--transition-base);
  border: 1px solid var(--color-border-light);
}

.device-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.device-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin: 0 auto var(--spacing-md);
  border-radius: var(--border-radius);
  background: var(--color-primary-light);
}

.device-card__name {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: 6px;
}

.device-card__desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
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