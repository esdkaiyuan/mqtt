<template>
  <nav class="site-nav" :class="{ 'is-scrolled': isScrolled }">
    <div class="site-container site-nav__inner">
      <BrandLogo to="/" :size="32" :text-size="20" />

      <div class="site-nav__links" :class="{ 'is-open': mobileMenuOpen }">
        <a class="site-nav__link" href="#features" @click.prevent="handleNavClick('features')">功能特性</a>
        <a class="site-nav__link" href="#devices" @click.prevent="handleNavClick('devices')">设备兼容</a>
        <a class="site-nav__link" href="#scenarios" @click.prevent="handleNavClick('scenarios')">应用场景</a>
        <router-link class="site-nav__link" to="/docs">开发文档</router-link>
      </div>

      <div class="site-nav__actions">
        <template v-if="authStore.isLoggedIn">
          <router-link to="/workbench/dashboard" class="site-nav__cta">进入工作台</router-link>
        </template>
        <template v-else>
          <router-link to="/login" class="site-nav__login">登录</router-link>
          <router-link to="/register" class="site-nav__register">注册</router-link>
          <router-link to="/workbench/dashboard" class="site-nav__cta">进入控制台</router-link>
        </template>
      </div>

      <button class="site-nav__toggle" type="button" aria-label="打开导航" @click="mobileMenuOpen = !mobileMenuOpen">
        <svg-icon name="device" :size="20" />
      </button>
    </div>
  </nav>
</template>

<script setup>
import { ref } from 'vue'
import SvgIcon from '@/components/Icon.vue'
import BrandLogo from '@/components/common/BrandLogo.vue'
import { useAuthStore } from '@/stores/auth'
import { scrollToSection, useScrollIndicator } from '@/composables/useLandingScroll'

const authStore = useAuthStore()
const { isScrolled } = useScrollIndicator()
const mobileMenuOpen = ref(false)

function handleNavClick(id) {
  scrollToSection(id)
  mobileMenuOpen.value = false
}
</script>

<style scoped>
.site-nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: var(--z-nav);
  height: var(--site-nav-height);
  display: flex;
  align-items: center;
  transition: background var(--transition-base), box-shadow var(--transition-base);
  background: transparent;
}

.site-nav.is-scrolled {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.site-nav__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.site-nav__links {
  display: flex;
  gap: var(--spacing-3xl);
}

.site-nav__link {
  font-size: var(--font-size-md);
  color: var(--color-text-regular);
  text-decoration: none;
  transition: color var(--transition-fast);
}

.site-nav__link:hover {
  color: var(--color-primary);
}

.site-nav__actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.site-nav__login {
  font-size: var(--font-size-md);
  color: var(--color-text-regular);
  text-decoration: none;
  padding: 6px 16px;
}

.site-nav__login:hover {
  color: var(--color-primary);
}

.site-nav__register {
  font-size: var(--font-size-md);
  color: var(--color-primary);
  text-decoration: none;
  padding: 6px 16px;
  border: 1px solid var(--color-primary);
  border-radius: var(--border-radius);
  transition: background var(--transition-fast);
}

.site-nav__register:hover {
  background: var(--color-primary-light);
}

.site-nav__cta {
  font-size: var(--font-size-md);
  color: var(--color-white);
  background: var(--color-primary);
  padding: 8px 20px;
  border-radius: var(--border-radius);
  text-decoration: none;
  transition: background var(--transition-fast);
}

.site-nav__cta:hover {
  background: var(--color-primary-hover);
  color: var(--color-white);
}

.site-nav__toggle {
  display: none;
  background: none;
  border: none;
  color: var(--color-text-regular);
  cursor: pointer;
}

@media (max-width: 768px) {
  .site-nav__links {
    display: none;
  }

  .site-nav__links.is-open {
    display: flex;
    flex-direction: column;
    position: absolute;
    top: var(--site-nav-height);
    left: 0;
    right: 0;
    background: var(--color-white);
    padding: var(--spacing-lg);
    box-shadow: var(--shadow-popover);
    gap: var(--spacing-md);
  }

  .site-nav__actions .site-nav__login,
  .site-nav__actions .site-nav__register {
    display: none;
  }

  .site-nav__toggle {
    display: block;
  }
}
</style>