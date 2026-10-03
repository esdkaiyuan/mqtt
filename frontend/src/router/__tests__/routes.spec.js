import { describe, expect, it } from 'vitest'
import { routes } from '../index'

const byPath = (path) => routes.find((route) => route.path === path)

describe('router/routes 分区与兼容', () => {
  it('公开站四条路由均放行未登录访问', () => {
    for (const path of ['/', '/docs', '/login', '/register']) {
      const route = byPath(path)
      expect(route, `缺少公开站路由 ${path}`).toBeTruthy()
      expect(route.meta?.requiresAuth).toBe(false)
    }
  })

  it('工作台父路由要求登录并默认跳概览', () => {
    const workbench = byPath('/workbench')
    expect(workbench.meta?.requiresAuth).toBe(true)
    expect(workbench.redirect).toBe('/workbench/dashboard')
  })

  it('工作台子路由覆盖监控、告警与开发接入三个分区', () => {
    const children = byPath('/workbench').children
    const names = children.map((child) => child.name)
    expect(names).toEqual([
      'Dashboard',
      'DeviceList',
      'DeviceGroups',
      'DeviceDetail',
      'ProductList',
      'ProductThingModel',
      'MessageMonitor',
      'HistoryQuery',
      'ApiKeyManagement',
      'WebhookManagement',
      'AlertList',
      'AlertRules'
    ])

    const groups = new Set(children.map((child) => child.meta?.group))
    expect(groups).toEqual(new Set(['monitor', 'access', 'alert']))

    // 设备详情 / 物模型不在导航中展示
    for (const name of ['DeviceDetail', 'ProductThingModel']) {
      const hidden = children.find((child) => child.name === name)
      expect(hidden.meta?.hidden).toBe(true)
    }

    // 产品管理与设备管理同组（monitor）
    const productList = children.find((child) => child.name === 'ProductList')
    expect(productList.path).toBe('products')
    expect(productList.meta?.group).toBe('monitor')
  })

  it('分组管理静态段排在设备详情动态段之前，且与设备管理同组', () => {
    const children = byPath('/workbench').children
    const groupsIndex = children.findIndex((child) => child.name === 'DeviceGroups')
    const detailIndex = children.findIndex((child) => child.name === 'DeviceDetail')

    expect(groupsIndex).toBeGreaterThan(-1)
    expect(detailIndex).toBeGreaterThan(-1)
    expect(groupsIndex).toBeLessThan(detailIndex)

    const groups = children[groupsIndex]
    expect(groups.path).toBe('devices/groups')
    expect(groups.meta?.group).toBe('monitor')
    expect(groups.meta?.hidden).toBeFalsy()
  })

  it('实时消息仅对 ADMIN / OPERATOR 可见', () => {
    const messages = byPath('/workbench').children.find((child) => child.name === 'MessageMonitor')
    expect(messages.meta?.roles).toEqual(['ADMIN', 'OPERATOR'])
  })

  it('文档站五章齐全且默认跳快速开始', () => {
    const docs = byPath('/docs')
    expect(docs.redirect).toBe('/docs/getting-started/overview')
    expect(docs.children.map((child) => child.name)).toEqual([
      'DocsGettingStarted',
      'DocsDeviceAccess',
      'DocsDataMessage',
      'DocsApiReference',
      'DocsOps'
    ])
  })

  it('旧路径全部重定向到新路径', () => {
    const expected = {
      '/landing': '/',
      '/dashboard': '/workbench/dashboard',
      '/devices': '/workbench/devices',
      '/messages': '/workbench/messages',
      '/history': '/workbench/history',
      '/api-docs': '/docs/api/rest',
      '/settings/api-keys': '/workbench/access/api-keys',
      '/settings/webhooks': '/workbench/access/webhooks'
    }

    for (const [from, to] of Object.entries(expected)) {
      const route = byPath(from)
      expect(route, `缺少旧路径 ${from}`).toBeTruthy()
      expect(route.redirect).toBe(to)
    }
  })

  it('设备详情旧路径按 id 重定向', () => {
    const route = byPath('/devices/:id')
    expect(typeof route.redirect).toBe('function')
    expect(route.redirect({ params: { id: 'd-9' } })).toBe('/workbench/devices/d-9')
  })

  it('未匹配路径落到 404 页面', () => {
    const fallback = routes.find((route) => route.path === '/:pathMatch(.*)*')
    expect(fallback?.name).toBe('NotFound')
  })
})