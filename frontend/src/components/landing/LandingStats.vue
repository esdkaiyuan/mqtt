<template>
  <LandingSection theme="dark" compact>
    <div class="stats-grid">
      <div class="stat-item" v-for="stat in stats" :key="stat.label">
        <div class="stat-number">{{ stat.value }}</div>
        <div class="stat-label">{{ stat.label }}</div>
      </div>
    </div>
  </LandingSection>
</template>

<script setup>
import LandingSection from './LandingSection.vue'

const stats = [
  { value: '10,000+', label: '设备接入规模' },
  { value: '<50ms', label: '消息转发延迟' },
  { value: '99.9%', label: '平台可用性' },
  { value: '500+', label: '企业客户' },
]
</script>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 32px;
}

.stat-item {
  text-align: center;
}

.stat-number {
  font-size: 48px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 8px;
  letter-spacing: -0.02em;
}

.stat-label {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
