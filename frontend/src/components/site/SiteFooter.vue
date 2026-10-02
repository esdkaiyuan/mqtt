<template>
  <footer class="site-footer">
    <div class="site-container">
      <div class="site-footer__grid">
        <div class="site-footer__col">
          <BrandLogo :size="24" :text-size="18" class="site-footer__brand" />
          <p class="site-footer__desc">
            开源自部署的 MQTT 物联网云平台，支持设备管理、实时消息和历史数据查询。
          </p>
        </div>

        <div class="site-footer__col">
          <h4>产品</h4>
          <router-link to="/#features">功能特性</router-link>
          <router-link to="/#devices">设备兼容</router-link>
          <router-link to="/docs">开发文档</router-link>
        </div>

        <div class="site-footer__col">
          <h4>场景</h4>
          <router-link to="/#scenarios">工业物联网</router-link>
          <router-link to="/#scenarios">智慧农业</router-link>
          <router-link to="/#scenarios">智能家居</router-link>
        </div>

        <div class="site-footer__col">
          <h4>资源</h4>
          <router-link to="/docs/getting-started/overview">快速开始</router-link>
          <router-link to="/docs/ops/deployment">部署指南</router-link>
          <router-link to="/docs/ops/faq">常见问题</router-link>
        </div>
      </div>

      <div class="site-footer__bottom">
        <p>&copy; 2026 MQTT Cloud. 基于 MQTT 协议的物联网设备管理平台。</p>
      </div>
    </div>
  </footer>
</template>

<script setup>
import BrandLogo from '@/components/common/BrandLogo.vue'
</script>

<style scoped>
.site-footer {
  background: var(--color-text-primary);
  padding: 64px 0 0;
}

.site-footer__grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr;
  gap: 48px;
  padding-bottom: 48px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.site-footer__brand {
  margin-bottom: var(--spacing-lg);
}

.site-footer__brand :deep(.brand-logo__text) {
  color: var(--color-white);
}

.site-footer__desc {
  font-size: var(--font-size-sm);
  color: rgba(255, 255, 255, 0.4);
  line-height: 1.8;
}

.site-footer__col h4 {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-white);
  margin-bottom: var(--spacing-lg);
}

.site-footer__col a {
  display: block;
  font-size: var(--font-size-sm);
  color: rgba(255, 255, 255, 0.4);
  text-decoration: none;
  margin-bottom: 10px;
  transition: color var(--transition-fast);
}

.site-footer__col a:hover {
  color: rgba(255, 255, 255, 0.8);
}

.site-footer__bottom {
  padding: var(--spacing-2xl) 0;
  text-align: center;
}

.site-footer__bottom p {
  font-size: var(--font-size-xs);
  color: rgba(255, 255, 255, 0.3);
}

@media (max-width: 1024px) {
  .site-footer__grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 768px) {
  .site-footer__grid {
    grid-template-columns: 1fr;
  }
}
</style>