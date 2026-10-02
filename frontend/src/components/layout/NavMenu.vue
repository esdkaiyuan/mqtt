<template>
  <div class="nav-menu" :class="{ 'nav-menu--collapsed': collapsed }">
    <el-menu
      :default-active="activePath"
      :collapse="collapsed"
      :collapse-transition="false"
      router
      class="nav-menu__list"
    >
      <el-menu-item-group
        v-for="group in visibleGroups"
        :key="group.key"
        :title="group.label"
      >
        <el-menu-item
          v-for="item in group.items"
          :key="item.path"
          :index="item.path"
        >
          <svg-icon :name="item.icon" :size="18" />
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu-item-group>
    </el-menu>

    <div class="nav-menu__footer">
      <el-tooltip content="开发文档" placement="right" :disabled="!collapsed">
        <a class="nav-menu__link" href="/docs" target="_blank" rel="noopener">
          <svg-icon name="documentation" :size="18" />
          <span v-if="!collapsed" class="nav-menu__link-text">开发文档</span>
        </a>
      </el-tooltip>
      <el-tooltip content="返回官网" placement="right" :disabled="!collapsed">
        <router-link class="nav-menu__link" to="/">
          <svg-icon name="login" :size="18" />
          <span v-if="!collapsed" class="nav-menu__link-text">返回官网</span>
        </router-link>
      </el-tooltip>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import SvgIcon from '@/components/Icon.vue'

defineProps({
  collapsed: {
    type: Boolean,
    default: false
  }
})

const route = useRoute()
const authStore = useAuthStore()

const navGroups = [
  {
    key: 'monitor',
    label: '监控',
    items: [
      { path: '/workbench/dashboard', title: '概览', icon: 'dashboard' },
      { path: '/workbench/devices', title: '设备管理', icon: 'device' },
      { path: '/workbench/products', title: '产品管理', icon: 'package' },
      { path: '/workbench/messages', title: '实时消息', icon: 'message', roles: ['ADMIN', 'OPERATOR'] },
      { path: '/workbench/history', title: '历史数据', icon: 'history' }
    ]
  },
  {
    key: 'alert',
    label: '告警',
    items: [
      { path: '/workbench/alerts', title: '告警列表', icon: 'bell' },
      { path: '/workbench/alerts/rules', title: '告警规则', icon: 'shield' }
    ]
  },
  {
    key: 'access',
    label: '开发接入',
    items: [
      { path: '/workbench/access/api-keys', title: 'API 密钥', icon: 'settings' },
      { path: '/workbench/access/webhooks', title: 'Webhook', icon: 'documentation' }
    ]
  }
]

const visibleGroups = computed(() =>
  navGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => authStore.hasRole(item.roles))
    }))
    .filter((group) => group.items.length > 0)
)

const allItems = computed(() => navGroups.flatMap((group) => group.items))

// 最长前缀匹配，保证 /workbench/devices/:id 时「设备管理」保持高亮
const activePath = computed(() => {
  const matched = allItems.value
    .filter((item) => route.path === item.path || route.path.startsWith(`${item.path}/`))
    .sort((a, b) => b.path.length - a.path.length)[0]
  return matched ? matched.path : route.path
})
</script>

<style scoped>
.nav-menu {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.nav-menu__list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  border-right: none;
  padding: var(--spacing-sm) 0;
}

.nav-menu__list:not(.el-menu--collapse) {
  width: var(--nav-width);
}

.nav-menu__footer {
  border-top: 1px solid var(--border-color-light);
  padding: var(--spacing-sm) 0;
}

.nav-menu__link {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  height: 40px;
  margin: 2px 8px;
  padding: 0 var(--spacing-md);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
  text-decoration: none;
  transition: background-color var(--transition-fast), color var(--transition-fast);
}

.nav-menu__link:hover {
  background: var(--color-bg);
  color: var(--color-primary);
}

.nav-menu__link-text {
  white-space: nowrap;
}

.nav-menu--collapsed .nav-menu__link {
  justify-content: center;
  padding: 0;
}
</style>