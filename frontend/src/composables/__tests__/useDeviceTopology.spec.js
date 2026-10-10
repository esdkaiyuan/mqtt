import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

vi.mock('@/api/device', () => ({
  deviceApi: {
    getList: vi.fn(),
    listSubDevices: vi.fn(),
    attachSubDevice: vi.fn(),
    detachSubDevice: vi.fn(),
    getParentDevice: vi.fn(),
    changeNodeType: vi.fn()
  }
}))

import { deviceApi } from '@/api/device'
import { NODE_TYPE_LABELS, SUB_DEVICE_PAGE_SIZE, useDeviceTopology } from '../useDeviceTopology'

const SUB_ONLINE = {
  id: 21,
  deviceKey: 'sensor-1',
  deviceName: '温度子设备',
  status: 'ONLINE',
  lastSeen: '2026-10-06T09:00:00'
}

const SUB_OFFLINE = {
  id: 22,
  deviceKey: 'sensor-2',
  deviceName: '湿度子设备',
  status: 'OFFLINE',
  lastSeen: null
}

const GATEWAY = { id: 10, deviceName: '车间网关', nodeType: 'GATEWAY' }

const DIRECT_SAME_PRODUCT = {
  id: 31,
  deviceKey: 'sensor-3',
  deviceName: '同产品直连',
  nodeType: 'DIRECT',
  productId: 7
}

const DIRECT_OTHER_PRODUCT = {
  id: 32,
  deviceKey: 'sensor-4',
  deviceName: '异产品直连',
  nodeType: 'DIRECT',
  productId: 9
}

function setup(deviceId = 10) {
  const deviceIdRef = ref(deviceId)
  const state = useDeviceTopology(deviceIdRef)
  return { deviceIdRef, state }
}

function deferred() {
  let resolve
  const promise = new Promise((done) => {
    resolve = done
  })
  return { promise, resolve }
}

