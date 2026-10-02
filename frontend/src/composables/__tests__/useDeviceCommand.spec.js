import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'

vi.mock('@/api/device', () => ({
  deviceApi: {
    getCommandCapability: vi.fn(),
    sendCommand: vi.fn(),
    getCommandRecords: vi.fn()
  }
}))

import { deviceApi } from '@/api/device'
import { useDeviceCommand } from '../useDeviceCommand'

const CAPABILITY = {
  modeled: true,
  version: 2,
  properties: [{ identifier: 'targetTemp', type: 'int' }],
  services: [{ identifier: 'reboot', callType: 'async', input: [] }]
}

const PAGE = { records: [{ commandId: 'cmd-1', status: 'SENT' }], total: 1 }

async function settle() {
  await flushPromises()
  await nextTick()
  await flushPromises()
}

describe('composables/useDeviceCommand', () => {
  let queryClient
  let state
  let wrapper
  let deviceKey
  let page
  let size

  function mountHarness() {
    const Harness = defineComponent({
      setup() {
        state = useDeviceCommand(deviceKey, page, size)
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
    page = ref(1)
    size = ref(10)
    deviceApi.getCommandCapability.mockResolvedValue({ code: 200, data: CAPABILITY })
    deviceApi.getCommandRecords.mockResolvedValue({ code: 200, data: PAGE })
    deviceApi.sendCommand.mockResolvedValue({
      code: 200,
      data: { commandId: 'cmd-9', status: 'ACKED' }
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    queryClient.clear()
  })

  it('挂载后加载能力与首页记录', async () => {
    mountHarness()
    await settle()

    expect(deviceApi.getCommandCapability).toHaveBeenCalledWith('dev-1')
    expect(deviceApi.getCommandRecords).toHaveBeenCalledWith('dev-1', { page: 1, size: 10 })
    expect(state.capability.value.modeled).toBe(true)
    expect(state.capability.value.version).toBe(2)
    expect(state.records.value).toHaveLength(1)
    expect(state.recordsTotal.value).toBe(1)
  })

  it('deviceKey 未就绪时不发请求', async () => {
    deviceKey.value = ''
    mountHarness()
    await settle()

    expect(deviceApi.getCommandCapability).not.toHaveBeenCalled()
    expect(deviceApi.getCommandRecords).not.toHaveBeenCalled()
  })

  it('翻页以新页码重新查询', async () => {
    mountHarness()
    await settle()

    page.value = 2
    await settle()

    expect(deviceApi.getCommandRecords).toHaveBeenLastCalledWith('dev-1', { page: 2, size: 10 })
  })

  it('下发命令解包返回记录，并在成功后刷新记录列表', async () => {
    mountHarness()
    await settle()
    expect(deviceApi.getCommandRecords).toHaveBeenCalledTimes(1)

    const record = await state.sendCommand({
      type: 'property_set',
      identifier: null,
      params: { targetTemp: 26 },
      callType: 'sync'
    })
    await settle()

    expect(deviceApi.sendCommand).toHaveBeenCalledWith('dev-1', {
      type: 'property_set',
      identifier: null,
      params: { targetTemp: 26 },
      callType: 'sync'
    })
    expect(record).toEqual({ commandId: 'cmd-9', status: 'ACKED' })
    expect(deviceApi.getCommandRecords).toHaveBeenCalledTimes(2)
  })
})