import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'

vi.mock('@/api/deviceGroup', () => ({
  deviceGroupApi: {
    tree: vi.fn(),
    list: vi.fn(),
    create: vi.fn(),
    getDetail: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    pageDevices: vi.fn(),
    addDevices: vi.fn(),
    removeDevices: vi.fn()
  }
}))

vi.mock('@/api/deviceTag', () => ({
  deviceTagApi: {
    list: vi.fn(),
    create: vi.fn(),
    getDetail: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    pageDevices: vi.fn(),
    addDevices: vi.fn(),
    removeDevices: vi.fn()
  }
}))

import { deviceGroupApi } from '@/api/deviceGroup'
import { deviceTagApi } from '@/api/deviceTag'
import {
  useDeviceGroupTreeQuery,
  useDeviceGroupListQuery,
  useDeviceGroupMutations,
  useTagListQuery,
  useTagMutations
} from '../useDeviceGroups'

const TREE = [{ id: 1, name: '华东厂区', deviceCount: 2, children: [{ id: 2, name: '一号车间', deviceCount: 2 }] }]
const FLAT = [{ id: 1, name: '华东厂区' }, { id: 2, name: '一号车间' }]
const TAGS = [{ id: 9, name: '重点设备', color: '#f56c6c' }]

async function settle() {
  await flushPromises()
  await nextTick()
  await flushPromises()
}

describe('composables/useDeviceGroups', () => {
  let queryClient
  let state
  let wrapper

  function mountHarness(setup) {
    const Harness = defineComponent({
      setup() {
        state = setup()
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
    deviceGroupApi.tree.mockResolvedValue({ code: 200, data: TREE })
    deviceGroupApi.list.mockResolvedValue({ code: 200, data: FLAT })
    deviceGroupApi.create.mockResolvedValue({ code: 200, data: { id: 3 } })
    deviceGroupApi.update.mockResolvedValue({ code: 200, data: { id: 1 } })
    deviceGroupApi.delete.mockResolvedValue({ code: 200, data: null })
    deviceTagApi.list.mockResolvedValue({ code: 200, data: TAGS })
    deviceTagApi.create.mockResolvedValue({ code: 200, data: { id: 10 } })
    deviceTagApi.update.mockResolvedValue({ code: 200, data: { id: 9 } })
    deviceTagApi.delete.mockResolvedValue({ code: 200, data: null })
  })

  afterEach(() => {
    wrapper?.unmount()
    queryClient.clear()
    vi.clearAllMocks()
  })

  it('分组树解包 Result 信封并保留 children / deviceCount', async () => {
    mountHarness(useDeviceGroupTreeQuery)
    await settle()

    expect(deviceGroupApi.tree).toHaveBeenCalledTimes(1)
    expect(state.tree.value).toEqual(TREE)
    expect(state.tree.value[0].children[0].deviceCount).toBe(2)
  })

  it('分组平铺列表解包 Result 信封', async () => {
    mountHarness(useDeviceGroupListQuery)
    await settle()

    expect(deviceGroupApi.list).toHaveBeenCalledTimes(1)
    expect(state.groups.value).toEqual(FLAT)
  })

  it('data 为空时分组查询回退为空数组', async () => {
    deviceGroupApi.tree.mockResolvedValue({ code: 200, data: null })
    mountHarness(useDeviceGroupTreeQuery)
    await settle()

    expect(state.tree.value).toEqual([])
  })

  it('分组增删改后同时失效树与平铺列表缓存', async () => {
    mountHarness(() => ({ tree: useDeviceGroupTreeQuery(), list: useDeviceGroupListQuery(), mutations: useDeviceGroupMutations() }))
    await settle()
    expect(deviceGroupApi.tree).toHaveBeenCalledTimes(1)
    expect(deviceGroupApi.list).toHaveBeenCalledTimes(1)

    await state.mutations.createGroup({ name: '新分组', parentId: null })
    await settle()
    expect(deviceGroupApi.create).toHaveBeenCalledWith({ name: '新分组', parentId: null })
    expect(deviceGroupApi.tree).toHaveBeenCalledTimes(2)
    expect(deviceGroupApi.list).toHaveBeenCalledTimes(2)

    await state.mutations.updateGroup(1, { name: '改名', parentId: null })
    await settle()
    expect(deviceGroupApi.update).toHaveBeenCalledWith(1, { name: '改名', parentId: null })
    expect(deviceGroupApi.tree).toHaveBeenCalledTimes(3)
    expect(deviceGroupApi.list).toHaveBeenCalledTimes(3)

    await state.mutations.deleteGroup(1)
    await settle()
    expect(deviceGroupApi.delete).toHaveBeenCalledWith(1)
    expect(deviceGroupApi.tree).toHaveBeenCalledTimes(4)
    expect(deviceGroupApi.list).toHaveBeenCalledTimes(4)
  })

  it('标签列表解包 Result 信封', async () => {
    mountHarness(useTagListQuery)
    await settle()

    expect(deviceTagApi.list).toHaveBeenCalledTimes(1)
    expect(state.tags.value).toEqual(TAGS)
  })

  it('标签增删改后失效标签列表缓存', async () => {
    mountHarness(() => ({ tags: useTagListQuery(), mutations: useTagMutations() }))
    await settle()
    expect(deviceTagApi.list).toHaveBeenCalledTimes(1)

    await state.mutations.createTag({ name: '重点设备', color: '#f56c6c' })
    await settle()
    expect(deviceTagApi.create).toHaveBeenCalledWith({ name: '重点设备', color: '#f56c6c' })
    expect(deviceTagApi.list).toHaveBeenCalledTimes(2)

    await state.mutations.updateTag(9, { name: '关键设备', color: null })
    await settle()
    expect(deviceTagApi.update).toHaveBeenCalledWith(9, { name: '关键设备', color: null })
    expect(deviceTagApi.list).toHaveBeenCalledTimes(3)

    await state.mutations.deleteTag(9)
    await settle()
    expect(deviceTagApi.delete).toHaveBeenCalledWith(9)
    expect(deviceTagApi.list).toHaveBeenCalledTimes(4)
  })
})