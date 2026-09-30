<template>
  <LandingSection
    id="api"
    theme="gray"
    title="开发者友好"
    desc="完善的API接口，轻松集成到您的应用中"
  >
    <div class="api-showcase">
      <div class="api-code-block">
        <div class="code-header">
          <span class="code-dot red"></span>
          <span class="code-dot yellow"></span>
          <span class="code-dot green"></span>
          <span class="code-title">获取设备列表</span>
        </div>
        <pre class="code-content"><code>curl -X GET \
  https://your-server/api/external/v1/devices \
  -H "X-API-Key: your-api-key"</code></pre>
      </div>

      <div class="api-features-list">
        <div class="api-feature-item" v-for="api in apiFeatures" :key="api.title">
          <svg-icon :name="api.icon" :size="20" color="#165DFF" />
          <div>
            <h4>{{ api.title }}</h4>
            <p>{{ api.desc }}</p>
          </div>
        </div>
      </div>
    </div>
  </LandingSection>
</template>

<script setup>
import SvgIcon from '@/components/Icon.vue'
import LandingSection from './LandingSection.vue'

const apiFeatures = [
  { icon: 'device', title: '设备管理API', desc: '创建设备、查询状态、更新配置' },
  { icon: 'message', title: '消息发送API', desc: '向设备发送指令与配置' },
  { icon: 'history', title: '数据查询API', desc: '获取设备历史数据与趋势' },
]
</script>

<style scoped>
.api-showcase {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 48px;
  align-items: center;
}

.api-code-block {
  background: #1D2129;
  border-radius: 12px;
  overflow: hidden;
}

.code-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.05);
}

.code-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.code-dot.red { background: #F53F3F; }
.code-dot.yellow { background: #FF7D00; }
.code-dot.green { background: #00B42A; }

.code-title {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  margin-left: 8px;
}

.code-content {
  padding: 20px;
  margin: 0;
}

.code-content code {
  font-family: var(--font-family-mono);
  font-size: 13px;
  color: #A9E1FF;
  line-height: 1.8;
  white-space: pre;
}

.api-features-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.api-feature-item {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.api-feature-item h4 {
  font-size: 15px;
  font-weight: 600;
  color: #1D2129;
  margin-bottom: 4px;
}

.api-feature-item p {
  font-size: 13px;
  color: #86909C;
}

@media (max-width: 1024px) {
  .api-showcase {
    grid-template-columns: 1fr;
  }
}
</style>
