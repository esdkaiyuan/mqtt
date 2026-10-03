import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h, inject, provide, computed } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'

/** 共享替身：vi.mock 会被提升到文件顶部，用 vi.hoisted 保证工厂执行时替身已就绪。 */
const mocks = vi.hoisted(() => ({
  message: { success: vi.fn(), warning: vi.fn(), info: vi.fn(), error: vi.fn() },
  confirm: vi.fn(() => Promise.resolve()),
  routerPush: vi.fn(),
  taskDetail: vi.fn(),
  taskRecords: vi.fn(),
  retryTask: vi.fn(),
  getProducts: vi.fn()
}))

/** 页面显式 `import { ElMessage, ElMessageBox } from 'element-plus'`，直接对根模块打桩。 */
vi.mock('element-plus', () => ({
  ElMessage: mocks.message,
  ElMessageBox: { confirm: mocks.confirm }
}))

/** 写权限由角色决定，测试统一放行。 */
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ hasRole: () => true })
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '21' } }),
  useRouter: () => ({ push: mocks.routerPush })
}))

vi.mock('@/api/ota', () => ({
  otaApi: {
    taskDetail: mocks.taskDetail,
    taskRecords: mocks.taskRecords,
    retryTask: mocks.retryTask
  }
}))

vi.mock('@/api/product', () => ({
  productApi: { getList: mocks.getProducts }
}))

/** 目标快照需要按 id 回填产品 / 分组 / 标签名称。 */
vi.mock('@/composables/useDeviceGroups', async () => {
  const { ref } = await import('vue')
  return {
    useDeviceGroupTreeQuery: () => ({
      tree: ref([{ id: 5, name: '华东厂区', children: [{ id: 6, name: '一号车间', children: [] }] }]),
      loading: ref(false),
      refresh: vi.fn()
    }),
    useTagListQuery: () => ({ tags: ref([{ id: 7, name: '试点' }]), loading: ref(false), refresh: vi.fn() })
  }
})

import UpgradeTaskDetail from '../UpgradeTaskDetail.vue'

/* ---------- Element Plus 最小替身 ---------- */
const ROW_KEY = Symbol('table-row')

const RowProvider = {
  name: 'RowProvider',
  props: ['row'],
  setup(props, { slots }) {
    provide(ROW_KEY, computed(() => props.row))
    return () => slots.default?.()
  }
}

const ElTableStub = {
  name: 'ElTable',
  props: ['data'],
  setup(props, { slots }) {
    return () => {
      if (!props.data || props.data.length === 0) {
        return h('div', { class: 'stub-table' }, slots.empty ? [slots.empty()] : [])
      }
      return h(
        'div',
        { class: 'stub-table' },
        props.data.map((row, index) => h(RowProvider, { row, key: index }, { default: () => slots.default?.() }))
      )
    }
  }
}

const ElTableColumnStub = {
  name: 'ElTableColumn',
  props: ['label', 'prop'],
  setup(props, { slots }) {
    const row = inject(ROW_KEY, null)
    return () => {
      if (slots.default) return h('div', { class: 'stub-cell' }, slots.default({ row: row?.value ?? null }))
      return h('div', { class: 'stub-cell' }, props.prop ? String(row?.value?.[props.prop] ?? '') : '')
    }
  }
}

const ElButtonStub = {
  name: 'ElButton',
  props: ['disabled', 'loading', 'size'],
  emits: ['click'],
  setup(props, { slots, emit }) {
    return () =>
      h('button', { class: 'stub-btn', disabled: props.disabled, onClick: () => emit('click') }, slots.default?.())
  }
}

/** v-model 驱动：测试通过 `$emit('update:modelValue', v)` 设置选中值。 */
const ElSelectStub = {
  name: 'ElSelect',
  props: ['modelValue'],
  emits: ['update:modelValue', 'change'],
  setup(_, { slots }) {
    return () => h('div', { class: 'stub-select' }, slots.default?.())
  }
}

