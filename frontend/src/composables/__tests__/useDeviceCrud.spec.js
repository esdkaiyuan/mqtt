import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { GATEWAY_PAGE_SIZE, useDeviceCrud } from '../useDeviceCrud'

/** store 与两个 api 均以替身注入，聚焦编排逻辑本身。 */
const deviceStoreMock = vi.hoisted(() => ({
  createDevice: vi.fn(),
  updateDevice: vi.fn(),
  deleteDevice: vi.fn()
}))

vi.mock('@/stores/device', () => ({
  useDeviceStore: () => deviceStoreMock
}))

const deviceApiMock = vi.hoisted(() => ({
  getList: vi.fn(),
  getParentDevice: vi.fn(),
  attachSubDevice: vi.fn()
}))

vi.mock('@/api/device', () => ({ deviceApi: deviceApiMock }))

const productApiMock = vi.hoisted(() => ({ getList: vi.fn() }))

vi.mock('@/api/product', () => ({ productApi: productApiMock }))

const GATEWAY_A = { id: 10, deviceName: '车间网关', deviceKey: 'gw-1' }
const GATEWAY_B = { id: 11, deviceName: '楼宇网关', deviceKey: 'gw-2' }

const SUB_DEVICE = { id: 5, deviceName: '温度传感器', nodeType: 'SUB' }
const DIRECT_DEVICE = { id: 6, deviceName: '独立电表', nodeType: 'DIRECT' }

const CREATE_FORM = {
  productId: 7,
  parentGatewayId: 10,
  deviceName: '温度传感器',
  deviceKey: 'dev-1',
  deviceType: 'sensor',
  topic: 'device/dev-1/data'
}

async function settle() {
  await flushPromises()
  await nextTick()
}

describe('composables/useDeviceCrud', () => {
  let state
  let wrapper

  function mountHarness() {
    const Harness = defineComponent({
      setup() {
        state = useDeviceCrud()
        return () => h('div')
      }
    })
    wrapper = mount(Harness)
    return state
  }

  beforeEach(() => {
    vi.clearAllMocks()
    deviceApiMock.getList.mockResolvedValue({
      code: 200,
      data: { records: [GATEWAY_A, GATEWAY_B], total: 2 }
    })
    deviceApiMock.attachSubDevice.mockResolvedValue({ code: 200, data: null })
    deviceApiMock.getParentDevice.mockResolvedValue({ code: 200, data: GATEWAY_A })
    deviceStoreMock.createDevice.mockResolvedValue({
      id: 42,
      username: 'dev-1',
      deviceSecret: 'secret'
    })
    productApiMock.getList.mockResolvedValue({ code: 200, data: [] })
  })

  afterEach(() => {
    wrapper?.unmount()
  })

  it('loadGateways 按产品拉取网关候选', async () => {
    const sut = mountHarness()

    await sut.loadGateways(7)

    expect(deviceApiMock.getList).toHaveBeenCalledWith({
      nodeType: 'GATEWAY',
      productId: 7,
      pageNum: 1,
      pageSize: GATEWAY_PAGE_SIZE
    })
    expect(sut.gateways.value).toEqual([GATEWAY_A, GATEWAY_B])
    expect(sut.gatewaysLoading.value).toBe(false)
  })

  it('loadGateways 在产品未选时清空候选且不发请求', async () => {
    const sut = mountHarness()
    sut.gateways.value = [GATEWAY_A]

    await sut.loadGateways('')

    expect(deviceApiMock.getList).not.toHaveBeenCalled()
    expect(sut.gateways.value).toEqual([])
  })

  it('handleProductChange 切换产品后重新拉取候选', async () => {
    const sut = mountHarness()

    await sut.handleProductChange(7)
    await settle()

    expect(deviceApiMock.getList).toHaveBeenCalledTimes(1)
    expect(sut.gateways.value).toEqual([GATEWAY_A, GATEWAY_B])
  })

  it('createDevice 选定父网关时在创建成功后挂载为子设备', async () => {
    const sut = mountHarness()

    const ok = await sut.createDevice({ ...CREATE_FORM })

    expect(deviceStoreMock.createDevice).toHaveBeenCalledWith({
      productId: 7,
      deviceName: '温度传感器',
      deviceKey: 'dev-1',
      deviceType: 'sensor',
      topic: 'device/dev-1/data'
    })
    expect(deviceApiMock.attachSubDevice).toHaveBeenCalledWith(10, 42)
    expect(ok).toBe(true)
    expect(sut.showCreateDialog.value).toBe(false)
    expect(sut.showCredentialDialog.value).toBe(true)
    expect(sut.createdCredential.value).toEqual({ username: 'dev-1', deviceSecret: 'secret' })
  })

  it('createDevice 未选父网关时不发起挂载', async () => {
    const sut = mountHarness()

    await sut.createDevice({ ...CREATE_FORM, parentGatewayId: '' })

    expect(deviceApiMock.attachSubDevice).not.toHaveBeenCalled()
  })

  it('createDevice 挂载失败仍保留已创建设备与凭据', async () => {
    deviceApiMock.attachSubDevice.mockRejectedValue(new Error('attach failed'))
    const sut = mountHarness()

    const ok = await sut.createDevice({ ...CREATE_FORM })

    expect(ok).toBe(true)
    expect(sut.showCredentialDialog.value).toBe(true)
    expect(sut.createdCredential.value.deviceSecret).toBe('secret')
  })

  it('openEditDialog 打开子设备时拉取所属网关', async () => {
    const sut = mountHarness()

    sut.openEditDialog(SUB_DEVICE)
    await settle()

    expect(sut.showEditDialog.value).toBe(true)
    expect(sut.editingDevice.value).toEqual(SUB_DEVICE)
    expect(deviceApiMock.getParentDevice).toHaveBeenCalledWith(5)
    expect(sut.parentGateway.value).toEqual(GATEWAY_A)
  })

  it('openEditDialog 打开普通设备时不查询所属网关', async () => {
    const sut = mountHarness()
    sut.parentGateway.value = GATEWAY_A

    sut.openEditDialog(DIRECT_DEVICE)
    await settle()

    expect(deviceApiMock.getParentDevice).not.toHaveBeenCalled()
    expect(sut.parentGateway.value).toBeNull()
  })

  it('openEditDialog 查询所属网关失败时归为无父网关', async () => {
    deviceApiMock.getParentDevice.mockRejectedValue(new Error('404'))
    const sut = mountHarness()

    sut.openEditDialog(SUB_DEVICE)
    await settle()

    expect(sut.parentGateway.value).toBeNull()
    expect(sut.showEditDialog.value).toBe(true)
  })
})
