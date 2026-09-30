<template>
  <div class="dashboard-page page-container">
    <div class="page-header">
      <h2>仪表盘</h2>
      <div class="header-actions">
        <el-select v-model="trendDays" size="small" style="width: 120px" @change="loadAllData">
          <el-option label="近7天" :value="7" />
          <el-option label="近14天" :value="14" />
          <el-option label="近30天" :value="30" />
        </el-select>
        <button class="btn-secondary" @click="loadAllData" :disabled="loading">
          <svg-icon name="refresh" :size="14" :class="{ 'spin': loading }" />
          刷新
        </button>
      </div>
    </div>

    <!-- Stat Cards -->
    <div class="stats-grid">
      <div class="stat-card card">
        <div class="stat-icon" style="background: #E8F3FF;">
          <svg-icon name="device" :size="24" color="#165DFF" />
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ overview.totalDevices || 0 }}</div>
          <div class="stat-label">设备总数</div>
        </div>
      </div>
      <div class="stat-card card">
        <div class="stat-icon" style="background: #E8FFEA;">
          <svg-icon name="online" :size="24" color="#00B42A" />
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ overview.onlineDevices || 0 }}</div>
          <div class="stat-label">在线设备</div>
        </div>
      </div>
      <div class="stat-card card">
        <div class="stat-icon" style="background: #FFECEC;">
          <svg-icon name="offline" :size="24" color="#F53F3F" />
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ overview.offlineDevices || 0 }}</div>
          <div class="stat-label">离线设备</div>
        </div>
      </div>
      <div class="stat-card card">
        <div class="stat-icon" style="background: #FFF3E8;">
          <svg-icon name="message" :size="24" color="#FF7D00" />
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ overview.todayMessages || 0 }}</div>
          <div class="stat-label">今日消息</div>
        </div>
      </div>
    </div>

    <!-- Charts Row -->
    <div class="charts-row">
      <div class="chart-card card">
        <div class="chart-header">
          <h3 class="chart-title">设备状态分布</h3>
          <span class="chart-subtitle" v-if="!statusData.length">暂无数据</span>
        </div>
        <div ref="statusChartRef" class="chart-container"></div>
      </div>
      <div class="chart-card card">
        <div class="chart-header">
          <h3 class="chart-title">设备类型分布</h3>
          <span class="chart-subtitle" v-if="!typeData.length">暂无数据</span>
        </div>
        <div ref="typeChartRef" class="chart-container"></div>
      </div>
    </div>

    <!-- Message Trend Chart -->
    <div class="chart-card card full-width">
      <div class="chart-header">
        <h3 class="chart-title">消息量趋势（近{{ trendDays }}天）</h3>
        <span class="chart-subtitle" v-if="!trendData.length">暂无数据</span>
      </div>
      <div ref="trendChartRef" class="chart-container"></div>
    </div>

    <!-- Recent Messages -->
    <div class="recent-messages card">
      <div class="recent-header">
        <h3 class="chart-title">最近消息</h3>
        <router-link to="/messages" class="btn-link">查看全部</router-link>
      </div>
      <div v-if="recentMessages.length === 0" class="empty-state-small">
        <svg-icon name="message" :size="32" color="#E0E0E0" />
        <p>暂无消息数据</p>
      </div>
      <div v-else class="recent-table-wrapper">
        <table class="recent-table">
          <thead>
            <tr>
              <th>Topic</th>
              <th>方向</th>
              <th>载荷预览</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="msg in recentMessages" :key="msg.id">
              <td class="topic-cell">{{ msg.topic }}</td>
              <td>
                <span :class="['direction-badge', msg.direction?.toLowerCase()]">
                  {{ msg.direction === 'PUBLISH' ? '发送' : '接收' }}
                </span>
              </td>
              <td class="payload-cell">{{ truncatePayload(msg.payload) }}</td>
              <td class="time-cell">{{ formatTime(msg.receivedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, onBeforeUnmount } from 'vue'
import echarts from '@/utils/echarts'
import { statsApi } from '@/api/stats'
import { messageApi } from '@/api/message'
import SvgIcon from '@/components/Icon.vue'

const trendDays = ref(7)
const loading = ref(false)
const overview = ref({})
const recentMessages = ref([])
const statusData = ref([])
const typeData = ref([])
const trendData = ref([])

const statusChartRef = ref(null)
const typeChartRef = ref(null)
const trendChartRef = ref(null)

let statusChart = null
let typeChart = null
let trendChart = null

async function loadOverview() {
  try {
    const res = await statsApi.getOverview()
    overview.value = res || {}
  } catch (e) {
    console.error('加载概览数据失败:', e)
  }
}

async function loadStatusChart() {
  try {
    const res = await statsApi.getDeviceStatusDistribution()
    statusData.value = res || []
    renderStatusChart()
  } catch (e) {
    console.error('加载状态分布失败:', e)
  }
}

async function loadTypeChart() {
  try {
    const res = await statsApi.getDeviceTypeDistribution()
    typeData.value = res || []
    renderTypeChart()
  } catch (e) {
    console.error('加载类型分布失败:', e)
  }
}

async function loadTrendChart() {
  try {
    const res = await statsApi.getMessageTrend(trendDays.value)
    trendData.value = res || []
    renderTrendChart()
  } catch (e) {
    console.error('加载趋势数据失败:', e)
  }
}

async function loadRecentMessages() {
  try {
    const res = await messageApi.getRecent(10)
    recentMessages.value = res || []
  } catch (e) {
    console.error('加载最近消息失败:', e)
  }
}

async function loadAllData() {
  loading.value = true
  try {
    await Promise.all([loadOverview(), loadStatusChart(), loadTypeChart(), loadTrendChart(), loadRecentMessages()])
  } finally {
    loading.value = false
  }
}

function renderStatusChart() {
  if (!statusChart || !statusChartRef.value) return
  const data = statusData.value
  if (!data.length) {
    statusChart.clear()
    return
  }
  statusChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      bottom: 0,
      textStyle: { fontSize: 12, color: '#4E5969' }
    },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: false,
      itemStyle: {
        borderRadius: 6,
        borderColor: '#fff',
        borderWidth: 2
      },
      label: {
        show: true,
        formatter: '{b}\n{c}',
        fontSize: 12,
        color: '#1D2129'
      },
      emphasis: {
        label: { fontSize: 14, fontWeight: 'bold' },
        itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.1)' }
      },
      data: data.map(item => ({
        name: { ONLINE: '在线', OFFLINE: '离线', INACTIVE: '未激活' }[item.status] || item.status,
        value: Number(item.count) || 0,
        itemStyle: {
          color: { ONLINE: '#00B42A', OFFLINE: '#F53F3F', INACTIVE: '#86909C' }[item.status] || '#86909C'
        }
      }))
    }]
  }, true)
}

