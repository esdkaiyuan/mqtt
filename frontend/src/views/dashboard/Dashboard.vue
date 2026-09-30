<template>
  <div class="dashboard-page page-container">
    <div class="page-header">
      <h2>仪表盘</h2>
      <div class="header-actions">
        <el-select v-model="trendDays" size="small" style="width: 120px" @change="refetchAll">
          <el-option label="近7天" :value="7" />
          <el-option label="近14天" :value="14" />
          <el-option label="近30天" :value="30" />
        </el-select>
        <button class="btn-secondary" :disabled="loading" @click="refetchAll">
          <svg-icon name="refresh" :size="14" :class="{ spin: loading }" />
          刷新
        </button>
      </div>
    </div>

    <StatCards :overview="overview" />

    <div class="charts-row">
      <DeviceStatusChart :data="statusData" />
      <DeviceTypeChart :data="typeData" />
    </div>

    <MessageTrendChart :data="trendData" :days="trendDays" />

    <RecentMessages :messages="recentMessages" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useDashboardData } from '@/composables/useDashboardData'
import SvgIcon from '@/components/Icon.vue'
import StatCards from '@/components/dashboard/StatCards.vue'
import DeviceStatusChart from '@/components/dashboard/DeviceStatusChart.vue'
import DeviceTypeChart from '@/components/dashboard/DeviceTypeChart.vue'
import MessageTrendChart from '@/components/dashboard/MessageTrendChart.vue'
import RecentMessages from '@/components/dashboard/RecentMessages.vue'

const trendDays = ref(7)

const { overview, statusData, typeData, trendData, recentMessages, loading, refetchAll } =
  useDashboardData(trendDays)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-lg);
}

.page-header h2 {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.header-actions {
  display: flex;
  gap: var(--spacing-md);
}

.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 6px 14px;
  background: #fff;
  color: var(--color-text-regular);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-sm);
  cursor: pointer;
  font-family: var(--font-family);
  transition: all 0.2s;
}

.btn-secondary:hover:not(:disabled) {
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.btn-secondary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary .svg-icon {
  transition: transform 0.4s;
}

.btn-secondary .spin {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}

@media (max-width: 1024px) {
  .charts-row {
    grid-template-columns: 1fr;
  }
}
</style>
