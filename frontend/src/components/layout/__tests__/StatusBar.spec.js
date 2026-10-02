import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import StatusBar from '../StatusBar.vue'
import { useUiStore } from '@/stores/ui'

vi.mock('@/api/device', () => ({
  deviceApi: {
    getOnline: vi.fn(() => Promise.resolve({ data: [] })),
    getList: vi.fn(() => Promise.resolve({ data: { total: 0 } }))
  }
}))

vi.mock('@/api/realtime', () => ({
  subscribeRealtime: vi.fn(() => ({ close: vi.fn() }))
}))

describe('components/layout/StatusBar', () => {
  let pinia
  let ui

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    ui = useUiStore()
  })

  function mountStatusBar() {
    return mount(StatusBar, { global: { plugins: [pinia] } })
  }

  it('按实时通道状态展示对应文案与圆点', async () => {
    ui.setRealtimeStatus('open')
    const wrapper = mountStatusBar()
    expect(wrapper.text()).toContain('实时通道已连接')
    expect(wrapper.find('.status-bar__dot.is-open').exists()).toBe(true)

    ui.setRealtimeStatus('reconnecting')
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('实时通道重连中')
    expect(wrapper.find('.status-bar__dot.is-reconnecting').exists()).toBe(true)
  })

  it('展示 Broker 状态、在线设备数与版本', () => {
    ui.brokerStatus = 'ok'
    ui.onlineDevices = 3
    ui.totalDevices = 8
    const wrapper = mountStatusBar()
    expect(wrapper.text()).toContain('Broker 正常')
    expect(wrapper.text()).toContain('在线 3/8')
    expect(wrapper.text()).toContain(ui.version)
  })

  it('挂载时启动健康度轮询与实时通道，卸载时释放', () => {
    const startSpy = vi.spyOn(ui, 'startHealthPolling')
    const stopSpy = vi.spyOn(ui, 'stopHealthPolling')
    const wrapper = mountStatusBar()

    expect(startSpy).toHaveBeenCalledTimes(1)
    wrapper.unmount()
    expect(stopSpy).toHaveBeenCalledTimes(1)
  })
})