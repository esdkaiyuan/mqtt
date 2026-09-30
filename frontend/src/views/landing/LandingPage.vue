<template>
  <div class="landing-page">
    <!-- Navigation -->
    <nav class="landing-nav" :class="{ scrolled: isScrolled }">
      <div class="nav-container">
        <div class="nav-logo">
          <svg class="logo-icon" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect width="32" height="32" rx="8" fill="#165DFF" />
            <path d="M8 16C8 11.6 11.6 8 16 8C20.4 8 24 11.6 24 16C24 20.4 20.4 24 16 24" stroke="white" stroke-width="2.5" stroke-linecap="round" />
            <circle cx="16" cy="16" r="3" fill="white" />
            <path d="M16 10V13M16 19V22M13 16H10M19 16H22" stroke="white" stroke-width="2" stroke-linecap="round" />
          </svg>
          <span class="logo-text">MQTT Cloud</span>
        </div>

        <div class="nav-links" :class="{ mobileOpen: mobileMenuOpen }">
          <a href="#features" class="nav-link" @click.prevent="scrollTo('features')">功能特性</a>
          <a href="#devices" class="nav-link" @click.prevent="scrollTo('devices')">设备兼容</a>
          <a href="#scenarios" class="nav-link" @click.prevent="scrollTo('scenarios')">应用场景</a>
          <a href="#api" class="nav-link" @click.prevent="scrollTo('api')">开发者</a>
        </div>

        <div class="nav-actions">
          <router-link to="/login" class="nav-login">登录</router-link>
          <router-link to="/register" class="nav-register">注册</router-link>
          <router-link to="/dashboard" class="nav-cta">进入控制台</router-link>
        </div>

        <button class="mobile-toggle" @click="mobileMenuOpen = !mobileMenuOpen">
          <svg-icon name="device" :size="20" />
        </button>
      </div>
    </nav>

    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-bg-pattern"></div>
      <div class="hero-content">
        <div class="hero-badge">
          <svg-icon name="online" :size="14" color="#52C41A" />
          <span>开源 · 自部署 · 高可靠</span>
        </div>
        <h1 class="hero-title">
          构建万物互联的<br>
          <span class="text-gradient">智能MQTT云平台</span>
        </h1>
        <p class="hero-desc">
          基于MQTT协议的物联网设备管理平台，支持设备接入、数据采集、实时消息、历史查询，
          为您的IoT项目提供完整的基础设施。
        </p>
        <div class="hero-actions">
          <router-link to="/dashboard" class="btn-hero-primary">立即开始</router-link>
          <a href="#features" class="btn-hero-secondary" @click.prevent="scrollTo('features')">了解更多</a>
        </div>
        <div class="hero-tech-tags">
          <span class="tech-tag">MQTT 5.0</span>
          <span class="tech-tag">EMQX</span>
          <span class="tech-tag">Spring Boot</span>
          <span class="tech-tag">Vue 3</span>
        </div>
      </div>
    </section>

    <!-- Trust Bar -->
    <section class="trust-section">
      <p class="trust-title">已有众多开发者选择使用，部署私有MQTT云平台</p>
      <div class="trust-logos">
        <div class="trust-logo-item"><span>🏭 工业制造</span></div>
        <div class="trust-logo-item"><span>🌾 智慧农业</span></div>
        <div class="trust-logo-item"><span>🏠 智能家居</span></div>
        <div class="trust-logo-item"><span>🏥 医疗健康</span></div>
        <div class="trust-logo-item"><span>🚗 车载系统</span></div>
        <div class="trust-logo-item"><span>🏙 智慧城市</span></div>
      </div>
    </section>

    <!-- Features Section -->
    <section id="features" class="features-section">
      <div class="section-container">
        <div class="section-header">
          <h2 class="section-title">全栈MQTT解决方案</h2>
          <p class="section-desc">从设备接入到数据可视化，一站式管理您的物联网设备</p>
        </div>

        <div class="features-grid">
          <div class="feature-card" v-for="feature in features" :key="feature.title">
            <div class="feature-icon" :style="{ background: feature.bg }">
              <span class="feature-icon-emoji">{{ feature.icon }}</span>
            </div>
            <h3 class="feature-title">{{ feature.title }}</h3>
            <p class="feature-desc">{{ feature.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Device Showcase -->
    <section id="devices" class="device-section">
      <div class="section-container">
        <div class="section-header">
          <h2 class="section-title">广泛的设备兼容性</h2>
          <p class="section-desc">支持各类工业级和消费级物联网设备接入</p>
        </div>

        <div class="device-grid">
          <div class="device-card" v-for="device in devices" :key="device.name">
            <div class="device-card-icon">{{ device.icon }}</div>
            <div class="device-card-name">{{ device.name }}</div>
            <div class="device-card-desc">{{ device.desc }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- Infrastructure Stats -->
    <section class="stats-section">
      <div class="section-container">
        <div class="stats-grid">
          <div class="stat-item" v-for="stat in stats" :key="stat.label">
            <div class="stat-number">{{ stat.value }}</div>
            <div class="stat-label">{{ stat.label }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- Application Scenarios -->
    <section id="scenarios" class="scenarios-section">
      <div class="section-container">
        <div class="section-header">
          <h2 class="section-title">丰富的应用场景</h2>
          <p class="section-desc">覆盖多个行业领域，满足多样化的物联网需求</p>
        </div>

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
      </div>
    </section>

    <!-- API Section -->
    <section id="api" class="api-section">
      <div class="section-container">
        <div class="section-header">
          <h2 class="section-title">开发者友好</h2>
          <p class="section-desc">完善的API接口，轻松集成到您的应用中</p>
        </div>

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
      </div>
    </section>

    <!-- CTA Section -->
    <section class="cta-section">
      <div class="cta-content">
        <h2 class="cta-title">准备好开始了吗？</h2>
        <p class="cta-desc">立即部署您的私有MQTT云平台，开启物联网之旅</p>
        <div class="cta-actions">
          <router-link to="/dashboard" class="btn-cta-primary">免费试用</router-link>
          <router-link to="/login" class="btn-cta-secondary">登录平台</router-link>
        </div>
      </div>
    </section>

    <!-- Footer -->
    <footer class="landing-footer">
      <div class="footer-container">
        <div class="footer-grid">
          <div class="footer-col">
            <div class="footer-brand">
              <svg-icon name="dashboard" :size="24" color="#165DFF" />
              <span>MQTT Cloud</span>
            </div>
            <p class="footer-desc">开源自部署的MQTT物联网云平台，支持设备管理、实时消息和历史数据查询。</p>
          </div>
          <div class="footer-col">
            <h4>产品</h4>
            <a href="#features">功能特性</a>
            <a href="#devices">设备兼容</a>
            <a href="#api">API文档</a>
          </div>
          <div class="footer-col">
            <h4>场景</h4>
            <a href="#scenarios">工业物联网</a>
            <a href="#scenarios">智慧农业</a>
            <a href="#scenarios">智能家居</a>
          </div>
          <div class="footer-col">
            <h4>资源</h4>
            <a href="#">开发文档</a>
            <a href="#">部署指南</a>
            <a href="#">常见问题</a>
          </div>
        </div>
        <div class="footer-bottom">
          <p>&copy; 2026 MQTT Cloud. 基于MQTT协议的物联网设备管理平台。</p>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import SvgIcon from '@/components/Icon.vue'

const isScrolled = ref(false)
const mobileMenuOpen = ref(false)

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

const devices = [
  { icon: '📡', name: 'DTU', desc: '串口转4G无线传输' },
  { icon: '🖥', name: 'RTU', desc: '远程终端控制单元' },
  { icon: '🌡', name: '传感器', desc: '温湿度/压力/流量' },
  { icon: '🏭', name: 'PLC', desc: '可编程逻辑控制器' },
  { icon: '📷', name: '摄像头', desc: '工业视频监控' },
  { icon: '🌐', name: '网关', desc: '协议转换与数据汇聚' },
]

const stats = [
  { value: '10,000+', label: '设备接入规模' },
  { value: '<50ms', label: '消息转发延迟' },
  { value: '99.9%', label: '平台可用性' },
  { value: '500+', label: '企业客户' },
]

const scenarios = [
  { icon: '🌾', title: '智慧农业', desc: '土壤监测、环境感知、智能灌溉', color: '#00B42A', bg: '#E8FFEA' },
  { icon: '🏭', title: '工业监测', desc: '设备监控、能耗管理、故障预警', color: '#165DFF', bg: '#E8F3FF' },
  { icon: '🏢', title: '智能楼宇', desc: '温控系统、照明管理、能耗优化', color: '#722ED1', bg: '#F3E8FF' },
  { icon: '🌿', title: '环境监测', desc: '空气质量、水质检测、噪音监控', color: '#13C2C2', bg: '#E6FFFB' },
  { icon: '🏠', title: '智能家居', desc: '家电控制、安防监控、场景联动', color: '#FF7D00', bg: '#FFF3E8' },
  { icon: '📦', title: '资产追踪', desc: '位置监控、仓储管理、冷链物流', color: '#F53F3F', bg: '#FFECEC' },
]

const apiFeatures = [
  { icon: 'device', title: '设备管理API', desc: '创建设备、查询状态、更新配置' },
  { icon: 'message', title: '消息发送API', desc: '向设备发送指令与配置' },
  { icon: 'history', title: '数据查询API', desc: '获取设备历史数据与趋势' },
]

function scrollTo(id) {
  const el = document.getElementById(id)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth' })
  }
  mobileMenuOpen.value = false
}

function onScroll() {
  isScrolled.value = window.scrollY > 50
}

onMounted(() => {
  window.addEventListener('scroll', onScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<style scoped>
.landing-page {
  min-height: 100vh;
}

/* ========== Navigation ========== */
.landing-nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  height: 64px;
  display: flex;
  align-items: center;
  transition: all 0.3s ease;
  background: transparent;
}

.landing-nav.scrolled {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.nav-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.nav-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
}

.logo-icon {
  width: 32px;
  height: 32px;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: #1D2129;
}

.nav-links {
  display: flex;
  gap: 32px;
}

.nav-link {
  font-size: 14px;
  color: #4E5969;
  text-decoration: none;
  transition: color 0.2s;
}

.nav-link:hover {
  color: #165DFF;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nav-login {
  font-size: 14px;
  color: #4E5969;
  text-decoration: none;
  padding: 6px 16px;
}

.nav-login:hover {
  color: #165DFF;
}

.nav-register {
  font-size: 14px;
  color: #165DFF;
  text-decoration: none;
  padding: 6px 16px;
  border: 1px solid #165DFF;
  border-radius: 6px;
  transition: all 0.2s;
}

.nav-register:hover {
  background: #E8F3FF;
}

.nav-cta {
  font-size: 14px;
  color: #fff;
  background: #165DFF;
  padding: 8px 20px;
  border-radius: 6px;
  text-decoration: none;
  transition: background 0.2s;
}

.nav-cta:hover {
  background: #4080FF;
  color: #fff;
}

.mobile-toggle {
  display: none;
  background: none;
  border: none;
  color: #4E5969;
  cursor: pointer;
}

/* ========== Hero Section ========== */
.hero-section {
  position: relative;
  padding: 160px 24px 100px;
  text-align: center;
  overflow: hidden;
  background: linear-gradient(180deg, #F0F5FF 0%, #F5F7FA 100%);
}

.hero-bg-pattern {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle, #165DFF 1px, transparent 1px);
  background-size: 40px 40px;
  opacity: 0.04;
}

.hero-content {
  position: relative;
  max-width: 800px;
  margin: 0 auto;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  background: rgba(22, 93, 255, 0.08);
  border-radius: 20px;
  font-size: 13px;
  color: #165DFF;
  margin-bottom: 24px;
}

.hero-title {
  font-size: 56px;
  font-weight: 700;
  color: #1D2129;
  line-height: 1.2;
  margin-bottom: 24px;
  letter-spacing: -0.02em;
}

.text-gradient {
  background: linear-gradient(135deg, #165DFF 0%, #4080FF 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-desc {
  font-size: 18px;
  color: #4E5969;
  line-height: 1.8;
  margin-bottom: 40px;
}

.hero-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin-bottom: 32px;
}

.btn-hero-primary {
  padding: 14px 36px;
  background: #165DFF;
  color: #fff;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  text-decoration: none;
  transition: all 0.2s;
  display: inline-flex;
  align-items: center;
}

.btn-hero-primary:hover {
  background: #4080FF;
  color: #fff;
}

.btn-hero-secondary {
  padding: 14px 36px;
  background: #fff;
  color: #165DFF;
  border: 1px solid #165DFF;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  text-decoration: none;
  transition: all 0.2s;
  display: inline-flex;
  align-items: center;
}

.btn-hero-secondary:hover {
  background: #E8F3FF;
}

.hero-tech-tags {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}

.tech-tag {
  padding: 4px 12px;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 4px;
  font-size: 12px;
  color: #86909C;
}

/* ========== Trust Section ========== */
.trust-section {
  padding: 48px 24px;
  text-align: center;
  background: #fff;
  border-bottom: 1px solid var(--border-color-light);
}

.trust-title {
  font-size: 14px;
  color: #86909C;
  margin-bottom: 24px;
}

.trust-logos {
  display: flex;
  gap: 48px;
  justify-content: center;
  flex-wrap: wrap;
}

.trust-logo-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  color: #4E5969;
  font-weight: 500;
  opacity: 0.8;
}

/* ========== Section Shared ========== */
.section-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
}

.section-header {
  text-align: center;
  margin-bottom: 48px;
}

.section-title {
  font-size: 36px;
  font-weight: 700;
  color: #1D2129;
  margin-bottom: 16px;
}

.section-desc {
  font-size: 16px;
  color: #4E5969;
}

/* ========== Features Section ========== */
.features-section {
  padding: 96px 0;
  background: #fff;
}

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

/* ========== Device Section ========== */
.device-section {
  padding: 96px 0;
  background: #F5F7FA;
}

.device-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 20px;
}

.device-card {
  background: #fff;
  border-radius: 12px;
  padding: 32px 20px;
  text-align: center;
  transition: all 0.3s ease;
  border: 1px solid #F2F3F5;
}

.device-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  transform: translateY(-2px);
}

.device-card-icon {
  font-size: 36px;
  margin-bottom: 12px;
}

.device-card-name {
  font-size: 15px;
  font-weight: 600;
  color: #1D2129;
  margin-bottom: 6px;
}

.device-card-desc {
  font-size: 12px;
  color: #86909C;
}

/* ========== Stats Section ========== */
.stats-section {
  padding: 80px 0;
  background: #1D2129;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 32px;
}

.stat-item {
  text-align: center;
}

.stat-number {
  font-size: 48px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 8px;
  letter-spacing: -0.02em;
}

.stat-label {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
}

/* ========== Scenarios Section ========== */
.scenarios-section {
  padding: 96px 0;
  background: #fff;
}

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

/* ========== API Section ========== */
.api-section {
  padding: 96px 0;
  background: #F5F7FA;
}

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

/* ========== CTA Section ========== */
.cta-section {
  padding: 80px 24px;
  text-align: center;
  background: linear-gradient(135deg, #165DFF 0%, #4080FF 100%);
}

.cta-content {
  max-width: 600px;
  margin: 0 auto;
}

.cta-title {
  font-size: 32px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 16px;
}

.cta-desc {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.8);
  margin-bottom: 32px;
}

.cta-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
}

.btn-cta-primary {
  padding: 14px 36px;
  background: #fff;
  color: #165DFF;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.2s;
}

.btn-cta-primary:hover {
  background: #E8F3FF;
  color: #165DFF;
}

.btn-cta-secondary {
  padding: 14px 36px;
  background: transparent;
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  text-decoration: none;
  transition: all 0.2s;
}

.btn-cta-secondary:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

/* ========== Footer ========== */
.landing-footer {
  background: #1D2129;
  padding: 64px 0 0;
}

.footer-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
}

