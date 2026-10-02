import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUiStore } from '../ui'

/** 替身：实时通道与设备接口，避免测试触达真实网络。 */
const realtime = vi.hoisted(() => ({
  subscribeRealtime: vi.fn(),
  handle: { close: vi.fn() },
  onStatus: null,
  onEvent: null
}))

const deviceApi = vi.hoisted(() => ({
  getOnline: vi.fn(),
  getList: vi.fn()
}))

vi.mock('@/api/realtime', () => ({
  subscribeRealtime: realtime.subscribeRealtime
}))

vi.mock('@/api/device', () => ({ deviceApi }))

describe('stores/ui', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    realtime.subscribeRealtime.mockReset()
    realtime.handle.close.mockReset()
    realtime.onStatus = null
    realtime.onEvent = null
    realtime.subscribeRealtime.mockImplementation(({ onStatus, onEvent }) => {
      realtime.onStatus = onStatus
      realtime.onEvent = onEvent
      return realtime.handle
    })
    deviceApi.getOnline.mockReset()
    deviceApi.getList.mockReset()
  })

  it('折叠状态写入 localStorage 并在新实例中恢复', () => {
    const ui = useUiStore()
    expect(ui.navCollapsed).toBe(false)

    ui.toggleNav()
    expect(ui.navCollapsed).toBe(true)
    expect(localStorage.getItem('ui.nav.collapsed')).toBe('true')

    setActivePinia(createPinia())
    const restored = useUiStore()
    expect(restored.navCollapsed).toBe(true)
  })

  it('右栏开关写入 localStorage', () => {
    const ui = useUiStore()
    ui.toggleRail()
    expect(ui.railOpen).toBe(false)
    expect(localStorage.getItem('ui.rail.open')).toBe('false')
  })

  describe('视口档位', () => {
    it('紧凑档右栏默认收起且仍为固定栏', () => {
      const ui = useUiStore()
      ui.setViewport('compact')
      expect(ui.isCompact).toBe(true)
      expect(ui.railOpen).toBe(false)
      expect(ui.railAsDrawer).toBe(false)
      expect(ui.navAsDrawer).toBe(false)
    })

    it('平板档左导航为图标态、右栏转抽屉', () => {
      const ui = useUiStore()
      ui.setViewport('narrow')
      expect(ui.isNarrow).toBe(true)
      expect(ui.railAsDrawer).toBe(true)
      expect(ui.navAsDrawer).toBe(false)
    })

    it('手机档左右均转抽屉', () => {
      const ui = useUiStore()
      ui.setViewport('phone')
      expect(ui.isPhone).toBe(true)
      expect(ui.navAsDrawer).toBe(true)
      expect(ui.railAsDrawer).toBe(true)
    })

    it('回到宽屏档右栏恢复展开', () => {
      const ui = useUiStore()
      ui.setViewport('compact')
      expect(ui.railOpen).toBe(false)
      ui.setViewport('wide')
      expect(ui.railOpen).toBe(true)
    })

    it('退出抽屉档位时自动收起抽屉', () => {
      const ui = useUiStore()
      ui.setViewport('phone')
      ui.toggleNavDrawer()
      ui.toggleRailDrawer()
      expect(ui.navDrawerOpen).toBe(true)
      expect(ui.railDrawerOpen).toBe(true)

      ui.setViewport('wide')
      expect(ui.navDrawerOpen).toBe(false)
      expect(ui.railDrawerOpen).toBe(false)
    })
  })

  describe('实时通道与事件缓冲', () => {
    it('全站仅建立一条连接，状态与事件汇聚到 store', () => {
      const ui = useUiStore()
      ui.connectRealtime()
      ui.connectRealtime()
      expect(realtime.subscribeRealtime).toHaveBeenCalledTimes(1)

      realtime.onStatus('open')
      expect(ui.realtimeStatus).toBe('open')

      realtime.onEvent({ topic: 'a/b', payload: '1' })
      realtime.onEvent({ topic: 'a/c', payload: '2' })
      expect(ui.events).toHaveLength(2)
      expect(ui.events[0].id).toBe(1)
      expect(ui.events[1].id).toBe(2)
    })

    it('事件缓冲不超过上限', () => {
      const ui = useUiStore()
      for (let i = 0; i < 250; i += 1) {
        ui.pushEvent({ topic: `t/${i}` })
      }
      expect(ui.events).toHaveLength(200)
      // 缓冲区前移后 id 仍单调递增，列表 key 稳定
      expect(ui.events[0].id).toBe(51)
      expect(ui.events[199].id).toBe(250)
    })

    it('断开连接后句柄被释放', () => {
      const ui = useUiStore()
      ui.connectRealtime()
      ui.disconnectRealtime()
      expect(realtime.handle.close).toHaveBeenCalledTimes(1)

      // 释放后可重新建立连接
      ui.connectRealtime()
      expect(realtime.subscribeRealtime).toHaveBeenCalledTimes(2)
    })
  })

  describe('健康度刷新', () => {
    it('汇聚在线设备数、设备总数与 Broker 状态', async () => {
      deviceApi.getOnline.mockResolvedValue({ data: [{ id: 1 }, { id: 2 }] })
      deviceApi.getList.mockResolvedValue({ data: { total: 10 } })

      const ui = useUiStore()
      await ui.refreshHealth()

      expect(ui.onlineDevices).toBe(2)
      expect(ui.totalDevices).toBe(10)
      expect(ui.brokerStatus).toBe('ok')
      expect(ui.onlineRate).toBe(20)
      expect(ui.lastRefreshAt).toBeGreaterThan(0)
    })

    it('在线接口失败时标记 Broker 异常且不阻塞总数', async () => {
      deviceApi.getOnline.mockRejectedValue(new Error('down'))
      deviceApi.getList.mockResolvedValue({ data: { total: 7 } })

      const ui = useUiStore()
      await ui.refreshHealth()

      expect(ui.brokerStatus).toBe('down')
      expect(ui.totalDevices).toBe(7)
    })
  })
})