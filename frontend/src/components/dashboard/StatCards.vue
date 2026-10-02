<template>
  <div class="stats-grid">
    <div v-for="card in CARDS" :key="card.key" class="stat-card card">
      <div class="stat-icon" :style="{ background: card.background }">
        <svg-icon :name="card.icon" :size="24" :color="card.color" />
      </div>
      <div class="stat-info">
        <div class="stat-value">{{ overview[card.key] || 0 }}</div>
        <div class="stat-label">{{ card.label }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import SvgIcon from '@/components/Icon.vue'

defineProps({
  overview: {
    type: Object,
    default: () => ({})
  }
})

const CARDS = [
  { key: 'totalDevices', label: '设备总数', icon: 'device', color: 'var(--color-primary)', background: 'var(--color-primary-light)' },
  { key: 'onlineDevices', label: '在线设备', icon: 'online', color: 'var(--color-success)', background: 'var(--color-success-light)' },
  { key: 'offlineDevices', label: '离线设备', icon: 'offline', color: 'var(--color-danger)', background: 'var(--color-danger-light)' },
  { key: 'todayMessages', label: '今日消息', icon: 'message', color: 'var(--color-warning)', background: 'var(--color-warning-light)' }
]
</script>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  transition: box-shadow 0.2s, transform 0.2s;
}

.stat-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--border-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

@media (max-width: 1024px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
