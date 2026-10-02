import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'

vi.mock('@/api/device', () => ({
  deviceApi: {
    getDeviceShadow: vi.fn(),
    setDeviceShadowDesired: vi.fn()
  }
}))

import { deviceApi } from '@/api/device'
import { useDeviceShadow } from '../useDeviceShadow'

const SHADOW = {
  modeled: true,
  version: 3,
  desired: { targetTemp: '26' },
  reported: { targetTemp: '24' },
  delta: { targetTemp: '26' },
  updatedAt: '2026-10-02T10:00:00.000'
}

const EMPTY_SHADOW = {
  modeled: false,
  version: 0,
  desired: {},
  reported: {},
  delta: {},
  updatedAt: null
}

async function settle() {
  await flushPromises()
  await nextTick()
  await flushPromises()
}

describe('composables/useDeviceShadow', () => {
  let queryClient
  let state
  let wrapper
  let deviceKey

  function mountHarness() {
    const Harness = defineComponent({
      setup() {
        state = useDeviceShadow(deviceKey)
        return () => h('div')
      }
    })
    wrapper = mount(Harness, {
      global: { plugins: [[VueQueryPlugin, { queryClient }]] }
    })
    return wrapper
  }

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: Infinity, gcTime: Infinity } }
    })
    deviceKey = ref('dev-1')
    deviceApi.getDeviceShadow.mockResolvedValue({ code: 200, data: SHADOW })
    deviceApi.setDeviceShadowDesired.mockResolvedValue({
      code: 200,
      data: { commandId: 'cmd-9', status: 'QUEUED' }
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    queryClient.clear()
  })

  it('挂载后加载影子的三份状态与版本号', async () => {
    mountHarness()
    await settle()

    expect(deviceApi.getDeviceShadow).toHaveBeenCalledWith('dev-1')
    expect(state.modeled.value).toBe(true)
    expect(state.version.value).toBe(3)
    expect(state.desired.value).toEqual({ targetTemp: '26' })
    expect(state.reported.value).toEqual({ targetTemp: '24' })
    expect(state.delta.value).toEqual({ targetTemp: '26' })
    expect(state.updatedAt.value).toBe('2026-10-02T10:00:00.000')
  })

  it('deviceKey 未就绪时不发请求', async () => {
    deviceKey.value = ''
    mountHarness()
    await settle()

    expect(deviceApi.getDeviceShadow).not.toHaveBeenCalled()
  })

  it('查询数据为 null 时回退到空影子', async () => {
    deviceApi.getDeviceShadow.mockResolvedValue({ code: 200, data: null })
    mountHarness()
    await settle()

    expect(state.shadow.value).toEqual(EMPTY_SHADOW)
    expect(state.modeled.value).toBe(false)
    expect(state.version.value).toBe(0)
  })

  it('写入期望值解包记录，并在成功后刷新影子与命令记录', async () => {
    mountHarness()
    await settle()
    expect(deviceApi.getDeviceShadow).toHaveBeenCalledTimes(1)

    const record = await state.setDesired({ targetTemp: 26 })
    await settle()

    expect(deviceApi.setDeviceShadowDesired).toHaveBeenCalledWith('dev-1', { targetTemp: 26 })
    expect(record).toEqual({ commandId: 'cmd-9', status: 'QUEUED' })
    expect(deviceApi.getDeviceShadow).toHaveBeenCalledTimes(2)
  })
})
