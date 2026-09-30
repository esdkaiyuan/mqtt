<template>
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
</template>

<script setup>
import SvgIcon from '@/components/Icon.vue'
</script>

<style scoped>
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

@media (max-width: 1024px) {
  .footer-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 768px) {
  .footer-grid {
    grid-template-columns: 1fr;
  }
}
</style>
