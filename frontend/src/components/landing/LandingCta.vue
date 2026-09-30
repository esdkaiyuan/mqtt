<template>
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
</template>

<style scoped>
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
</style>
