import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'

vi.mock('@/api/device', () => ({
  deviceApi: {
    batchCommands: vi.fn(),
    batchEnable: vi.fn(),
    batchDisable: vi.fn(),
    batchAssignGroup: vi.fn(),
    batchAssignTag: vi.fn()
  }
}))

import { deviceApi } from '@/api/device'
import { useDeviceBatch } from '../useDeviceBatch'

const RESULT = { total: 2, succeeded: 1, failed: 1, items: [{ deviceKey: 'd-1', success: true }] }

async function settle() {
  await flushPromises()
  await nextTick()
  await flushPromises()
}

describe('composables/useDeviceBatch', () => {
  let queryClient
  let state
  let wrapper

  function mountHarness() {
    const Harness = defineComponent({
      setup() {
        state = useDeviceBatch()
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
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } }
    })
    deviceApi.batchCommands.mockResolvedValue({ code: 200, data: RESULT })
    deviceApi.batchEnable.mockResolvedValue({ code: 200, data: RESULT })
    deviceApi.batchDisable.mockResolvedValue({ code: 200, data: RESULT })
    deviceApi.batchAssignGroup.mockResolvedValue({ code: 200, data: RESULT })
    deviceApi.batchAssignTag.mockResolvedValue({ code: 200, data: RESULT })
    mountHarness()
  })

  afterEach(() => {
    wrapper?.unmount()
    queryClient.clear()
    vi.clearAllMocks()
  })

  it('单选切换维护选择集与计数', () => {
    expect(state.selectedCount.value).toBe(0)

    state.toggleSelect('d-1')
    state.toggleSelect('d-2')
    expect(state.selectedIds.value).toEqual(['d-1', 'd-2'])
    expect(state.selectedCount.value).toBe(2)
    expect(state.isSelected('d-1')).toBe(true)

    state.toggleSelect('d-1')
    expect(state.selectedIds.value).toEqual(['d-2'])
    expect(state.isSelected('d-1')).toBe(false)
  })

  it('本页全选保留跨页已选，取消全选只移除本页', () => {
    state.toggleSelect('other-page')
    state.toggleSelectAll([{ id: 'd-1' }, { id: 'd-2' }], true)

    expect(state.selectedIds.value.sort()).toEqual(['d-1', 'd-2', 'other-page'])

    state.toggleSelectAll([{ id: 'd-1' }, { id: 'd-2' }], false)
    expect(state.selectedIds.value).toEqual(['other-page'])
  })

  it('清空选择移除全部已选', () => {
    state.toggleSelect('d-1')
    state.toggleSelect('d-2')

    state.clearSelection()

    expect(state.selectedIds.value).toEqual([])
    expect(state.selectedCount.value).toBe(0)
  })

  it('批量下发命令解包结果并打开结果抽屉', async () => {
    const payload = { type: 'property_set', params: { temperature: 26 }, deviceIds: ['d-1', 'd-2'] }
    await state.sendBatchCommands(payload)
    await settle()

    expect(deviceApi.batchCommands).toHaveBeenCalledWith(payload)
    expect(state.result.value).toEqual(RESULT)
    expect(state.resultVisible.value).toBe(true)
  })

  it('批量启用 / 禁用把目标原样透传', async () => {
    await state.batchEnable({ groupIds: [3] })
    await state.batchDisable({ tagIds: [9] })
    await settle()

    expect(deviceApi.batchEnable).toHaveBeenCalledWith({ groupIds: [3] })
    expect(deviceApi.batchDisable).toHaveBeenCalledWith({ tagIds: [9] })
    expect(state.resultVisible.value).toBe(true)
  })

  it('批量分组 / 标签关联把 action 一并透传', async () => {
    await state.batchAssignGroup({ deviceIds: ['d-1'], groupId: 3, action: 'ADD' })
    await state.batchAssignTag({ deviceIds: ['d-1'], tagId: 9, action: 'REMOVE' })
    await settle()

    expect(deviceApi.batchAssignGroup).toHaveBeenCalledWith({ deviceIds: ['d-1'], groupId: 3, action: 'ADD' })
    expect(deviceApi.batchAssignTag).toHaveBeenCalledWith({ deviceIds: ['d-1'], tagId: 9, action: 'REMOVE' })
  })

  it('关闭结果抽屉仅收起可见状态，保留结果数据', async () => {
    await state.sendBatchCommands({ deviceIds: ['d-1'] })
    await settle()

    state.closeResult()

    expect(state.resultVisible.value).toBe(false)
    expect(state.result.value).toEqual(RESULT)
  })

  it('请求失败时不打开结果抽屉', async () => {
    deviceApi.batchCommands.mockRejectedValue(new Error('boom'))

    await expect(state.sendBatchCommands({ deviceIds: ['d-1'] })).rejects.toThrow('boom')

    expect(state.resultVisible.value).toBe(false)
  })
})