import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { useDeviceList } from '../useDeviceList'

/** store 以替身注入，便于断言每次加载携带的查询参数。 */
const deviceStore = vi.hoisted(() => ({
  fetchDevices: vi.fn(() => Promise.resolve())
}))

vi.mock('@/stores/device', () => ({
  useDeviceStore: () => ({
    devices: [{ id: 'd1' }],
    totalDevices: 100,
    loading: false,
    fetchDevices: deviceStore.fetchDevices
  })
}))

async function settle() {
  await flushPromises()
  await nextTick()
}

describe('composables/useDeviceList', () => {
  let state
  let wrapper

  function mountHarness() {
    const Harness = defineComponent({
      setup() {
        state = useDeviceList()
        return () => h('div')
      }
    })
    wrapper = mount(Harness)
    return wrapper
  }

  beforeEach(() => {
    deviceStore.fetchDevices.mockClear()
  })

  afterEach(() => {
    wrapper?.unmount()
  })

  it('挂载后默认加载第 1 页，空筛选不下发空串参数', async () => {
    mountHarness()
    await settle()

    expect(deviceStore.fetchDevices).toHaveBeenCalledTimes(1)
    expect(deviceStore.fetchDevices).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 10,
      deviceName: undefined,
      deviceType: undefined,
      status: undefined
    })
    expect(state.currentPage.value).toBe(1)
    expect(state.totalPages.value).toBe(10)
  })

  it('翻页以新页码重新加载（分页参数变化触发重新查询）', async () => {
    mountHarness()
    await settle()

    state.goToPage(3)
    await settle()

    expect(state.currentPage.value).toBe(3)
    expect(deviceStore.fetchDevices).toHaveBeenLastCalledWith({
      pageNum: 3,
      pageSize: 10,
      deviceName: undefined,
      deviceType: undefined,
      status: undefined
    })
    expect(deviceStore.fetchDevices).toHaveBeenCalledTimes(2)
  })

  it('越界页码不触发加载', async () => {
    mountHarness()
    await settle()

    state.goToPage(0)
    state.goToPage(11)
    await settle()

    expect(state.currentPage.value).toBe(1)
    expect(deviceStore.fetchDevices).toHaveBeenCalledTimes(1)
  })

  it('查询时带上筛选条件并回到第 1 页', async () => {
    mountHarness()
    await settle()

    state.goToPage(2)
    await settle()
    state.filters.deviceType = 'SENSOR'
    state.filters.status = 'ONLINE'
    state.handleSearch()
    await settle()

    expect(state.currentPage.value).toBe(1)
    expect(deviceStore.fetchDevices).toHaveBeenLastCalledWith({
      pageNum: 1,
      pageSize: 10,
      deviceName: undefined,
      deviceType: 'SENSOR',
      status: 'ONLINE'
    })
  })

  it('重置清空筛选并重新加载第 1 页', async () => {
    mountHarness()
    await settle()

    state.filters.deviceName = '温湿度'
    state.handleSearch()
    await settle()

    state.handleReset()
    await settle()

    expect(state.filters.deviceName).toBe('')
    expect(state.currentPage.value).toBe(1)
    expect(deviceStore.fetchDevices).toHaveBeenLastCalledWith({
      pageNum: 1,
      pageSize: 10,
      deviceName: undefined,
      deviceType: undefined,
      status: undefined
    })
  })
})
