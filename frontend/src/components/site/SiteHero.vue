<template>
  <section class="hero">
    <div class="site-container hero__inner">
      <div class="hero__badge">
        <svg-icon name="online" :size="14" :color="'var(--color-success)'" />
        <span>开源 · 自部署 · 高可靠</span>
      </div>

      <h1 class="hero__title">
        构建万物互联的<br>
        <span class="hero__title-accent">智能 MQTT 云平台</span>
      </h1>

      <p class="hero__desc">
        基于 MQTT 协议的物联网设备管理平台，支持设备接入、数据采集、实时消息、历史查询，
        为你的 IoT 项目提供完整的基础设施。
      </p>

      <div class="hero__actions">
        <router-link :to="primaryTo" class="hero__btn hero__btn--primary">
          {{ authStore.isLoggedIn ? '进入工作台' : '立即开始' }}
        </router-link>
        <router-link to="/docs" class="hero__btn hero__btn--secondary">查看文档</router-link>
      </div>

      <div class="hero__tags">
        <span class="hero__tag">MQTT 5.0</span>
        <span class="hero__tag">EMQX</span>
        <span class="hero__tag">Spring Boot</span>
        <span class="hero__tag">Vue 3</span>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import SvgIcon from '@/components/Icon.vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const primaryTo = computed(() => (authStore.isLoggedIn ? '/workbench/dashboard' : '/login'))
</script>

<style scoped>
.hero {
  position: relative;
  padding: 160px 0 100px;
  text-align: center;
  background: linear-gradient(180deg, var(--color-primary-light) 0%, var(--color-bg) 100%);
}

.hero__inner {
  max-width: 800px;
}

.hero__badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  background: var(--color-primary-light);
  border-radius: 20px;
  font-size: var(--font-size-sm);
  color: var(--color-primary);
  margin-bottom: var(--spacing-2xl);
}

.hero__title {
  font-size: 56px;
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
  line-height: 1.2;
  letter-spacing: -0.02em;
  margin-bottom: var(--spacing-2xl);
}

.hero__title-accent {
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-hover) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero__desc {
  font-size: 18px;
  color: var(--color-text-regular);
  line-height: 1.8;
  margin-bottom: var(--spacing-4xl);
}

.hero__actions {
  display: flex;
  gap: var(--spacing-lg);
  justify-content: center;
  margin-bottom: var(--spacing-3xl);
}

.hero__btn {
  display: inline-flex;
  align-items: center;
  padding: 14px 36px;
  border-radius: var(--border-radius);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-medium);
  text-decoration: none;
  transition: all var(--transition-base);
}

.hero__btn--primary {
  background: var(--color-primary);
  color: var(--color-white);
}

.hero__btn--primary:hover {
  background: var(--color-primary-hover);
  color: var(--color-white);
}

.hero__btn--secondary {
  background: var(--color-white);
  color: var(--color-primary);
  border: 1px solid var(--color-primary);
}

.hero__btn--secondary:hover {
  background: var(--color-primary-light);
}

.hero__tags {
  display: flex;
  gap: var(--spacing-md);
  justify-content: center;
  flex-wrap: wrap;
}

.hero__tag {
  padding: 4px 12px;
  background: rgba(0, 0, 0, 0.04);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

@media (max-width: 768px) {
  .hero {
    padding: 120px 0 72px;
  }

  .hero__title {
    font-size: 36px;
  }

  .hero__desc {
    font-size: var(--font-size-lg);
  }

  .hero__actions {
    flex-direction: column;
    align-items: center;
  }
}
</style>