function renderTypeChart() {
  if (!typeChart || !typeChartRef.value) return
  const data = typeData.value
  if (!data.length) {
    typeChart.clear()
    return
  }
  const typeLabels = { sensor: '传感器', gateway: '网关', actuator: '执行器', other: '其他' }
  typeChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: '{b}: {c} 台'
    },
    grid: { left: 60, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: data.map(item => typeLabels[item.device_type] || item.device_type),
      axisLine: { lineStyle: { color: '#E5E6EB' } },
      axisLabel: { fontSize: 12, color: '#4E5969' }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#F2F3F5' } },
      axisLabel: { fontSize: 12, color: '#86909C' }
    },
    series: [{
      type: 'bar',
      data: data.map(item => Number(item.count) || 0),
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#4080FF' },
          { offset: 1, color: '#165DFF' }
        ])
      },
      barWidth: 40,
      label: {
        show: true,
        position: 'top',
        fontSize: 11,
        color: '#4E5969'
      }
    }]
  }, true)
}

function renderTrendChart() {
  if (!trendChart || !trendChartRef.value) return
  const data = trendData.value
  if (!data.length) {
    trendChart.clear()
    return
  }
  trendChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: function(params) {
        const p = params[0]
        return p.axisValue + '<br/>消息量: <strong>' + p.value + '</strong>'
      }
    },
    grid: { left: 60, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: data.map(item => item.date),
      axisLine: { lineStyle: { color: '#E5E6EB' } },
      axisLabel: { fontSize: 11, color: '#86909C' }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#F2F3F5' } },
      axisLabel: { fontSize: 11, color: '#86909C' }
    },
    series: [{
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 3, color: '#165DFF' },
      itemStyle: { color: '#165DFF', borderWidth: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(22, 93, 255, 0.15)' },
          { offset: 1, color: 'rgba(22, 93, 255, 0)' }
        ])
      },
      data: data.map(item => Number(item.count) || 0)
    }]
  }, true)
}

function initCharts() {
  if (statusChartRef.value) {
    statusChart = echarts.init(statusChartRef.value)
  }
  if (typeChartRef.value) {
    typeChart = echarts.init(typeChartRef.value)
  }
  if (trendChartRef.value) {
    trendChart = echarts.init(trendChartRef.value)
  }
}

function formatTime(timeStr) {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function truncatePayload(payload) {
  if (!payload) return ''
  try {
    const obj = JSON.parse(payload)
    payload = JSON.stringify(obj)
  } catch {}
  return payload.length > 50 ? payload.slice(0, 50) + '...' : payload
}

function resizeCharts() {
  statusChart?.resize()
  typeChart?.resize()
  trendChart?.resize()
}

onMounted(async () => {
  initCharts()
  await loadAllData()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  statusChart?.dispose()
  typeChart?.dispose()
  trendChart?.dispose()
})
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

/* Stats Grid */
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

/* Charts */
.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}

.chart-card {
  padding: var(--spacing-lg);
}

.chart-card.full-width {
  margin-bottom: var(--spacing-lg);
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.chart-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.chart-subtitle {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.chart-container {
  width: 100%;
  height: 300px;
}

.chart-card.full-width .chart-container {
  height: 280px;
}

/* Recent Messages */
.recent-messages {
  padding: var(--spacing-lg);
}

.recent-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.btn-link {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
  text-decoration: none;
}

.btn-link:hover {
  color: var(--color-primary-hover);
}

.recent-table-wrapper {
  overflow-x: auto;
}

.recent-table {
  width: 100%;
  border-collapse: collapse;
}

.recent-table th {
  background: var(--color-bg);
  padding: 10px 14px;
  text-align: left;
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--color-text-tertiary);
  border-bottom: 1px solid var(--border-color-light);
}

.recent-table td {
  padding: 12px 14px;
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
  border-bottom: 1px solid var(--border-color-light);
}

.recent-table tbody tr:hover td {
  background: var(--color-bg);
}

.topic-cell {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-primary);
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.payload-cell {
  max-width: 250px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.time-cell {
  white-space: nowrap;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.direction-badge {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: var(--font-size-xs);
  font-weight: 500;
}

.direction-badge.publish {
  background: #E8F3FF;
  color: #165DFF;
}

.direction-badge.subscribe {
  background: var(--color-bg);
  color: var(--color-text-tertiary);
}

.empty-state-small {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-xl);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

@media (max-width: 1024px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .charts-row {
    grid-template-columns: 1fr;
  }
}
</style>
