import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import DevicePropertyTrend from '../DevicePropertyTrend.vue'

const mocks = vi.hoisted(() => ({
  getThingModel: vi.fn(),
  queryHistory: vi.fn()
}))

vi.mock('@/api/product', () => ({ productApi: { getThingModel: mocks.getThingModel } }))
vi.mock('@/api/propertyHistory', () => ({
  propertyHistoryApi: { query: mocks.queryHistory }
}))

/** 让查询走完 microtask 链并触发 Vue 的响应式更新。 */
async function settle() {
  await flushPromises()
  await nextTick()
  await flushPromises()
  await new Promise((resolve) => setTimeout(resolve, 0))
  await flushPromises()
}

const ElSelectStub = {
  name: 'ElSelect',
  props: ['modelValue', 'placeholder', 'loading', 'disabled'],
  emits: ['update:modelValue', 'change'],
  template: '<div class="el-select-stub"><slot /></div>'
}

const ElOptionStub = {
  name: 'ElOption',
  props: ['label', 'value'],
  template: '<div class="el-option-stub" :data-value="value">{{ label }}</div>'
}

const PropertyTrendChartStub = {
  name: 'PropertyTrendChart',
  props: ['title', 'series', 'deviceNames', 'aggregation'],
  template: '<div class="trend-chart-stub"></div>'
}

const EmptyStateStub = {
  name: 'EmptyState',
  props: ['description', 'imageSize'],
  template: '<div class="empty-stub">{{ description }}</div>'
}

const STUBS = {
  ElSelect: ElSelectStub,
  ElOption: ElOptionStub,
  ElButton: { name: 'ElButton', template: '<button class="el-button-stub"><slot /></button>' },
  PropertyTrendChart: PropertyTrendChartStub,
  EmptyState: EmptyStateStub
}

const THING_MODEL = {
  schemaVersion: '1.0',
  properties: [
    { identifier: 'temperature', name: '温度', dataType: { type: 'float' } },
    { identifier: 'status', name: '', dataType: { type: 'enum' } }
  ],
  events: [],
  services: []
}

describe('components/device/DevicePropertyTrend', () => {
  let queryClient
  let wrapper

  function mountTrend(props = {}) {
    wrapper = mount(DevicePropertyTrend, {
      props: { deviceId: 1, deviceName: '车间温度计', productId: 5, ...props },
      global: {
        plugins: [[VueQueryPlugin, { queryClient }]],
        stubs: STUBS,
        directives: { loading: {} }
      }
    })
    return wrapper
  }

  function lastQueryParams() {
    return mocks.queryHistory.mock.calls.at(-1)?.[0]
  }

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: Infinity, gcTime: Infinity } }
    })
    mocks.getThingModel.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { thingModel: THING_MODEL }
    })
    mocks.queryHistory.mockResolvedValue({ code: 200, message: 'ok', data: [] })
  })

  afterEach(() => {
    wrapper?.unmount()
    queryClient.clear()
    vi.clearAllMocks()
  })

  it('属性下拉来自物模型，并默认选中首个属性发起查询', async () => {
    mountTrend()
    await settle()

    expect(mocks.getThingModel).toHaveBeenCalledWith(5)
    const labels = wrapper.findAll('.el-option-stub').map((node) => node.text())
    // 有 name 的拼「名称（标识符）」，无 name 的只显示标识符
    expect(labels).toContain('温度（temperature）')
    expect(labels).toContain('status')

    expect(lastQueryParams()).toMatchObject({
      deviceIds: '1',
      identifiers: 'temperature',
      bucket: '5m'
    })
    expect(lastQueryParams().startTime).toBeTruthy()
    expect(lastQueryParams().endTime).toBeTruthy()
  })

  it('数值型属性按 avg 聚合传给图表', async () => {
    mountTrend()
    await settle()

    expect(wrapper.findComponent(PropertyTrendChartStub).props('aggregation')).toBe('avg')
  })

  it('非数值型属性改为 count 聚合', async () => {
    mountTrend()
    await settle()

    const propertySelect = wrapper.findAllComponents(ElSelectStub)[0]
    propertySelect.vm.$emit('update:modelValue', 'status')
    await settle()

    expect(mocks.queryHistory).toHaveBeenLastCalledWith(
      expect.objectContaining({ identifiers: 'status' })
    )
    expect(wrapper.findComponent(PropertyTrendChartStub).props('aggregation')).toBe('count')
  })

  it('粒度变更触发重新查询并带上新桶宽', async () => {
    mountTrend()
    await settle()
    const before = mocks.queryHistory.mock.calls.length

    const bucketSelect = wrapper.findAllComponents(ElSelectStub)[1]
    bucketSelect.vm.$emit('update:modelValue', '15m')
    bucketSelect.vm.$emit('change')
    await settle()

    expect(mocks.queryHistory.mock.calls.length).toBeGreaterThan(before)
    expect(lastQueryParams().bucket).toBe('15m')
  })

  it('快捷时间范围按钮触发重新查询', async () => {
    mountTrend()
    await settle()
    const before = mocks.queryHistory.mock.calls.length

    const presetButtons = wrapper.findAll('.el-button-stub')
    // 第一个快捷项为「近 1 小时」
    await presetButtons[0].trigger('click')
    await settle()

    expect(mocks.queryHistory.mock.calls.length).toBeGreaterThan(before)
  })

  it('物模型未定义属性时展示空态且不渲染图表', async () => {
    mocks.getThingModel.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { thingModel: { schemaVersion: '1.0', properties: [], events: [], services: [] } }
    })

    mountTrend()
    await settle()

    expect(wrapper.find('.empty-stub').exists()).toBe(true)
    expect(wrapper.findComponent(PropertyTrendChartStub).exists()).toBe(false)
    expect(mocks.queryHistory).not.toHaveBeenCalled()
  })

  it('未提供 productId 时不查询物模型', async () => {
    mountTrend({ productId: null })
    await settle()

    expect(mocks.getThingModel).not.toHaveBeenCalled()
  })
})