.footer-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr;
  gap: 48px;
  padding-bottom: 48px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.footer-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 16px;
}

.footer-desc {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.4);
  line-height: 1.8;
}

.footer-col h4 {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  margin-bottom: 16px;
}

.footer-col a {
  display: block;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.4);
  text-decoration: none;
  margin-bottom: 10px;
  transition: color 0.2s;
}

.footer-col a:hover {
  color: rgba(255, 255, 255, 0.8);
}

.footer-bottom {
  padding: 24px 0;
  text-align: center;
}

.footer-bottom p {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.3);
}

/* ========== Responsive ========== */
@media (max-width: 1024px) {
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .device-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  .scenarios-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .api-showcase {
    grid-template-columns: 1fr;
  }
  .footer-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 768px) {
  .hero-title {
    font-size: 36px;
  }
  .hero-desc {
    font-size: 15px;
  }
  .hero-actions {
    flex-direction: column;
    align-items: center;
  }
  .nav-links {
    display: none;
  }
  .nav-links.mobileOpen {
    display: flex;
    flex-direction: column;
    position: absolute;
    top: 64px;
    left: 0;
    right: 0;
    background: #fff;
    padding: 16px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    gap: 12px;
  }
  .nav-actions .nav-login,
  .nav-actions .nav-register {
    display: none;
  }
  .mobile-toggle {
    display: block;
  }
  .features-grid {
    grid-template-columns: 1fr;
  }
  .device-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .scenarios-grid {
    grid-template-columns: 1fr;
  }
  .footer-grid {
    grid-template-columns: 1fr;
  }
}
</style>
