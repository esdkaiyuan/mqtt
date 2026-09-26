<template>
  <div class="sidebar" :class="{ collapsed: isCollapsed }">
    <div class="sidebar-logo">
      <svg-icon name="dashboard" :size="24" color="#165DFF" />
      <span v-if="!isCollapsed" class="logo-text">MQTT Cloud</span>
    </div>

    <el-menu
      :default-active="currentRoute"
      :collapse="isCollapsed"
      background-color="#FFFFFF"
      text-color="#4E5969"
      active-text-color="#165DFF"
      router
    >
      <el-menu-item index="/dashboard">
        <svg-icon name="dashboard" :size="18" />
        <template #title>仪表盘</template>
      </el-menu-item>

      <el-menu-item index="/devices">
        <svg-icon name="device" :size="18" />
        <template #title>设备管理</template>
      </el-menu-item>

      <el-menu-item index="/messages" v-if="hasOperatorRole">
        <svg-icon name="message" :size="18" />
        <template #title>实时消息</template>
      </el-menu-item>

      <el-menu-item index="/history">
        <svg-icon name="history" :size="18" />
        <template #title>历史查询</template>
      </el-menu-item>

      <el-sub-menu index="/settings" v-if="isAdmin">
        <template #title>
          <svg-icon name="settings" :size="18" />
          <span>系统管理</span>
        </template>
        <el-menu-item index="/api-docs">
          <svg-icon name="documentation" :size="18" />
          <template #title>API文档</template>
        </el-menu-item>
        <el-menu-item index="/settings/api-keys">
          <svg-icon name="settings" :size="18" />
          <template #title>API密钥</template>
        </el-menu-item>
        <el-menu-item index="/settings/webhooks">
          <svg-icon name="settings" :size="18" />
          <template #title>Webhook</template>
        </el-menu-item>
      </el-sub-menu>
    </el-menu>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import SvgIcon from '@/components/Icon.vue'

const route = useRoute()
const authStore = useAuthStore()

const currentRoute = computed(() => route.path)
const isCollapsed = computed(() => false)
const hasOperatorRole = computed(() => {
  return ['ADMIN', 'OPERATOR'].includes(authStore.user?.role)
})
const isAdmin = computed(() => authStore.user?.role === 'ADMIN')
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  height: 100vh;
  background: var(--color-white);
  border-right: 1px solid var(--border-color);
  overflow-y: auto;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  height: var(--header-height);
  border-bottom: 1px solid var(--border-color-light);
}

.logo-text {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-gray-dark);
}

.el-menu {
  border-right: none;
  padding: var(--spacing-sm) 0;
}

.el-menu-item {
  height: 48px;
  line-height: 48px;
  margin: 2px 8px;
  border-radius: var(--border-radius-sm);
}
</style>
