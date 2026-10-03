import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { deviceApi } from '@/api/device'
import { useDeviceLog } from '../useDeviceLog'

/** 让一条查询走完 microtask 链并触发 Vue 的响应式更新。 */
async function settle() {
  await flushPromises()
  await nextTick()
  await flushPromises()
  await new Promise((resolve) => setTimeout(resolve, 0))
  await flushPromises()
}

/** 把 `yyyy-MM-dd HH:mm:ss` 还原成 Date，便于校验默认时间窗跨度。 */
function toDate(value) {
  return value ? new Date(value.replace(' ', 'T')) : null
}

describe('composables/useDeviceLog', () => {
  let queryClient
  let state
  let wrapper

  /** 把 composable 挂进真实组件，才能拿到 queryKey 变更的响应式链路。 */
  function mountHarness(deviceIdRef) {
    const Harness = defineComponent({
      setup() {
        state = useDeviceLog(deviceIdRef)
        return () => h('div')
      }
    })
    wrapper = mount(Harness, {
      global: { plugins: [[VueQueryPlugin, { queryClient }]] }
    })
    return wrapper
  }

  /** 取最近一次 getDeviceLogs 的 (deviceId, params)。 */
  function lastCall() {
    return deviceApi.getDeviceLogs.mock.calls.at(-1)
  }

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        // 不重试、不自动回收；缓存到 staleTime 之外才重新请求，
        // 以便断言「翻回已查过的页码直接命中缓存」。
        queries: { retry: false, staleTime: Infinity, gcTime: Infinity }
      }
    })
    deviceApi.getDeviceLogs = vi.fn().mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { records: [{ logId: 'l-1' }], total: 100 }
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    queryClient.clear()
    vi.restoreAllMocks()
  })

  it('deviceId 为空时不发起请求', async () => {
    mountHarness(ref(null))
    await settle()

    expect(deviceApi.getDeviceLogs).not.toHaveBeenCalled()
    expect(state.items.value).toEqual([])
    expect(state.total.value).toBe(0)
    expect(state.totalPages.value).toBe(1)
  })

  it('deviceId 就绪后自动按默认条件查询一次', async () => {
    const deviceId = ref(null)
    mountHarness(deviceId)
    await settle()
    expect(deviceApi.getDeviceLogs).not.toHaveBeenCalled()

    deviceId.value = 7
    await settle()

    expect(deviceApi.getDeviceLogs).toHaveBeenCalledTimes(1)
    const [id, params] = lastCall()
    expect(id).toBe(7)
    expect(params.pageNum).toBe(1)
    expect(params.pageSize).toBe(20)
    expect(state.items.value).toEqual([{ logId: 'l-1' }])
    expect(state.total.value).toBe(100)
  })

  it('默认时间窗为「近 24 小时」且格式与后端一致', async () => {
    mountHarness(ref(7))
    await settle()

    const [, params] = lastCall()
    expect(params.startTime).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
    expect(params.endTime).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)

    const spanHours = (toDate(params.endTime) - toDate(params.startTime)) / 3600000
    expect(spanHours).toBeGreaterThan(23.9)
    expect(spanHours).toBeLessThan(24.1)
    // 没有筛选时 types / keyword 不应进入参数
    expect(params).not.toHaveProperty('types')
    expect(params).not.toHaveProperty('keyword')
  })

  it('提交时按草稿表单生成条件，types 逗号分隔', async () => {
    mountHarness(ref(7))
    await settle()
    deviceApi.getDeviceLogs.mockClear()

    state.form.types = ['MESSAGE', 'COMMAND']
    state.form.keyword = '温度'
    state.submit()
    await settle()

    expect(deviceApi.getDeviceLogs).toHaveBeenCalledTimes(1)
    const [, params] = lastCall()
    expect(params.pageNum).toBe(1)
    expect(params.pageSize).toBe(20)
    expect(params.types).toBe('MESSAGE,COMMAND')
    expect(params.keyword).toBe('温度')
  })

  it('未提交的草稿改动不影响已提交条件', async () => {
    mountHarness(ref(7))
    await settle()
    deviceApi.getDeviceLogs.mockClear()

    state.form.keyword = '草稿关键字'
    await settle()

    expect(deviceApi.getDeviceLogs).not.toHaveBeenCalled()

    state.submit()
    await settle()

    const [, params] = lastCall()
    expect(params.keyword).toBe('草稿关键字')
  })

  it('翻页以新的分页参数重新查询', async () => {
    mountHarness(ref(7))
    await settle()
    expect(deviceApi.getDeviceLogs).toHaveBeenCalledTimes(1)

    state.goToPage(2)
    await settle()

    expect(state.pageNum.value).toBe(2)
    expect(deviceApi.getDeviceLogs).toHaveBeenCalledTimes(2)
    expect(lastCall()[1].pageNum).toBe(2)
  })

  it('翻回已查页码直接命中缓存，不重复请求', async () => {
    mountHarness(ref(7))
    await settle()
    state.goToPage(2)
    await settle()
    expect(deviceApi.getDeviceLogs).toHaveBeenCalledTimes(2)

    state.goToPage(1)
    await settle()

    expect(state.pageNum.value).toBe(1)
    expect(deviceApi.getDeviceLogs).toHaveBeenCalledTimes(2)
  })

  it('越界页码被忽略', async () => {
    mountHarness(ref(7))
    await settle()
    const calls = deviceApi.getDeviceLogs.mock.calls.length

    state.goToPage(0)
    state.goToPage(99)
    await settle()

    expect(state.pageNum.value).toBe(1)
    expect(deviceApi.getDeviceLogs.mock.calls.length).toBe(calls)
  })

  it('条件未变时再次提交仍会重新拉取（searchSeq 递增）', async () => {
    mountHarness(ref(7))
    await settle()
    expect(deviceApi.getDeviceLogs).toHaveBeenCalledTimes(1)

    state.submit()
    await settle()

    expect(deviceApi.getDeviceLogs).toHaveBeenCalledTimes(2)
    expect(deviceApi.getDeviceLogs.mock.calls[0][1]).toEqual(deviceApi.getDeviceLogs.mock.calls[1][1])
  })

  it('重置恢复默认近 24 小时条件、回到第 1 页并重新拉取', async () => {
    mountHarness(ref(7))
    await settle()

    state.form.types = ['EVENT']
    state.form.keyword = '告警'
    state.submit()
    await settle()
    state.goToPage(3)
    await settle()
    const callsBeforeReset = deviceApi.getDeviceLogs.mock.calls.length

    state.reset()
    await settle()

    expect(state.pageNum.value).toBe(1)
    expect(state.form.types).toEqual([])
    expect(state.form.keyword).toBe('')
    expect(deviceApi.getDeviceLogs.mock.calls.length).toBe(callsBeforeReset + 1)

    const [, params] = lastCall()
    expect(params.pageNum).toBe(1)
    expect(params).not.toHaveProperty('types')
    expect(params).not.toHaveProperty('keyword')
    const spanHours = (toDate(params.endTime) - toDate(params.startTime)) / 3600000
    expect(spanHours).toBeGreaterThan(23.9)
    expect(spanHours).toBeLessThan(24.1)
  })

  it('totalPages 按总数向上取整，至少为 1', async () => {
    deviceApi.getDeviceLogs = vi.fn().mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { records: [], total: 0 }
    })
    mountHarness(ref(7))
    await settle()

    expect(state.total.value).toBe(0)
    expect(state.totalPages.value).toBe(1)

    deviceApi.getDeviceLogs.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { records: [], total: 45 }
    })
    state.submit()
    await settle()

    expect(state.totalPages.value).toBe(3)
  })

  it('返回体缺 data 时回退空分页', async () => {
    deviceApi.getDeviceLogs = vi.fn().mockResolvedValue({ code: 200, message: 'ok', data: null })
    mountHarness(ref(7))
    await settle()

    expect(state.items.value).toEqual([])
    expect(state.total.value).toBe(0)
    expect(state.totalPages.value).toBe(1)
  })
})
