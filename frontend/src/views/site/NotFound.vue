<template>
  <SiteLayout>
    <div class="not-found">
      <div class="site-container not-found__inner">
        <p class="not-found__code">404</p>
        <h1 class="not-found__title">页面不存在</h1>
        <p class="not-found__desc">你访问的页面可能已被移动或删除，请检查地址是否正确。</p>
        <div class="not-found__actions">
          <router-link to="/" class="not-found__btn not-found__btn--primary">返回首页</router-link>
          <router-link to="/workbench/dashboard" class="not-found__btn">进入工作台</router-link>
          <router-link to="/docs" class="not-found__btn">查看文档</router-link>
        </div>
      </div>
    </div>
  </SiteLayout>
</template>

<script setup>
import SiteLayout from '@/components/layout/SiteLayout.vue'
</script>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - var(--site-nav-height) - 320px);
  padding: var(--spacing-3xl) 0;
}

.not-found__inner {
  text-align: center;
}

.not-found__code {
  font-size: 96px;
  font-weight: var(--font-weight-bold);
  line-height: 1;
  color: var(--color-primary);
  letter-spacing: -0.02em;
}

.not-found__title {
  margin-top: var(--spacing-md);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
}

.not-found__desc {
  margin-top: var(--spacing-sm);
  font-size: var(--font-size-md);
  color: var(--color-text-tertiary);
}

.not-found__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--spacing-md);
  margin-top: var(--spacing-2xl);
}

.not-found__btn {
  padding: 10px 24px;
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius);
  font-size: var(--font-size-md);
  color: var(--color-text-regular);
  text-decoration: none;
  transition: all var(--transition-fast);
}

.not-found__btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.not-found__btn--primary {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--color-white);
}

.not-found__btn--primary:hover {
  background: var(--color-primary-hover);
  color: var(--color-white);
}
</style>