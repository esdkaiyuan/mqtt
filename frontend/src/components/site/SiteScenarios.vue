<template>
  <SiteSection
    id="scenarios"
    theme="white"
    title="丰富的应用场景"
    desc="覆盖多个行业领域，满足多样化的物联网需求"
  >
    <div class="scenarios-grid">
      <div class="scenario-card" v-for="scenario in scenarios" :key="scenario.title">
        <div class="scenario-card__icon">
          <svg-icon :name="scenario.icon" :size="22" :color="'var(--color-primary)'" />
        </div>
        <h3 class="scenario-card__title">{{ scenario.title }}</h3>
        <p class="scenario-card__desc">{{ scenario.desc }}</p>
      </div>
    </div>
  </SiteSection>
</template>

<script setup>
import SiteSection from './SiteSection.vue'
import SvgIcon from '@/components/Icon.vue'

const scenarios = [
  { icon: 'leaf', title: '智慧农业', desc: '土壤监测、环境感知、智能灌溉' },
  { icon: 'factory', title: '工业监测', desc: '设备监控、能耗管理、故障预警' },
  { icon: 'building', title: '智能楼宇', desc: '温控系统、照明管理、能耗优化' },
  { icon: 'droplet', title: '环境监测', desc: '空气质量、水质检测、噪音监控' },
  { icon: 'home', title: '智能家居', desc: '家电控制、安防监控、场景联动' },
  { icon: 'package', title: '资产追踪', desc: '位置监控、仓储管理、冷链物流' }
]
</script>

<style scoped>
.scenarios-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-2xl);
}

.scenario-card {
  padding: var(--spacing-3xl);
  border-radius: var(--border-radius-md);
  border: 1px solid var(--color-border-light);
  border-left: 4px solid var(--color-primary);
  background: var(--color-card);
  box-shadow: var(--shadow-card);
  transition: all var(--transition-base);
}

.scenario-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.scenario-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--border-radius);
  background: var(--color-primary-light);
  margin-bottom: var(--spacing-lg);
}

.scenario-card__title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-sm);
}

.scenario-card__desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
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