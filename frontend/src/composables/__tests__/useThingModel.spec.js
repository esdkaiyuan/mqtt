import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

import { ElMessage, ElMessageBox } from 'element-plus'
import { productApi } from '@/api/product'
import { useThingModel } from '../useThingModel'

async function settle() {
  await flushPromises()
  await nextTick()
  await flushPromises()
}

const SAVED_TSL = JSON.stringify({
  schemaVersion: '1.0',
  properties: [
    {
      identifier: 'temperature',
      name: '温度',
      dataType: { type: 'int', min: 0, max: 100, step: 1, unit: '℃' },
      accessMode: 'r',
      required: false,
      description: ''
    }
  ],
  events: [],
  services: []
})

describe('composables/useThingModel', () => {
  let queryClient
  let state
  let wrapper
  let productId
  let productKey

  function mountHarness() {
    const Harness = defineComponent({
      setup() {
        state = useThingModel(productId, productKey)
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
    productId = ref(1)
    productKey = ref('env-sensor')
    productApi.getThingModel = vi.fn().mockResolvedValue({
      code: 200,
      data: { thingModel: SAVED_TSL, version: 3, updatedAt: '2026-10-02T10:00:00' }
    })
    productApi.saveThingModel = vi.fn().mockResolvedValue({ code: 200, data: {} })
    productApi.clearThingModel = vi.fn().mockResolvedValue({ code: 200, data: {} })
    productApi.exportThingModel = vi.fn().mockResolvedValue(new Blob(['{}']))
    productApi.importThingModel = vi.fn().mockResolvedValue({ code: 200, data: {} })
    ElMessageBox.confirm.mockResolvedValue()
    URL.createObjectURL = vi.fn(() => 'blob:mock')
    URL.revokeObjectURL = vi.fn()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  })

  afterEach(() => {
    wrapper?.unmount()
    queryClient.clear()
    vi.restoreAllMocks()
  })

  it('加载后草稿等于服务端定义，版本号就位且无脏标记', async () => {
    mountHarness()
    await settle()

    expect(state.version.value).toBe(3)
    expect(state.draft.value.properties).toHaveLength(1)
    expect(state.draft.value.properties[0].identifier).toBe('temperature')
    expect(state.dirty.value).toBe(false)
  })

  it('修改草稿后置脏，恢复后回到干净', async () => {
    mountHarness()
    await settle()

    state.draft.value.properties.push({
      identifier: 'humidity',
      name: '湿度',
      dataType: { type: 'int', min: 0, max: 100, step: 1, unit: '' },
      accessMode: 'r',
      required: false,
      description: ''
    })
    await nextTick()
    expect(state.dirty.value).toBe(true)

    state.draft.value.properties.pop()
    await nextTick()
    expect(state.dirty.value).toBe(false)
  })

  it('保存以序列化草稿调用接口并在成功后重新拉取', async () => {
    mountHarness()
    await settle()

    state.draft.value.properties[0].name = '环境温度'
    await nextTick()

    const ok = await state.save()
    await settle()

    expect(ok).toBe(true)
    expect(productApi.saveThingModel).toHaveBeenCalledTimes(1)
    const [id, payload] = productApi.saveThingModel.mock.calls[0]
    expect(id).toBe(1)
    expect(JSON.parse(payload).properties[0].name).toBe('环境温度')
    expect(productApi.getThingModel).toHaveBeenCalledTimes(2)
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('导出当前服务端定义并触发下载', async () => {
    mountHarness()
    await settle()

    const ok = await state.exportModel()

    expect(ok).toBe(true)
    expect(productApi.exportThingModel).toHaveBeenCalledWith(1)
    expect(URL.createObjectURL).toHaveBeenCalled()
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled()
  })

  it('导入合法文件调用接口并刷新', async () => {
    mountHarness()
    await settle()

    const file = { text: () => Promise.resolve(SAVED_TSL) }
    const ok = await state.importModel(file)
    await settle()

    expect(ok).toBe(true)
    expect(productApi.importThingModel).toHaveBeenCalledWith(1, SAVED_TSL)
    expect(productApi.getThingModel).toHaveBeenCalledTimes(2)
  })

  it('导入非法 JSON 时提示错误且不调用接口', async () => {
    mountHarness()
    await settle()

    const ok = await state.importModel({ text: () => Promise.resolve('not json') })

    expect(ok).toBe(false)
    expect(ElMessage.error).toHaveBeenCalled()
    expect(productApi.importThingModel).not.toHaveBeenCalled()
  })

  it('清空需二次确认，确认后调用接口', async () => {
    mountHarness()
    await settle()

    const ok = await state.clearModel()
    await settle()

    expect(ok).toBe(true)
    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(productApi.clearThingModel).toHaveBeenCalledWith(1)
  })

  it('无脏标记时可直接离开', async () => {
    mountHarness()
    await settle()

    await expect(state.confirmLeave()).resolves.toBe(true)
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
  })

  it('有脏标记时离开需确认，取消则不放行', async () => {
    mountHarness()
    await settle()

    state.draft.value.properties.pop()
    await nextTick()
    ElMessageBox.confirm.mockRejectedValueOnce(new Error('stay'))

    await expect(state.confirmLeave()).resolves.toBe(false)
    expect(ElMessageBox.confirm).toHaveBeenCalled()
  })
})