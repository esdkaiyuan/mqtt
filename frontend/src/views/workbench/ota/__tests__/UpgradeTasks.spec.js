import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h, inject, provide, computed } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'

/** 共享替身：vi.mock 会被提升到文件顶部，用 vi.hoisted 保证工厂执行时替身已就绪。 */
const mocks = vi.hoisted(() => ({
  message: { success: vi.fn(), warning: vi.fn(), info: vi.fn(), error: vi.fn() },
  confirm: vi.fn(() => Promise.resolve()),
  routerPush: vi.fn(),
  listTasks: vi.fn(),
  listFirmwares: vi.fn(),
  createTask: vi.fn(),
  retryTask: vi.fn(),
  removeTask: vi.fn(),
  getDevices: vi.fn()
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
  useRouter: () => ({ push: mocks.routerPush })
}))

vi.mock('@/api/ota', () => ({
  otaApi: {
    listTasks: mocks.listTasks,
    listFirmwares: mocks.listFirmwares,
    createTask: mocks.createTask,
    retryTask: mocks.retryTask,
    removeTask: mocks.removeTask
  }
}))

vi.mock('@/api/device', () => ({
  deviceApi: { getList: mocks.getDevices }
}))

/** 分组 / 标签选项与「新建任务」无关，返回空集合即可。 */
vi.mock('@/composables/useDeviceGroups', async () => {
  const { ref } = await import('vue')
  return {
    useDeviceGroupTreeQuery: () => ({ tree: ref([]), loading: ref(false), refresh: vi.fn() }),
    useTagListQuery: () => ({ tags: ref([]), loading: ref(false), refresh: vi.fn() })
  }
})

import UpgradeTasks from '../UpgradeTasks.vue'

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
  props: ['disabled', 'loading'],
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

const ElRadioGroupStub = {
  name: 'ElRadioGroup',
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue'],
  setup(_, { slots }) {
    return () => h('div', { class: 'stub-radio-group' }, slots.default?.())
  }
}

const ElInputStub = {
  name: 'ElInput',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('input', {
        class: 'stub-input',
        value: props.modelValue,
        onInput: (event) => emit('update:modelValue', event.target.value)
      })
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

const ElDialogStub = {
  name: 'ElDialog',
  props: ['modelValue', 'title'],
  emits: ['update:modelValue'],
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('div', { class: 'stub-dialog' }, [
            h('div', { class: 'stub-dialog-title' }, props.title),
            slots.default?.(),
            slots.footer?.()
          ])
        : null
  }
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
  template: '<div class="stub-empty">{{ description }}</div>'
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
  ElRadioGroup: ElRadioGroupStub,
  ElRadioButton: passthrough('ElRadioButton'),
  ElTreeSelect: passthrough('ElTreeSelect'),
  ElInput: ElInputStub,
  ElProgress: ElProgressStub,
  ElTag: ElTagStub,
  ElDialog: ElDialogStub,
  ElForm: passthrough('ElForm'),
  ElFormItem: passthrough('ElFormItem'),
  ElPagination: passthrough('ElPagination'),
  EmptyState: EmptyStateStub,
  PageHeader: PageHeaderStub,
  SvgIcon: passthrough('SvgIcon')
}

const FIRMWARE = {
  id: 11,
  productId: 3,
  productName: '温湿度传感器',
  version: '1.0.0'
}

const PARTIAL_TASK = {
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
  createdAt: '2026-10-01T08:00:00.000'
}

const RUNNING_TASK = { ...PARTIAL_TASK, id: 22, name: '灰度升级', status: 'RUNNING' }
const SUCCESS_TASK = {
  ...PARTIAL_TASK,
  id: 23,
  name: '历史升级',
  status: 'SUCCESS',
  successCount: 4,
  failedCount: 0
}

async function settle() {
  await flushPromises()
  await flushPromises()
}

