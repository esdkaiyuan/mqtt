<template>
  <div class="dashboard-page page">
    <PageHeader title="概览" desc="平台设备与消息的整体运行情况">
      <template #actions>
        <el-select v-model="trendDays" size="default" style="width: 120px" @change="refetchAll">
          <el-option label="近7天" :value="7" />
          <el-option label="近14天" :value="14" />
          <el-option label="近30天" :value="30" />
        </el-select>
        <el-button :loading="loading" @click="refetchAll">
          <svg-icon name="refresh" :size="14" />
          刷新
        </el-button>
      </template>
    </PageHeader>

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
import PageHeader from '@/components/common/PageHeader.vue'
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
.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--grid-gutter);
  margin-bottom: var(--grid-gutter);
}

@media (max-width: 1024px) {
  .charts-row {
    grid-template-columns: 1fr;
  }
}
</style>
