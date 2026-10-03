import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import DeviceLog from '../DeviceLog.vue'

// 共享替身：vi.mock 会被提升，用 vi.hoisted 保证工厂执行时函数已就绪。
const mocks = vi.hoisted(() => ({
  routerPush: vi.fn(),
  fetchDeviceById: vi.fn(),
  getDeviceLogs: vi.fn()
}))

/**
 * Element Plus 的命令式 API 由 unplugin-auto-import 注入，页面里以裸 `ElMessage` 调用，
 * 测试环境用替身拦截，避免真实 ElMessage 触发渲染。
 */
vi.mock('element-plus/es/components/message/index.mjs', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn()
  }
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '7' } }),
  useRouter: () => ({ push: mocks.routerPush })
}))

vi.mock('@/stores/device', () => ({
  useDeviceStore: () => ({ fetchDeviceById: mocks.fetchDeviceById })
}))

// useDeviceLog 内部依赖 deviceApi.getDeviceLogs，替换为替身以驱动真实组合式函数的翻页/筛选逻辑。
vi.mock('@/api/device', () => ({
  deviceApi: { getDeviceLogs: mocks.getDeviceLogs }
}))

const { ElMessage } = await import('element-plus/es/components/message/index.mjs')

const ElButtonStub = {
  name: 'ElButton',
  props: ['type', 'link', 'size', 'disabled', 'loading'],
  emits: ['click'],
  template:
    '<button type="button" class="stub-button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
}

const ElFormStub = { name: 'ElForm', template: '<form class="stub-form"><slot /></form>' }
const ElFormItemStub = {
  name: 'ElFormItem',
  props: ['label'],
  template: '<div class="stub-form-item"><slot /></div>'
}
const ElSelectStub = {
  name: 'ElSelect',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div class="stub-select"><slot /></div>'
}
const ElOptionStub = { name: 'ElOption', props: ['label', 'value'], template: '<span class="stub-option" />' }
const ElInputStub = {
  name: 'ElInput',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template:
    '<input class="stub-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
}
const ElDatePickerStub = {
  name: 'ElDatePicker',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div class="stub-date-picker" />'
}

const DeviceLogTimelineStub = {
  name: 'DeviceLogTimeline',
  props: {
    items: { type: Array, default: () => [] },
    total: { type: Number, default: 0 },
    loading: { type: Boolean, default: false },
    page: { type: Number, default: 1 },
    size: { type: Number, default: 20 }
  },
  emits: ['page-change'],
  template:
    '<div class="stub-timeline" :data-total="total" :data-count="items.length" :data-page="page">' +
    '<span v-for="item in items" :key="item.logId" class="stub-timeline__item">{{ item.logId }}</span>' +
    '<span v-if="!items.length" class="stub-timeline__empty">暂无日志</span>' +
    '<button type="button" class="stub-timeline__next" @click="$emit(\'page-change\', page + 1)">next</button>' +
    '</div>'
}

const PageHeaderStub = {
  name: 'PageHeader',
  props: ['title', 'desc'],
  template: '<div class="stub-page-header"><slot name="title" /><slot name="actions" /></div>'
}
const SvgIconStub = { name: 'SvgIcon', props: ['name', 'size'], template: '<span class="stub-icon" />' }

const STUBS = {
  ElButton: ElButtonStub,
  ElForm: ElFormStub,
  ElFormItem: ElFormItemStub,
  ElSelect: ElSelectStub,
  ElOption: ElOptionStub,
  ElInput: ElInputStub,
  ElDatePicker: ElDatePickerStub,
  DeviceLogTimeline: DeviceLogTimelineStub,
  PageHeader: PageHeaderStub,
  SvgIcon: SvgIconStub
}

/** 推进异步：请求 → 渲染 → 微任务，覆盖 vue-query 的异步链路。 */
async function settle() {
  await flushPromises()
  await nextTick()
  await flushPromises()
  await new Promise((resolve) => setTimeout(resolve, 0))
  await flushPromises()
}

/** 构造一个 getDeviceLogs 的 Result 响应体。 */
function logPage(records = [], total = 0) {
  return { code: 200, message: 'ok', data: { records, total } }
}

