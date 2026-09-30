<template>
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
        <a href="#features" class="btn-hero-secondary" @click.prevent="scrollToSection('features')">了解更多</a>
      </div>
      <div class="hero-tech-tags">
        <span class="tech-tag">MQTT 5.0</span>
        <span class="tech-tag">EMQX</span>
        <span class="tech-tag">Spring Boot</span>
        <span class="tech-tag">Vue 3</span>
      </div>
    </div>
  </section>
</template>

<script setup>
import SvgIcon from '@/components/Icon.vue'
import { scrollToSection } from '@/composables/useLandingScroll'
</script>

<style scoped>
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
}
</style>
