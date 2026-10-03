import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const WORKBENCH_HOME = '/workbench/dashboard'

// 导出路由表供路由快照测试断言分区与旧路径重定向
export const routes = [
  // ===== 公开站 =====
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/site/home/HomePage.vue'),
    meta: { requiresAuth: false, layout: 'site' }
  },
  {
    path: '/docs',
    component: () => import('@/components/layout/DocsLayout.vue'),
    meta: { requiresAuth: false, layout: 'docs' },
    redirect: '/docs/getting-started/overview',
    children: [
      {
        path: 'getting-started/:section?',
        name: 'DocsGettingStarted',
        component: () => import('@/views/site/docs/GettingStarted.vue'),
        meta: { title: '快速开始', group: 'getting-started' }
      },
      {
        path: 'device/:section?',
        name: 'DocsDeviceAccess',
        component: () => import('@/views/site/docs/DeviceAccess.vue'),
        meta: { title: '设备接入', group: 'device' }
      },
      {
        path: 'data/:section?',
        name: 'DocsDataMessage',
        component: () => import('@/views/site/docs/DataMessage.vue'),
        meta: { title: '消息与数据', group: 'data' }
      },
      {
        path: 'api/:section?',
        name: 'DocsApiReference',
        component: () => import('@/views/site/docs/ApiReference.vue'),
        meta: { title: '开放 API', group: 'api' }
      },
      {
        path: 'ops/:section?',
        name: 'DocsOps',
        component: () => import('@/views/site/docs/Ops.vue'),
        meta: { title: '平台运维', group: 'ops' }
      }
    ]
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { requiresAuth: false, layout: 'blank' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { requiresAuth: false, layout: 'blank' }
  },

  // ===== 工作台 =====
  {
    path: '/workbench',
    component: () => import('@/components/layout/WorkbenchLayout.vue'),
    meta: { requiresAuth: true },
    redirect: WORKBENCH_HOME,
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/workbench/dashboard/Dashboard.vue'),
        meta: { title: '概览', icon: 'dashboard', group: 'monitor' }
      },
      {
        path: 'devices',
        name: 'DeviceList',
        component: () => import('@/views/workbench/device/DeviceList.vue'),
        meta: { title: '设备管理', icon: 'device', group: 'monitor' }
      },
      {
        // 静态段排在 devices/:id 之前，避免被设备详情路由吞掉
        path: 'devices/groups',
        name: 'DeviceGroups',
        component: () => import('@/views/workbench/device/DeviceGroups.vue'),
        meta: { title: '分组管理', icon: 'building', group: 'monitor' }
      },
      {
        path: 'devices/:id',
        name: 'DeviceDetail',
        component: () => import('@/views/workbench/device/DeviceDetail.vue'),
        meta: { title: '设备详情', hidden: true, group: 'monitor' }
      },
      {
        // 设备统一日志时间线（T-20），入口在设备详情页，不在导航中展示
        path: 'devices/:id/logs',
        name: 'DeviceLog',
        component: () => import('@/views/workbench/device/DeviceLog.vue'),
        meta: { title: '设备日志', hidden: true, group: 'monitor' }
      },
      {
        path: 'products',
        name: 'ProductList',
        component: () => import('@/views/workbench/product/ProductList.vue'),
        meta: { title: '产品管理', icon: 'package', group: 'monitor' }
      },
      {
        path: 'products/:id/thing-model',
        name: 'ProductThingModel',
        component: () => import('@/views/workbench/product/ThingModel.vue'),
        meta: { title: '物模型', hidden: true, group: 'monitor' }
      },
      {
        path: 'messages',
        name: 'MessageMonitor',
        component: () => import('@/views/workbench/message/MessageMonitor.vue'),
        meta: { title: '实时消息', icon: 'message', group: 'monitor', roles: ['ADMIN', 'OPERATOR'] }
      },
      {
        path: 'history',
        name: 'HistoryQuery',
        component: () => import('@/views/workbench/history/HistoryQuery.vue'),
        meta: { title: '历史数据', icon: 'history', group: 'monitor' }
      },
      {
        path: 'access/api-keys',
        name: 'ApiKeyManagement',
        component: () => import('@/views/workbench/access/ApiKeys.vue'),
        meta: { title: 'API 密钥', icon: 'settings', group: 'access' }
      },
      {
        path: 'access/webhooks',
        name: 'WebhookManagement',
        component: () => import('@/views/workbench/access/Webhooks.vue'),
        meta: { title: 'Webhook', icon: 'settings', group: 'access' }
      },
      {
        path: 'alerts',
        name: 'AlertList',
        component: () => import('@/views/workbench/alert/AlertList.vue'),
        meta: { title: '告警列表', icon: 'bell', group: 'alert' }
      },
      {
        path: 'alerts/rules',
        name: 'AlertRules',
        component: () => import('@/views/workbench/alert/AlertRules.vue'),
        meta: { title: '告警规则', icon: 'shield', group: 'alert' }
      },
      {
        path: 'rules',
        name: 'RuleCenter',
        component: () => import('@/views/workbench/rule/RuleCenter.vue'),
        meta: { title: '消息规则', icon: 'operation', group: 'monitor' }
      }
    ]
  },

  // ===== 旧路径兼容（301） =====
  { path: '/landing', redirect: '/' },
  { path: '/dashboard', redirect: WORKBENCH_HOME },
  { path: '/devices', redirect: '/workbench/devices' },
  { path: '/devices/:id', redirect: (to) => `/workbench/devices/${to.params.id}` },
  { path: '/messages', redirect: '/workbench/messages' },
  { path: '/history', redirect: '/workbench/history' },
  { path: '/api-docs', redirect: '/docs/api/rest' },
  { path: '/settings/api-keys', redirect: '/workbench/access/api-keys' },
  { path: '/settings/webhooks', redirect: '/workbench/access/webhooks' },

  // ===== 404 =====
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/site/NotFound.vue'),
    meta: { requiresAuth: false, layout: 'blank' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, top: 96 }
    return { top: 0 }
  }
})

/**
 * 收集路由链上声明的角色要求（meta.roles），用于前端访问控制。
 * 后端仍会独立鉴权，此处仅用于避免渲染无权访问的页面。
 */
function resolveRequiredRoles(to) {
  const required = []
  to.matched.forEach((record) => {
    const roles = record.meta?.roles
    if (Array.isArray(roles) && roles.length > 0) {
      required.push(roles)
    }
  })
  return required
}

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const isPublic = to.matched.some((record) => record.meta.requiresAuth === false)

  if (!authStore.isLoggedIn) {
    if (isPublic) return true
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 已登录：补齐用户信息，保证角色判断可靠
  await authStore.ensureUserLoaded()

  if (to.path === '/login' || to.path === '/register') {
    return WORKBENCH_HOME
  }

  const requiredRoles = resolveRequiredRoles(to)
  const allowed = requiredRoles.every((roles) => authStore.hasRole(roles))
  if (!allowed) {
    return WORKBENCH_HOME
  }

  return true
})

export default router