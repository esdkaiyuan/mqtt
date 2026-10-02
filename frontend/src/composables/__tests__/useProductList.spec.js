import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ hasRole: (roles) => roles.includes('ADMIN') })
}))

import { ElMessage, ElMessageBox } from 'element-plus'
import { productApi } from '@/api/product'
import { useProductList } from '../useProductList'

async function settle() {
  await flushPromises()
  await nextTick()
  await flushPromises()
}

const ENABLED_PRODUCT = {
  id: 1,
  productKey: 'env-sensor',
  productName: '环境传感器',
  status: 'ENABLED',
  thingModelVersion: 0
}

describe('composables/useProductList', () => {
  let queryClient
  let state
  let wrapper

  function mountHarness() {
    const Harness = defineComponent({
      setup() {
        state = useProductList()
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
    productApi.getList = vi.fn().mockResolvedValue({ code: 200, data: [ENABLED_PRODUCT] })
    productApi.create = vi.fn().mockResolvedValue({ code: 200, data: ENABLED_PRODUCT })
    productApi.update = vi.fn().mockResolvedValue({ code: 200, data: ENABLED_PRODUCT })
    productApi.disable = vi.fn().mockResolvedValue({ code: 200 })
    productApi.enable = vi.fn().mockResolvedValue({ code: 200 })
    productApi.remove = vi.fn().mockResolvedValue({ code: 200 })
    ElMessageBox.confirm.mockResolvedValue()
  })

  afterEach(() => {
    wrapper?.unmount()
    queryClient.clear()
    vi.restoreAllMocks()
  })

  it('挂载后加载产品列表', async () => {
    mountHarness()
    await settle()

    expect(productApi.getList).toHaveBeenCalledTimes(1)
    expect(state.products.value).toEqual([ENABLED_PRODUCT])
    expect(state.canWrite.value).toBe(true)
  })

  it('创建成功后重新拉取列表', async () => {
    mountHarness()
    await settle()

    const ok = await state.createProduct({ productKey: 'p2', productName: 'P2' })
    await settle()

    expect(ok).toBe(true)
    expect(productApi.create).toHaveBeenCalledWith({ productKey: 'p2', productName: 'P2' })
    expect(productApi.getList).toHaveBeenCalledTimes(2)
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('创建失败返回 false 且不刷新列表', async () => {
    productApi.create.mockRejectedValueOnce(new Error('conflict'))
    mountHarness()
    await settle()

    const ok = await state.createProduct({ productKey: 'dup', productName: 'Dup' })
    await settle()

    expect(ok).toBe(false)
    expect(productApi.getList).toHaveBeenCalledTimes(1)
  })

  it('停用先二次确认，确认后调用接口并刷新', async () => {
    mountHarness()
    await settle()

    const ok = await state.toggleProduct(ENABLED_PRODUCT)
    await settle()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(ok).toBe(true)
    expect(productApi.disable).toHaveBeenCalledWith(1)
    expect(productApi.getList).toHaveBeenCalledTimes(2)
  })

  it('停用确认取消时不调用接口', async () => {
    ElMessageBox.confirm.mockRejectedValueOnce(new Error('cancel'))
    mountHarness()
    await settle()

    const ok = await state.toggleProduct(ENABLED_PRODUCT)
    await settle()

    expect(ok).toBe(false)
    expect(productApi.disable).not.toHaveBeenCalled()
    expect(productApi.getList).toHaveBeenCalledTimes(1)
  })

  it('停用已停用产品时调用启用接口', async () => {
    mountHarness()
    await settle()

    const ok = await state.toggleProduct({ ...ENABLED_PRODUCT, status: 'DISABLED' })
    await settle()

    expect(ok).toBe(true)
    expect(productApi.enable).toHaveBeenCalledWith(1)
    expect(productApi.disable).not.toHaveBeenCalled()
  })

  it('删除命中 6003 时返回 false 且不抛异常', async () => {
    productApi.remove.mockRejectedValueOnce(Object.assign(new Error('has devices'), { code: 6003 }))
    mountHarness()
    await settle()

    const ok = await state.deleteProduct(ENABLED_PRODUCT)
    await settle()

    expect(ok).toBe(false)
    expect(productApi.getList).toHaveBeenCalledTimes(1)
  })

  it('删除成功后刷新列表', async () => {
    mountHarness()
    await settle()

    const ok = await state.deleteProduct(ENABLED_PRODUCT)
    await settle()

    expect(ok).toBe(true)
    expect(productApi.remove).toHaveBeenCalledWith(1)
    expect(productApi.getList).toHaveBeenCalledTimes(2)
  })
})