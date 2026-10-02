<template>
  <section class="cta-section">
    <div class="cta-content">
      <h2 class="cta-title">准备好开始了吗？</h2>
      <p class="cta-desc">立即部署您的私有MQTT云平台，开启物联网之旅</p>
      <div class="cta-actions">
        <router-link to="/register" class="btn-cta-primary">免费开始</router-link>
        <router-link to="/docs" class="btn-cta-secondary">查看文档</router-link>
      </div>
    </div>
  </section>
</template>

<style scoped>
.cta-section {
  padding: 80px 24px;
  text-align: center;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-hover) 100%);
}

.cta-content {
  max-width: 600px;
  margin: 0 auto;
}

.cta-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--color-white);
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
  background: var(--color-white);
  color: var(--color-primary);
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.2s;
}

.btn-cta-primary:hover {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.btn-cta-secondary {
  padding: 14px 36px;
  background: transparent;
  color: var(--color-white);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  text-decoration: none;
  transition: all 0.2s;
}

.btn-cta-secondary:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--color-white);
}
</style>
