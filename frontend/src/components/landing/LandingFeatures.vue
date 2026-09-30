<template>
  <LandingSection
    id="features"
    theme="white"
    title="全栈MQTT解决方案"
    desc="从设备接入到数据可视化，一站式管理您的物联网设备"
  >
    <div class="features-grid">
      <div class="feature-card" v-for="feature in features" :key="feature.title">
        <div class="feature-icon" :style="{ background: feature.bg }">
          <span class="feature-icon-emoji">{{ feature.icon }}</span>
        </div>
        <h3 class="feature-title">{{ feature.title }}</h3>
        <p class="feature-desc">{{ feature.desc }}</p>
      </div>
    </div>
  </LandingSection>
</template>

<script setup>
import LandingSection from './LandingSection.vue'

const features = [
  { icon: '📡', title: '设备接入', desc: '支持MQTT/TCP/WebSocket多种协议，轻松接入各类传感器、网关、DTU设备', bg: '#E8F3FF' },
  { icon: '📊', title: '数据采集', desc: '实时采集设备上报数据，支持JSON/二进制等多种数据格式解析与存储', bg: '#E8FFEA' },
  { icon: '📈', title: '数据可视化', desc: '丰富的图表组件，实时展示设备状态、数据趋势、统计分析', bg: '#FFF3E8' },
  { icon: '📱', title: 'APP开发', desc: '低代码/零代码构建设备管理应用，快速生成设备监控面板', bg: '#F3E8FF' },
  { icon: '🔔', title: '报警管理', desc: '多级报警规则配置，支持短信、邮件、Webhook等多渠道通知', bg: '#FFECEC' },
  { icon: '⚡', title: '自动化规则', desc: '可视化流程编排，设备联动自动化，条件触发自动执行', bg: '#E8F3FF' },
  { icon: '🔌', title: '开放API', desc: '完整的RESTful API与Webhook回调，轻松集成到现有系统', bg: '#E8FFEA' },
  { icon: '🔐', title: '安全可靠', desc: 'JWT认证、设备鉴权、通信加密，企业级安全保障', bg: '#FFF3E8' },
]
</script>

<style scoped>
.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.feature-card {
  padding: 32px 24px;
  border-radius: 12px;
  border: 1px solid #F2F3F5;
  transition: all 0.3s ease;
  cursor: default;
}

.feature-card:hover {
  border-color: #165DFF;
  box-shadow: 0 4px 16px rgba(22, 93, 255, 0.08);
  transform: translateY(-2px);
}

.feature-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.feature-icon-emoji {
  font-size: 24px;
}

.feature-title {
  font-size: 16px;
  font-weight: 600;
  color: #1D2129;
  margin-bottom: 8px;
}

.feature-desc {
  font-size: 13px;
  color: #86909C;
  line-height: 1.7;
}

@media (max-width: 1024px) {
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .features-grid {
    grid-template-columns: 1fr;
  }
}
</style>
