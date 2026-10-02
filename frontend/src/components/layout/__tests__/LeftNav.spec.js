import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { mount } from '@vue/test-utils'
import LeftNav from '../LeftNav.vue'

const auth = vi.hoisted(() => ({ hasRole: vi.fn() }))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ hasRole: auth.hasRole })
}))

const NAV_PATHS = [
  '/',
  '/docs',
  '/workbench/dashboard',
  '/workbench/devices',
  '/workbench/messages',
  '/workbench/history',
  '/workbench/access/api-keys',
  '/workbench/access/webhooks'
]

describe('components/layout/LeftNav 角色可见性', () => {
  let pinia

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    auth.hasRole.mockReset()
  })

  async function mountLeftNav() {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: NAV_PATHS.map((path) => ({ path, component: { template: '<div />' } }))
    })
    await router.push('/workbench/dashboard')
    await router.isReady()

    return mount(LeftNav, {
      global: { plugins: [pinia, router] }
    })
  }

  it('角色允许时展示全部分组菜单', async () => {
    auth.hasRole.mockImplementation(() => true)
    const wrapper = await mountLeftNav()
    const text = wrapper.text()

    expect(text).toContain('监控')
    expect(text).toContain('开发接入')
    expect(text).toContain('概览')
    expect(text).toContain('设备管理')
    expect(text).toContain('实时消息')
    expect(text).toContain('API 密钥')
    expect(text).toContain('Webhook')
  })

  it('无角色项可见、受限项被隐藏', async () => {
    // 有 roles 声明的菜单项（实时消息）被过滤，其余保留
    auth.hasRole.mockImplementation((roles) => !roles)
    const wrapper = await mountLeftNav()
    const text = wrapper.text()

    expect(text).toContain('设备管理')
    expect(text).toContain('历史数据')
    expect(text).not.toContain('实时消息')
  })

  it('全部分组被过滤时导航为空', async () => {
    auth.hasRole.mockImplementation(() => false)
    const wrapper = await mountLeftNav()
    expect(wrapper.findAll('.el-menu-item')).toHaveLength(0)
  })
})