describe('views/workbench/ota/UpgradeTasks', () => {
  let queryClient
  let wrapper

  function mountPage() {
    wrapper = mount(UpgradeTasks, {
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

  function buttonsByText(text) {
    return wrapper.findAll('button').filter((button) => button.text().trim() === text)
  }

  /** 对话框底部按钮：避免与页头同名按钮（如「新建任务」）混淆。 */
  function dialogButton(text) {
    return wrapper
      .find('.stub-dialog')
      .findAll('button')
      .find((button) => button.text().trim() === text)
  }

  async function openDialog() {
    await buttonByText('新建任务').trigger('click')
    await flushPromises()
  }

  function chooseFirmware(firmwareId) {
    wrapper.findAllComponents({ name: 'ElSelect' })[0].vm.$emit('update:modelValue', firmwareId)
  }

  function chooseTargetMode(mode) {
    wrapper.findAllComponents({ name: 'ElRadioGroup' })[0].vm.$emit('update:modelValue', mode)
  }

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: Infinity, gcTime: Infinity } }
    })
    mocks.message.success.mockClear()
    mocks.message.warning.mockClear()
    mocks.confirm.mockReset().mockResolvedValue(undefined)
    mocks.routerPush.mockReset()
    mocks.listTasks
      .mockReset()
      .mockResolvedValue({ code: 200, message: 'ok', data: { records: [PARTIAL_TASK], total: 1 } })
    mocks.listFirmwares.mockReset().mockResolvedValue({ code: 200, message: 'ok', data: [FIRMWARE] })
    mocks.getDevices.mockReset().mockResolvedValue({ code: 200, message: 'ok', data: { records: [] } })
    mocks.createTask.mockReset().mockResolvedValue({ code: 200, message: 'ok', data: { id: 31 } })
    mocks.retryTask.mockReset().mockResolvedValue({ code: 200, message: 'ok', data: null })
    mocks.removeTask.mockReset().mockResolvedValue({ code: 200, message: 'ok', data: null })
  })

  afterEach(() => {
    wrapper?.unmount()
    queryClient.clear()
    vi.clearAllMocks()
  })

  it('无任务时渲染空态提示', async () => {
    mocks.listTasks.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    mountPage()
    await settle()

    expect(wrapper.find('.stub-empty').exists()).toBe(true)
    expect(wrapper.find('.stub-empty').text()).toContain('暂无升级任务')
  })

  it('渲染任务行并计算进度与状态文案', async () => {
    mountPage()
    await settle()

    const text = wrapper.text()
    expect(text).toContain('1.0.0 全量升级')
    expect(text).toContain('温湿度传感器')
    expect(text).toContain('成功 3 / 共 4')
    expect(text).toContain('部分成功')

    // 3 / 4 → 75%，PARTIAL → warning 配色
    const progress = wrapper.find('.stub-progress')
    expect(progress.attributes('data-percentage')).toBe('75')
    expect(progress.attributes('data-status')).toBe('warning')
  })

  it('点击「详情」跳转到任务详情页', async () => {
    mountPage()
    await settle()

    await buttonByText('详情').trigger('click')

    expect(mocks.routerPush).toHaveBeenCalledWith({
      name: 'UpgradeTaskDetail',
      params: { id: 21 }
    })
  })

  it('运行中的任务禁用删除、已完成的任务禁用重投', async () => {
    mocks.listTasks.mockResolvedValue({
      code: 200,
      data: { records: [RUNNING_TASK, SUCCESS_TASK], total: 2 }
    })
    mountPage()
    await settle()

    const retryButtons = buttonsByText('重投')
    const removeButtons = buttonsByText('删除')

    expect(retryButtons[0].attributes('disabled')).toBeUndefined()
    expect(retryButtons[1].attributes('disabled')).toBeDefined()
    expect(removeButtons[0].attributes('disabled')).toBeDefined()
    expect(removeButtons[1].attributes('disabled')).toBeUndefined()
  })

  it('点击「新建任务」打开新建对话框', async () => {
    mountPage()
    await settle()
    expect(wrapper.find('.stub-dialog').exists()).toBe(false)

    await openDialog()

    expect(wrapper.find('.stub-dialog-title').text()).toBe('新建升级任务')
  })

  it('填写完整后提交产品维度目标并提示成功', async () => {
    mountPage()
    await settle()
    await openDialog()

    await wrapper.find('input.stub-input').setValue('1.0.0 全量升级')
    chooseFirmware(11)
    await flushPromises()

    await dialogButton('创建').trigger('click')
    await settle()

    expect(mocks.createTask).toHaveBeenCalledTimes(1)
    expect(mocks.createTask).toHaveBeenCalledWith({
      name: '1.0.0 全量升级',
      firmwareId: 11,
      target: { productIds: [3] }
    })
    expect(mocks.message.success).toHaveBeenCalledWith('升级任务已创建')
    expect(wrapper.find('.stub-dialog').exists()).toBe(false)
  })

  it('任务名称为空时前端拦截并提示', async () => {
    mountPage()
    await settle()
    await openDialog()

    chooseFirmware(11)
    await dialogButton('创建').trigger('click')
    await settle()

    expect(mocks.message.warning).toHaveBeenCalledWith('请填写任务名称')
    expect(mocks.createTask).not.toHaveBeenCalled()
  })

  it('任务名称超过 64 字符时前端拦截并提示', async () => {
    mountPage()
    await settle()
    await openDialog()

    await wrapper.find('input.stub-input').setValue('a'.repeat(65))
    await dialogButton('创建').trigger('click')
    await settle()

    expect(mocks.message.warning).toHaveBeenCalledWith('任务名称不能超过 64 个字符')
    expect(mocks.createTask).not.toHaveBeenCalled()
  })

  it('未选择固件包时前端拦截并提示', async () => {
    mountPage()
    await settle()
    await openDialog()

    await wrapper.find('input.stub-input').setValue('1.0.0 全量升级')
    await dialogButton('创建').trigger('click')
    await settle()

    expect(mocks.message.warning).toHaveBeenCalledWith('请选择固件包')
    expect(mocks.createTask).not.toHaveBeenCalled()
  })

  it('目标范围为空时前端拦截并提示', async () => {
    mountPage()
    await settle()
    await openDialog()

    await wrapper.find('input.stub-input').setValue('1.0.0 全量升级')
    chooseFirmware(11)
    chooseTargetMode('group')
    await flushPromises()

    await dialogButton('创建').trigger('click')
    await settle()

    expect(mocks.message.warning).toHaveBeenCalledWith('请至少选择一个升级目标')
    expect(mocks.createTask).not.toHaveBeenCalled()
  })

  it('重投需二次确认后调用接口', async () => {
    mountPage()
    await settle()

    await buttonByText('重投').trigger('click')
    await settle()

    expect(mocks.confirm).toHaveBeenCalledTimes(1)
    expect(mocks.retryTask).toHaveBeenCalledWith(21)
    expect(mocks.message.success).toHaveBeenCalledWith('已触发重投')
  })

  it('删除任务需二次确认后调用接口', async () => {
    mountPage()
    await settle()

    await buttonByText('删除').trigger('click')
    await settle()

    expect(mocks.confirm).toHaveBeenCalledTimes(1)
    expect(mocks.removeTask).toHaveBeenCalledWith(21)
    expect(mocks.message.success).toHaveBeenCalledWith('任务已删除')
  })

  it('取消删除时不调用接口', async () => {
    mocks.confirm.mockRejectedValueOnce(new Error('cancel'))
    mountPage()
    await settle()

    await buttonByText('删除').trigger('click')
    await settle()

    expect(mocks.removeTask).not.toHaveBeenCalled()
  })
})
