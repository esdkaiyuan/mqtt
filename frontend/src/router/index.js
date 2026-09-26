import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { requiresAuth: false, hideLayout: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { requiresAuth: false, hideLayout: true }
  },
  {
    path: '/landing',
    name: 'Landing',
    component: () => import('@/views/landing/LandingPage.vue'),
    meta: { requiresAuth: false, hideLayout: true }
  },
  {
    path: '/',
    component: () => import('@/components/Layout/MainLayout.vue'),
    meta: { requiresAuth: true },
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Dashboard.vue'),
        meta: { title: '仪表盘', icon: 'dashboard' }
      },
      {
        path: 'devices',
        name: 'DeviceList',
        component: () => import('@/views/device/DeviceList.vue'),
        meta: { title: '设备管理', icon: 'device' }
      },
      {
        path: 'devices/:id',
        name: 'DeviceDetail',
        component: () => import('@/views/device/DeviceDetail.vue'),
        meta: { title: '设备详情', hidden: true }
      },
      {
        path: 'messages',
        name: 'MessageMonitor',
        component: () => import('@/views/message/MessageMonitor.vue'),
        meta: { title: '实时消息', icon: 'message', roles: ['ADMIN', 'OPERATOR'] }
      },
      {
        path: 'history',
        name: 'HistoryQuery',
        component: () => import('@/views/history/HistoryQuery.vue'),
        meta: { title: '历史查询', icon: 'history' }
      },
      {
        path: 'api-docs',
        name: 'ApiDocs',
        component: () => import('@/views/api/ApiDocs.vue'),
        meta: { title: 'API文档', icon: 'documentation' }
      },
      {
        path: 'settings/api-keys',
        name: 'ApiKeyManagement',
        component: () => import('@/views/api/ApiKeys.vue'),
        meta: { title: 'API密钥', icon: 'settings' }
      },
      {
        path: 'settings/webhooks',
        name: 'WebhookManagement',
        component: () => import('@/views/api/Webhooks.vue'),
        meta: { title: 'Webhook', icon: 'settings' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const PUBLIC_PATHS = new Set(['/login', '/register', '/landing'])

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

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  const isPublic = to.matched.some((record) => record.meta.requiresAuth === false)
    || PUBLIC_PATHS.has(to.path)

  if (!authStore.isLoggedIn) {
    if (isPublic) {
      next()
    } else {
      next({ path: '/login', query: { redirect: to.fullPath } })
    }
    return
  }

  // 已登录：补齐用户信息，保证角色判断可靠
  await authStore.ensureUserLoaded()

  if (to.path === '/login' || to.path === '/register') {
    next('/dashboard')
    return
  }

  const requiredRoles = resolveRequiredRoles(to)
  const allowed = requiredRoles.every((roles) => authStore.hasRole(roles))
  if (!allowed) {
    next('/dashboard')
    return
  }

  next()
})

export default router