/** 进度条暴露 percentage / status，便于断言计算与配色。 */
const ElProgressStub = {
  name: 'ElProgress',
  props: ['percentage', 'status', 'strokeWidth', 'showText'],
  setup(props) {
    return () =>
      h('div', {
        class: 'stub-progress',
        'data-percentage': String(props.percentage),
        'data-status': props.status
      })
  }
}

const ElTagStub = {
  name: 'ElTag',
  props: ['type'],
  template: '<span class="stub-tag" :data-type="type"><slot /></span>'
}

const passthrough = (name) => ({
  name,
  setup(_, { slots }) {
    return () => h('div', { class: `stub-${name}` }, slots.default?.())
  }
})

const EmptyStateStub = {
  name: 'EmptyState',
  props: ['description', 'imageSize'],
  template: '<div class="stub-empty">{{ description }}<slot /></div>'
}

const PageHeaderStub = {
  name: 'PageHeader',
  props: ['title', 'desc'],
  template:
    '<div class="stub-page-header"><span class="stub-page-header__title">{{ title }}</span>' +
    '<span class="stub-page-header__desc">{{ desc }}</span>' +
    '<slot name="title" /><slot name="actions" /></div>'
}

const STUBS = {
  ElTable: ElTableStub,
  ElTableColumn: ElTableColumnStub,
  ElButton: ElButtonStub,
  ElSelect: ElSelectStub,
  ElOption: passthrough('ElOption'),
  ElProgress: ElProgressStub,
  ElTag: ElTagStub,
  ElPagination: passthrough('ElPagination'),
  EmptyState: EmptyStateStub,
  PageHeader: PageHeaderStub,
  SvgIcon: passthrough('SvgIcon')
}

const DETAIL = {
  id: 21,
  name: '1.0.0 全量升级',
  firmwareId: 11,
  version: '1.0.0',
  productId: 3,
  productName: '温湿度传感器',
  totalCount: 4,
  dispatchedCount: 4,
  successCount: 3,
  failedCount: 1,
  status: 'PARTIAL',
  createdAt: '2026-10-01T08:00:00.000',
  target: { productIds: [3], groupIds: [5], tagIds: [7], deviceIds: [] }
}

const RECORDS = [
  {
    deviceId: 1,
    deviceName: '传感器-01',
    deviceKey: 'dev-001',
    version: '1.0.0',
    status: 'FLASHING',
    progress: 60,
    message: '',
    commandId: 'cmd-1',
    dispatchedAt: '2026-10-01T08:10:00.000',
    lastReportAt: '2026-10-01T09:00:00.000'
  },
  {
    deviceId: 2,
    deviceName: '传感器-02',
    deviceKey: 'dev-002',
    version: '1.0.0',
    status: 'SUCCESS',
    progress: 100,
    message: '',
    commandId: 'cmd-2',
    dispatchedAt: '2026-10-01T08:10:00.000',
    lastReportAt: '2026-10-01T09:05:00.000'
  }
]

async function settle() {
  await flushPromises()
  await flushPromises()
}