describe('composables/useDeviceTopology', () => {
  beforeEach(() => {
    deviceApi.getList.mockResolvedValue({
      code: 200,
      data: { records: [DIRECT_SAME_PRODUCT, DIRECT_OTHER_PRODUCT], total: 2 }
    })
    deviceApi.listSubDevices.mockResolvedValue({
      code: 200,
      data: { records: [SUB_ONLINE, SUB_OFFLINE], total: 2 }
    })
    deviceApi.attachSubDevice.mockResolvedValue({ code: 200, data: SUB_ONLINE })
    deviceApi.detachSubDevice.mockResolvedValue({ code: 200, data: null })
    deviceApi.getParentDevice.mockResolvedValue({ code: 200, data: GATEWAY })
    deviceApi.changeNodeType.mockResolvedValue({ code: 200, data: { ...GATEWAY, nodeType: 'DIRECT' } })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('节点类型标签与后端枚举一一对应', () => {
    expect(Object.keys(NODE_TYPE_LABELS).sort()).toEqual(['DIRECT', 'GATEWAY', 'SUB'])
    expect(NODE_TYPE_LABELS.SUB).toBe('子设备')
  })

  it('loadSubDevices 按当前设备 ID 拉取子设备并取 records', async () => {
    const { state } = setup(10)

    await state.loadSubDevices()

    expect(deviceApi.listSubDevices).toHaveBeenCalledWith(10, {
      pageNum: 1,
      pageSize: SUB_DEVICE_PAGE_SIZE
    })
    expect(state.subDevices.value).toEqual([SUB_ONLINE, SUB_OFFLINE])
  })

  it('loadSubDevices 在响应缺 records 时回落为空数组', async () => {
    deviceApi.listSubDevices.mockResolvedValue({ code: 200, data: null })
    const { state } = setup(10)

    await state.loadSubDevices()

    expect(state.subDevices.value).toEqual([])
  })

  it('loadSubDevices 在设备 ID 未就绪时不发请求', async () => {
    const { state } = setup(null)

    await state.loadSubDevices()

    expect(deviceApi.listSubDevices).not.toHaveBeenCalled()
    expect(state.subDevices.value).toEqual([])
  })

  it('loadSubDevices 请求期间置加载态', async () => {
    const pending = deferred()
    deviceApi.listSubDevices.mockReturnValue(pending.promise)
    const { state } = setup(10)

    const task = state.loadSubDevices()
    expect(state.subDevicesLoading.value).toBe(true)

    pending.resolve({ code: 200, data: { records: [SUB_ONLINE], total: 1 } })
    await task

    expect(state.subDevicesLoading.value).toBe(false)
  })

  it('attachSubDevice 调挂载端点并重载子设备列表', async () => {
    const { state } = setup(10)

    const device = await state.attachSubDevice(21)

    expect(deviceApi.attachSubDevice).toHaveBeenCalledWith(10, 21)
    expect(device).toEqual(SUB_ONLINE)
    expect(deviceApi.listSubDevices).toHaveBeenCalledTimes(1)
  })

  it('attachSubDevice 缺子设备 ID 时直接返回 null', async () => {
    const { state } = setup(10)

    const device = await state.attachSubDevice(null)

    expect(device).toBeNull()
    expect(deviceApi.attachSubDevice).not.toHaveBeenCalled()
  })

  it('detachSubDevice 调解绑端点并重载子设备列表', async () => {
    const { state } = setup(10)

    const done = await state.detachSubDevice(21)

    expect(deviceApi.detachSubDevice).toHaveBeenCalledWith(10, 21)
    expect(done).toBe(true)
    expect(deviceApi.listSubDevices).toHaveBeenCalledTimes(1)
  })

  it('detachSubDevice 缺子设备 ID 时直接返回 false', async () => {
    const { state } = setup(10)

    const done = await state.detachSubDevice(undefined)

    expect(done).toBe(false)
    expect(deviceApi.detachSubDevice).not.toHaveBeenCalled()
  })

  it('changeNodeType 透传拓扑角色取值', async () => {
    const { state } = setup(10)

    const device = await state.changeNodeType('DIRECT')

    expect(deviceApi.changeNodeType).toHaveBeenCalledWith(10, 'DIRECT')
    expect(device.nodeType).toBe('DIRECT')
  })

  it('changeNodeType 缺取值时不发请求', async () => {
    const { state } = setup(10)

    const device = await state.changeNodeType('')

    expect(device).toBeNull()
    expect(deviceApi.changeNodeType).not.toHaveBeenCalled()
  })

  it('loadParent 命中父网关', async () => {
    const { state } = setup(21)

    const parent = await state.loadParent()

    expect(deviceApi.getParentDevice).toHaveBeenCalledWith(21)
    expect(state.parent.value).toEqual(GATEWAY)
    expect(parent).toEqual(GATEWAY)
  })

  it('loadParent 在无父设备时回落为 null', async () => {
    deviceApi.getParentDevice.mockResolvedValue({ code: 200, data: null })
    const { state } = setup(21)

    await state.loadParent()

    expect(state.parent.value).toBeNull()
  })

  it('loadCandidates 按产品拉取可挂载的直连设备', async () => {
    const { state } = setup(10)

    const devices = await state.loadCandidates(7)

    expect(deviceApi.getList).toHaveBeenCalledWith({
      nodeType: 'DIRECT',
      productId: 7,
      pageNum: 1,
      pageSize: SUB_DEVICE_PAGE_SIZE
    })
    expect(state.candidates.value).toEqual([DIRECT_SAME_PRODUCT, DIRECT_OTHER_PRODUCT])
    expect(devices).toEqual([DIRECT_SAME_PRODUCT, DIRECT_OTHER_PRODUCT])
  })

  it('loadCandidates 在产品 ID 未就绪时不发请求', async () => {
    const { state } = setup(10)

    const devices = await state.loadCandidates(null)

    expect(deviceApi.getList).not.toHaveBeenCalled()
    expect(state.candidates.value).toEqual([])
    expect(devices).toEqual([])
  })

  it('loadCandidates 请求期间置加载态', async () => {
    const pending = deferred()
    deviceApi.getList.mockReturnValue(pending.promise)
    const { state } = setup(10)

    const task = state.loadCandidates(7)
    expect(state.candidatesLoading.value).toBe(true)

    pending.resolve({ code: 200, data: { records: [DIRECT_SAME_PRODUCT], total: 1 } })
    await task

    expect(state.candidatesLoading.value).toBe(false)
  })
})
