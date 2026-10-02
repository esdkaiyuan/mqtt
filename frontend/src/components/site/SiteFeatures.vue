<template>
  <SiteSection
    id="features"
    theme="white"
    title="全栈 MQTT 解决方案"
    desc="从设备接入到数据可视化，一站式管理你的物联网设备"
  >
    <div class="features-grid">
      <div class="feature-card" v-for="feature in features" :key="feature.title">
        <div class="feature-card__icon">
          <svg-icon :name="feature.icon" :size="22" :color="'var(--color-primary)'" />
        </div>
        <h3 class="feature-card__title">{{ feature.title }}</h3>
        <p class="feature-card__desc">{{ feature.desc }}</p>
      </div>
    </div>
  </SiteSection>
</template>

<script setup>
import SiteSection from './SiteSection.vue'
import SvgIcon from '@/components/Icon.vue'

const features = [
  { icon: 'device', title: '设备接入', desc: '支持 MQTT/TCP/WebSocket 多种协议，轻松接入各类传感器、网关、DTU 设备' },
  { icon: 'cpu', title: '数据采集', desc: '实时采集设备上报数据，支持 JSON/二进制等多种数据格式解析与存储' },
  { icon: 'chart', title: '数据可视化', desc: '丰富的图表组件，实时展示设备状态、数据趋势、统计分析' },
  { icon: 'dashboard', title: '面板搭建', desc: '低代码构建设备管理面板，快速生成设备监控视图' },
  { icon: 'bell', title: '报警管理', desc: '多级报警规则配置，支持短信、邮件、Webhook 等多渠道通知' },
  { icon: 'bolt', title: '自动化规则', desc: '可视化流程编排，设备联动自动化，条件触发自动执行' },
  { icon: 'code', title: '开放 API', desc: '完整的 RESTful API 与 Webhook 回调，轻松集成到现有系统' },
  { icon: 'shield', title: '安全可靠', desc: 'JWT 认证、设备鉴权、通信加密，企业级安全保障' }
]
</script>

<style scoped>
.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--grid-gutter);
}

.feature-card {
  padding: var(--spacing-3xl) var(--spacing-2xl);
  border-radius: var(--border-radius-md);
  border: 1px solid var(--color-border-light);
  transition: all var(--transition-base);
}

.feature-card:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.feature-card__icon {
  width: 48px;
  height: 48px;
  border-radius: var(--border-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--spacing-lg);
  background: var(--color-primary-light);
}

.feature-card__title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-sm);
}

.feature-card__desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
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