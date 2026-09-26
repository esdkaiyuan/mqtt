import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/Dashboard.vue'),
    meta: { title: '仪表板' }
  },
  {
    path: '/waveform',
    name: 'Waveform',
    component: () => import('../views/Waveform.vue'),
    meta: { title: '实时波形' }
  },
  {
    path: '/3d-view',
    name: 'ThreeDView',
    component: () => import('../views/ThreeDView.vue'),
    meta: { title: '3D姿态' }
  },
  {
    path: '/annotation',
    name: 'DataAnnotation',
    component: () => import('../views/DataAnnotation.vue'),
    meta: { title: '数据标注' }
  },
  {
    path: '/export',
    name: 'DataExport',
    component: () => import('../views/DataExport.vue'),
    meta: { title: '数据导出' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
