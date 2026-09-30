<template>
  <nav class="landing-nav" :class="{ scrolled: isScrolled }">
    <div class="nav-container">
      <div class="nav-logo">
        <svg class="logo-icon" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect width="32" height="32" rx="8" fill="#165DFF" />
          <path d="M8 16C8 11.6 11.6 8 16 8C20.4 8 24 11.6 24 16C24 20.4 20.4 24 16 24" stroke="white" stroke-width="2.5" stroke-linecap="round" />
          <circle cx="16" cy="16" r="3" fill="white" />
          <path d="M16 10V13M16 19V22M13 16H10M19 16H22" stroke="white" stroke-width="2" stroke-linecap="round" />
        </svg>
        <span class="logo-text">MQTT Cloud</span>
      </div>

      <div class="nav-links" :class="{ mobileOpen: mobileMenuOpen }">
        <a href="#features" class="nav-link" @click.prevent="handleNavClick('features')">功能特性</a>
        <a href="#devices" class="nav-link" @click.prevent="handleNavClick('devices')">设备兼容</a>
        <a href="#scenarios" class="nav-link" @click.prevent="handleNavClick('scenarios')">应用场景</a>
        <a href="#api" class="nav-link" @click.prevent="handleNavClick('api')">开发者</a>
      </div>

      <div class="nav-actions">
        <router-link to="/login" class="nav-login">登录</router-link>
        <router-link to="/register" class="nav-register">注册</router-link>
        <router-link to="/dashboard" class="nav-cta">进入控制台</router-link>
      </div>

      <button class="mobile-toggle" @click="mobileMenuOpen = !mobileMenuOpen">
        <svg-icon name="device" :size="20" />
      </button>
    </div>
  </nav>
</template>

<script setup>
import { ref } from 'vue'
import SvgIcon from '@/components/Icon.vue'
import { scrollToSection, useScrollIndicator } from '@/composables/useLandingScroll'

const { isScrolled } = useScrollIndicator()
const mobileMenuOpen = ref(false)

function handleNavClick(id) {
  scrollToSection(id)
  mobileMenuOpen.value = false
}
</script>

<style scoped>
.landing-nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  height: 64px;
  display: flex;
  align-items: center;
  transition: all 0.3s ease;
  background: transparent;
}

.landing-nav.scrolled {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.nav-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.nav-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
}

.logo-icon {
  width: 32px;
  height: 32px;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: #1D2129;
}

.nav-links {
  display: flex;
  gap: 32px;
}

.nav-link {
  font-size: 14px;
  color: #4E5969;
  text-decoration: none;
  transition: color 0.2s;
}

.nav-link:hover {
  color: #165DFF;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nav-login {
  font-size: 14px;
  color: #4E5969;
  text-decoration: none;
  padding: 6px 16px;
}

.nav-login:hover {
  color: #165DFF;
}

.nav-register {
  font-size: 14px;
  color: #165DFF;
  text-decoration: none;
  padding: 6px 16px;
  border: 1px solid #165DFF;
  border-radius: 6px;
  transition: all 0.2s;
}

.nav-register:hover {
  background: #E8F3FF;
}

.nav-cta {
  font-size: 14px;
  color: #fff;
  background: #165DFF;
  padding: 8px 20px;
  border-radius: 6px;
  text-decoration: none;
  transition: background 0.2s;
}

.nav-cta:hover {
  background: #4080FF;
  color: #fff;
}

.mobile-toggle {
  display: none;
  background: none;
  border: none;
  color: #4E5969;
  cursor: pointer;
}

@media (max-width: 768px) {
  .nav-links {
    display: none;
  }
  .nav-links.mobileOpen {
    display: flex;
    flex-direction: column;
    position: absolute;
    top: 64px;
    left: 0;
    right: 0;
    background: #fff;
    padding: 16px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    gap: 12px;
  }
  .nav-actions .nav-login,
  .nav-actions .nav-register {
    display: none;
  }
  .mobile-toggle {
    display: block;
  }
}
</style>
