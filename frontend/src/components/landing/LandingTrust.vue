<template>
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
</template>

<style scoped>
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
</style>
