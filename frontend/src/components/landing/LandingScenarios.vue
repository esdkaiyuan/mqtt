<template>
  <LandingSection
    id="scenarios"
    theme="white"
    title="丰富的应用场景"
    desc="覆盖多个行业领域，满足多样化的物联网需求"
  >
    <div class="scenarios-grid">
      <div
        v-for="scenario in scenarios"
        :key="scenario.title"
        class="scenario-card"
        :style="{ borderColor: scenario.color }"
      >
        <div class="scenario-icon" :style="{ background: scenario.bg }">
          {{ scenario.icon }}
        </div>
        <h3 class="scenario-title">{{ scenario.title }}</h3>
        <p class="scenario-desc">{{ scenario.desc }}</p>
      </div>
    </div>
  </LandingSection>
</template>

<script setup>
import LandingSection from './LandingSection.vue'

const scenarios = [
  { icon: '🌾', title: '智慧农业', desc: '土壤监测、环境感知、智能灌溉', color: '#00B42A', bg: '#E8FFEA' },
  { icon: '🏭', title: '工业监测', desc: '设备监控、能耗管理、故障预警', color: '#165DFF', bg: '#E8F3FF' },
  { icon: '🏢', title: '智能楼宇', desc: '温控系统、照明管理、能耗优化', color: '#722ED1', bg: '#F3E8FF' },
  { icon: '🌿', title: '环境监测', desc: '空气质量、水质检测、噪音监控', color: '#13C2C2', bg: '#E6FFFB' },
  { icon: '🏠', title: '智能家居', desc: '家电控制、安防监控、场景联动', color: '#FF7D00', bg: '#FFF3E8' },
  { icon: '📦', title: '资产追踪', desc: '位置监控、仓储管理、冷链物流', color: '#F53F3F', bg: '#FFECEC' },
]
</script>

<style scoped>
.scenarios-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.scenario-card {
  padding: 32px;
  border-radius: 12px;
  border-left: 4px solid;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  transition: all 0.3s ease;
}

.scenario-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  transform: translateY(-2px);
}

.scenario-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  margin-bottom: 16px;
}

.scenario-title {
  font-size: 16px;
  font-weight: 600;
  color: #1D2129;
  margin-bottom: 8px;
}

.scenario-desc {
  font-size: 13px;
  color: #86909C;
  line-height: 1.7;
}

@media (max-width: 1024px) {
  .scenarios-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .scenarios-grid {
    grid-template-columns: 1fr;
  }
}
</style>