describe('views/workbench/ota/UpgradeTaskDetail', () => {
  let queryClient
  let wrapper

  function mountPage() {
    wrapper = mount(UpgradeTaskDetail, {
      global: {
        plugins: [[VueQueryPlugin, { queryClient }]],
        stubs: STUBS,
        directives: { loading: {} }
      }
    })
    return wrapper
  }

  function buttonByText(text) {
    return wrapper.findAll('button').find((button) => button.text().includes(text))
  }

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: Infinity, gcTime: Infinity } }
    })
    mocks.message.success.mockClear()
    mocks.message.warning.mockClear()
    mocks.confirm.mockReset().mockResolvedValue(undefined)
    mocks.routerPush.mockReset()
    mocks.taskDetail.mockReset().mockResolvedValue({ code: 200, message: 'ok', data: DETAIL })
    mocks.taskRecords
      .mockReset()
      .mockResolvedValue({ code: 200, message: 'ok', data: { records: RECORDS, total: 2 } })
    mocks.getProducts
      .mockReset()
      .mockResolvedValue({ code: 200, message: 'ok', data: [{ id: 3, productName: '温湿度传感器' }] })
    mocks.retryTask.mockReset().mockResolvedValue({ code: 200, message: 'ok', data: null })
  })

  afterEach(() => {
    wrapper?.unmount()
    queryClient.clear()
    vi.clearAllMocks()
  })

  it('载荷失败时渲染错误空态与重试按钮', async () => {
    mocks.taskDetail.mockRejectedValue(new Error('boom'))
    mountPage()
    await settle()

    expect(wrapper.find('.stub-empty').exists()).toBe(true)
    expect(wrapper.find('.stub-empty').text()).toContain('任务加载失败，请稍后重试')
    expect(buttonByText('重试')).toBeTruthy()
  })

  it('渲染计数卡片、元信息与页头描述', async () => {
    mountPage()
    await settle()

    const text = wrapper.text()
    expect(text).toContain('1.0.0 全量升级')
    expect(text).toContain('目标总数')
    expect(text).toContain('已下发')
    expect(text).toContain('部分成功')

    const desc = wrapper.find('.stub-page-header__desc').text()
    expect(desc).toContain('固件 1.0.0')
    expect(desc).toContain('产品 温湿度传感器')
  })

  it('按 id 回填目标快照的名称', async () => {
    mountPage()
    await settle()

    const snapshot = wrapper.find('.target-snapshot').text()
    expect(snapshot).toContain('产品')
    expect(snapshot).toContain('温湿度传感器')
    expect(snapshot).toContain('华东厂区')
    expect(snapshot).toContain('试点')
  })

  it('渲染逐台记录并展示进度与状态文案', async () => {
    mountPage()
    await settle()

    const text = wrapper.text()
    expect(text).toContain('传感器-01')
    expect(text).toContain('dev-001')
    expect(text).toContain('刷写中')
    expect(text).toContain('成功')

    const progressBars = wrapper.findAll('.stub-progress')
    expect(progressBars[0].attributes('data-percentage')).toBe('60')
    expect(progressBars[1].attributes('data-percentage')).toBe('100')
  })

  it('无记录时渲染记录表空态', async () => {
    mocks.taskRecords.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    mountPage()
    await settle()

    expect(wrapper.find('.stub-empty').text()).toContain('暂无升级记录')
  })

  it('切换记录状态后按 status 重新查询', async () => {
    mountPage()
    await settle()
    expect(mocks.taskRecords).toHaveBeenLastCalledWith('21', { status: undefined, page: 1, size: 10 })

    wrapper.findAllComponents({ name: 'ElSelect' })[0].vm.$emit('update:modelValue', 'FAILED')
    await settle()

    expect(mocks.taskRecords).toHaveBeenLastCalledWith('21', { status: 'FAILED', page: 1, size: 10 })
  })

  it('重投需二次确认后调用接口', async () => {
    mountPage()
    await settle()

    await buttonByText('重投未成功').trigger('click')
    await settle()

    expect(mocks.confirm).toHaveBeenCalledTimes(1)
    expect(mocks.retryTask).toHaveBeenCalledWith('21')
    expect(mocks.message.success).toHaveBeenCalledWith('已触发重投')
  })

  it('取消重投时不调用接口', async () => {
    mocks.confirm.mockRejectedValueOnce(new Error('cancel'))
    mountPage()
    await settle()

    await buttonByText('重投未成功').trigger('click')
    await settle()

    expect(mocks.retryTask).not.toHaveBeenCalled()
  })

  it('已成功任务禁用重投按钮', async () => {
    mocks.taskDetail.mockResolvedValue({
      code: 200,
      data: { ...DETAIL, status: 'SUCCESS', successCount: 4, failedCount: 0 }
    })
    mountPage()
    await settle()

    expect(buttonByText('重投未成功').attributes('disabled')).toBeDefined()
  })

  it('点击「返回」回到任务列表', async () => {
    mountPage()
    await settle()

    await buttonByText('返回').trigger('click')

    expect(mocks.routerPush).toHaveBeenCalledWith({ name: 'UpgradeTasks' })
  })
})
