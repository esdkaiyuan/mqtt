<template>
  <section class="trust">
    <p class="trust__title">已有众多开发者选择使用，部署私有 MQTT 云平台</p>
    <div class="trust__items">
      <span v-for="item in items" :key="item" class="trust__item">{{ item }}</span>
    </div>
  </section>
</template>

<script setup>
const items = ['工业制造', '智慧农业', '智能家居', '医疗健康', '车载系统', '智慧城市']
</script>

<style scoped>
.trust {
  padding: var(--spacing-4xl) 0;
  text-align: center;
  background: var(--color-white);
  border-bottom: 1px solid var(--color-border-light);
}

.trust__title {
  font-size: var(--font-size-md);
  color: var(--color-text-tertiary);
  margin-bottom: var(--spacing-2xl);
}

.trust__items {
  display: flex;
  gap: var(--spacing-4xl);
  justify-content: center;
  flex-wrap: wrap;
}

.trust__item {
  font-size: var(--font-size-lg);
  color: var(--color-text-regular);
  font-weight: var(--font-weight-medium);
  opacity: 0.8;
}
</style>