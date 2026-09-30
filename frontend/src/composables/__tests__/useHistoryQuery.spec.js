import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { historyApi } from '@/api/history'
import { useHistoryQuery } from '../useHistoryQuery'

/**
 * 历史查询页只依赖 device store 拉筛选项，这里以替身隔离，
 * 让用例聚焦在「分页 / 筛选条件变化 → 重新查询」这条主链路上。
 */
vi.mock('@/stores/device', () => ({
  useDeviceStore: () => ({ devices: [], fetchDevices: () => Promise.resolve() })
}))

/** 让一条查询走完 microtask 链并触发 Vue 的响应式更新。 */
async function settle() {
  await flushPromises()
  await nextTick()
  await flushPromises()
  await new Promise((resolve) => setTimeout(resolve, 0))
  await flushPromises()
}

describe('composables/useHistoryQuery', () => {
  let queryClient
  let state
  let wrapper

  /** 把 composable 挂进真实组件，才能拿到 onMounted 与 queryKey 变更的响应式链路。 */
  function mountHarness() {
    const Harness = defineComponent({
      setup() {
        state = useHistoryQuery()
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
      defaultOptions: {
        // 不重试、不自动回收；缓存到 staleTime 之外才重新请求，
        // 以便断言「翻回已查过的页码直接命中缓存」。
        queries: { retry: false, staleTime: Infinity, gcTime: Infinity }
      }
    })
    historyApi.query = vi.fn().mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { records: [{ id: 1 }], total: 100 }
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    queryClient.clear()
    vi.restoreAllMocks()
  })

  it('未点击查询前不发起历史记录请求', async () => {
    mountHarness()
    await settle()

    expect(historyApi.query).not.toHaveBeenCalled()
    expect(state.hasSearched.value).toBe(false)
    expect(state.records.value).toEqual([])
  })

  it('提交查询时使用默认分页（第 1 页 / 每页 20 条）', async () => {
    mountHarness()

    state.submit()
    await settle()

    expect(historyApi.query).toHaveBeenCalledTimes(1)
    expect(historyApi.query).toHaveBeenLastCalledWith({ pageNum: 1, pageSize: 20 })
    expect(state.hasSearched.value).toBe(true)
    expect(state.total.value).toBe(100)
    expect(state.totalPages.value).toBe(5)
  })

  it('只把有值的筛选项放进请求参数', async () => {
    mountHarness()

    state.form.topic = 'sensor/#'
    state.form.deviceId = 'dev-1'
    state.submit()
    await settle()

    const params = historyApi.query.mock.calls.at(-1)[0]
    expect(params).toEqual({ pageNum: 1, pageSize: 20, deviceId: 'dev-1', topic: 'sensor/#' })
    expect(params).not.toHaveProperty('startTime')
    expect(params).not.toHaveProperty('endTime')
  })

  it('翻页会以新的分页参数重新查询', async () => {
    mountHarness()

    state.submit()
    await settle()
    expect(historyApi.query).toHaveBeenCalledTimes(1)

    state.goToPage(2)
    await settle()

    expect(state.currentPage.value).toBe(2)
    expect(historyApi.query).toHaveBeenCalledTimes(2)
    expect(historyApi.query).toHaveBeenLastCalledWith({ pageNum: 2, pageSize: 20 })
  })

  it('翻回已查询过的页码直接命中缓存，不重复请求', async () => {
    mountHarness()

    state.submit()
    await settle()
    state.goToPage(2)
    await settle()
    expect(historyApi.query).toHaveBeenCalledTimes(2)

    state.goToPage(1)
    await settle()

    expect(state.currentPage.value).toBe(1)
    expect(historyApi.query).toHaveBeenCalledTimes(2)
  })

  it('修改每页条数会重置到第 1 页并重新查询', async () => {
    mountHarness()

    state.submit()
    await settle()
    state.goToPage(3)
    await settle()

    state.changePageSize(50)
    await settle()

    expect(state.currentPage.value).toBe(1)
    expect(state.pageSize.value).toBe(50)
    expect(historyApi.query).toHaveBeenLastCalledWith({ pageNum: 1, pageSize: 50 })
  })

  it('越界页码被忽略', async () => {
    mountHarness()

    state.submit()
    await settle()
    const callsAfterSearch = historyApi.query.mock.calls.length

    state.goToPage(0)
    state.goToPage(99)
    await settle()

    expect(state.currentPage.value).toBe(1)
    expect(historyApi.query.mock.calls.length).toBe(callsAfterSearch)
  })

  it('条件未变时再次提交仍会重新拉取（searchSeq 递增）', async () => {
    mountHarness()

    state.submit()
    await settle()
    expect(historyApi.query).toHaveBeenCalledTimes(1)

    state.submit()
    await settle()

    expect(historyApi.query).toHaveBeenCalledTimes(2)
    expect(historyApi.query.mock.calls[0][0]).toEqual(historyApi.query.mock.calls[1][0])
  })

  it('重置后回到未查询态且不再新增请求', async () => {
    mountHarness()

    state.form.deviceId = 'dev-1'
    state.submit()
    await settle()
    state.goToPage(2)
    await settle()
    const calls = historyApi.query.mock.calls.length

    state.reset()
    await settle()

    expect(state.hasSearched.value).toBe(false)
    expect(state.currentPage.value).toBe(1)
    expect(state.form.deviceId).toBe('')
    expect(historyApi.query.mock.calls.length).toBe(calls)
  })
})
