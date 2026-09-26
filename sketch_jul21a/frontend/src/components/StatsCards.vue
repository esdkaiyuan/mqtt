<template>
  <div class="stats-cards">
    <el-row :gutter="20">
      <el-col :span="6">
        <div class="stats-card">
          <div class="card-header">
            <el-icon class="card-icon" :size="24" color="#409eff"><DataLine /></el-icon>
            <span class="card-label">总数据量</span>
          </div>
          <div class="card-value">{{ formatNumber(store.stats.totalSamples) }}</div>
          <div class="card-footer">
            <span class="footer-text">传感器数据样本总数</span>
          </div>
        </div>
      </el-col>

      <el-col :span="6">
        <div class="stats-card">
          <div class="card-header">
            <el-icon class="card-icon" :size="24" color="#f56c6c"><Warning /></el-icon>
            <span class="card-label">摔倒次数</span>
          </div>
          <div class="card-value danger">{{ formatNumber(store.stats.totalFalls) }}</div>
          <div class="card-footer">
            <span class="footer-text">累计检测到的摔倒事件</span>
          </div>
        </div>
      </el-col>

      <el-col :span="6">
        <div class="stats-card">
          <div class="card-header">
            <el-icon class="card-icon" :size="24" color="#67c23a"><TrendCharts /></el-icon>
            <span class="card-label">今日数据</span>
          </div>
          <div class="card-value success">{{ formatNumber(store.stats.todaySamples) }}</div>
          <div class="card-footer">
            <span class="footer-text">今日采集的数据样本</span>
          </div>
        </div>
      </el-col>

      <el-col :span="6">
        <div class="stats-card">
          <div class="card-header">
            <el-icon class="card-icon" :size="24" color="#e6a23c"><Timer /></el-icon>
            <span class="card-label">今日摔倒</span>
          </div>
          <div class="card-value warning">{{ formatNumber(store.stats.todayFalls) }}</div>
          <div class="card-footer">
            <span class="footer-text">今日检测到的摔倒事件</span>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { useMotionStore } from '../stores/motion'

const store = useMotionStore()

const formatNumber = (num) => {
  if (num >= 1000000) {
    return (num / 1000000).toFixed(1) + 'M'
  } else if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'K'
  }
  return num.toString()
}
</script>

<style scoped>
.stats-cards {
  margin-bottom: 24px;
}

.stats-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  transition: transform 0.2s, box-shadow 0.2s;
  height: 100%;
}

.stats-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.card-label {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.card-value {
  font-size: 32px;
  font-weight: 700;
  color: #1a1a1a;
  margin-bottom: 16px;
}

.card-value.danger {
  color: #f56c6c;
}

.card-value.success {
  color: #67c23a;
}

.card-value.warning {
  color: #e6a23c;
}

.card-footer {
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
}

.footer-text {
  font-size: 12px;
  color: #999;
}
</style>