describe('views/workbench/device/DeviceLog', () => {
  let queryClient
  let wrapper

  function mountPage() {
    wrapper = mount(DeviceLog, {
      global: {
        plugins: [[VueQueryPlugin, { queryClient }]],
        stubs: STUBS,
        directives: { loading: {} }
      }
    })
    return wrapper
  }

  function buttonWith(text) {
    return wrapper.findAll('.stub-button').find((button) => button.text().includes(text))
  }

  /** 取最近一次 getDeviceLogs 的 (deviceId, params)。 */
  function lastCall() {
    return mocks.getDeviceLogs.mock.calls.at(-1)
  }

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: Infinity, gcTime: Infinity } }
    })
    mocks.routerPush.mockReset()
    mocks.fetchDeviceById.mockReset().mockResolvedValue({ deviceName: '温湿度传感器' })
    mocks.getDeviceLogs.mockReset().mockResolvedValue(logPage([{ logId: 'l-1' }], 1))
    ElMessage.warning.mockClear()
  })

  afterEach(() => {
    wrapper?.unmount()
    queryClient.clear()
    vi.clearAllMocks()
  })

  it('挂载时按路由设备拉取详情并展示设备名', async () => {
    mountPage()
    await settle()

    expect(mocks.fetchDeviceById).toHaveBeenCalledWith('7')
    expect(wrapper.text()).toContain('温湿度传感器')
  })

  it('默认按「近 24 小时」窗口查询并渲染记录数', async () => {
    mountPage()
    await settle()

    expect(mocks.getDeviceLogs).toHaveBeenCalledTimes(1)
    const [deviceId, params] = lastCall()
    expect(deviceId).toBe('7')
    expect(params.pageNum).toBe(1)
    expect(params.pageSize).toBe(20)
    expect(params.startTime).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
    expect(params.endTime).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
    // 未选类型 / 未填关键字时不拼接伪参数
    expect(params).not.toHaveProperty('types')
    expect(params).not.toHaveProperty('keyword')
    expect(wrapper.text()).toContain('共 1 条记录')
    expect(wrapper.find('.stub-timeline').attributes('data-count')).toBe('1')
  })

  it('无记录时展示空态与「共 0 条记录」', async () => {
    mocks.getDeviceLogs.mockResolvedValue(logPage([], 0))
    mountPage()
    await settle()

    expect(wrapper.find('.stub-timeline__empty').exists()).toBe(true)
    expect(wrapper.find('.stub-timeline').attributes('data-total')).toBe('0')
    expect(wrapper.find('.stub-timeline__item').exists()).toBe(false)
    expect(wrapper.text()).toContain('共 0 条记录')
  })

  it('点击查询按草稿条件重新拉取', async () => {
    mountPage()
    await settle()

    await wrapper.find('.stub-input').setValue('reboot')
    await buttonWith('查询').trigger('click')
    await settle()

    expect(mocks.getDeviceLogs).toHaveBeenCalledTimes(2)
    const [, params] = lastCall()
    expect(params.keyword).toBe('reboot')
    expect(params.pageNum).toBe(1)
  })

  it('翻页按新页码查询', async () => {
    mocks.getDeviceLogs.mockResolvedValue(logPage([{ logId: 'l-1' }], 45))
    mountPage()
    await settle()

    await wrapper.find('.stub-timeline__next').trigger('click')
    await settle()

    expect(mocks.getDeviceLogs).toHaveBeenCalledTimes(2)
    expect(lastCall()[1].pageNum).toBe(2)
    expect(wrapper.find('.stub-timeline').attributes('data-page')).toBe('2')
  })

  it('筛选变更后查询回到第 1 页', async () => {
    mocks.getDeviceLogs.mockResolvedValue(logPage([{ logId: 'l-1' }], 45))
    mountPage()
    await settle()

    await wrapper.find('.stub-timeline__next').trigger('click')
    await settle()
    expect(wrapper.find('.stub-timeline').attributes('data-page')).toBe('2')

    await wrapper.find('.stub-input').setValue('reboot')
    await buttonWith('查询').trigger('click')
    await settle()

    expect(lastCall()[1].pageNum).toBe(1)
    expect(lastCall()[1].keyword).toBe('reboot')
    expect(wrapper.find('.stub-timeline').attributes('data-page')).toBe('1')
  })

  it('时间跨度超过 31 天时提示并拦截查询', async () => {
    mountPage()
    await settle()
    const callsBefore = mocks.getDeviceLogs.mock.calls.length

    wrapper
      .findComponent(ElDatePickerStub)
      .vm.$emit('update:modelValue', ['2026-01-01 00:00:00', '2026-03-15 00:00:00'])
    await nextTick()

    await buttonWith('查询').trigger('click')
    await settle()

    expect(ElMessage.warning).toHaveBeenCalledWith('时间跨度不能超过 31 天，请缩小范围后再查询')
    expect(mocks.getDeviceLogs.mock.calls.length).toBe(callsBefore)
  })

  it('时间跨度在 31 天内时放行并按新窗口查询', async () => {
    mountPage()
    await settle()

    wrapper
      .findComponent(ElDatePickerStub)
      .vm.$emit('update:modelValue', ['2026-10-01 00:00:00', '2026-10-08 00:00:00'])
    await nextTick()

    await buttonWith('查询').trigger('click')
    await settle()

    const [, params] = lastCall()
    expect(params.startTime).toBe('2026-10-01 00:00:00')
    expect(params.endTime).toBe('2026-10-08 00:00:00')
    expect(ElMessage.warning).not.toHaveBeenCalled()
  })

  it('点击返回跳转设备详情', async () => {
    mountPage()
    await settle()

    await buttonWith('返回').trigger('click')

    expect(mocks.routerPush).toHaveBeenCalledWith('/workbench/devices/7')
  })
})
