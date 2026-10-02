import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

let mockRouteName = 'Home'
const mockPush = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({ name: mockRouteName }),
  useRouter: () => ({ push: mockPush })
}))

import SiteNav from '../SiteNav.vue'

function mountNav() {
  return mount(SiteNav, {
    global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } }
  })
}

function linkByText(wrapper, text) {
  return wrapper.findAll('.site-nav__link').find((link) => link.text() === text)
}

describe('components/site/SiteNav', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockRouteName = 'Home'
    mockPush.mockClear()
  })

  it('首页内点击锚点链接不触发路由跳转', async () => {
    const wrapper = mountNav()
    await linkByText(wrapper, '功能特性').trigger('click')
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('非首页点击锚点链接回到首页并带上对应 hash', async () => {
    mockRouteName = 'DocsDeviceAccess'
    const wrapper = mountNav()

    await linkByText(wrapper, '功能特性').trigger('click')
    await linkByText(wrapper, '设备兼容').trigger('click')
    await linkByText(wrapper, '应用场景').trigger('click')

    expect(mockPush).toHaveBeenNthCalledWith(1, { path: '/', hash: '#features' })
    expect(mockPush).toHaveBeenNthCalledWith(2, { path: '/', hash: '#devices' })
    expect(mockPush).toHaveBeenNthCalledWith(3, { path: '/', hash: '#scenarios' })
  })
})