import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, h, inject, provide } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

const { messageMock, confirmMock } = vi.hoisted(() => ({
  messageMock: { success: vi.fn(), warning: vi.fn(), info: vi.fn(), error: vi.fn() },
  confirmMock: vi.fn(() => Promise.resolve())
}))

vi.mock('element-plus', () => ({
  ElMessage: messageMock,
  ElMessageBox: { confirm: confirmMock }
}))

/** 场景列表 / 执行记录以可写容器暴露，便于逐用例设置数据与断言调用。 */
const hooks = vi.hoisted(() => ({
  filters: null,
  scenes: null,
  total: null,
  loading: null,
  setFilter: null,
  resetFilters: null,
  refresh: null,
  setEnabled: null,
  deleteScene: null,
  runScene: null,
  executionFilters: null,
  executions: null,
  executionTotal: null,
  executionLoading: null,
  hasActive: null,
  setExecutionFilter: null,
  resetExecutionFilters: null,
  refreshExecutions: null,
  retryExecution: null
}))

vi.mock('@/composables/useScenes', async () => {
  const { reactive, ref } = await import('vue')
  hooks.filters = reactive({ triggerType: '', enabled: '', keyword: '' })
  hooks.scenes = ref([])
  hooks.total = ref(0)
  hooks.loading = ref(false)
  hooks.setFilter = vi.fn()
  hooks.resetFilters = vi.fn()
  hooks.goToPage = vi.fn()
  hooks.changePageSize = vi.fn()
  hooks.refresh = vi.fn()
  hooks.setEnabled = vi.fn(() => Promise.resolve())
  hooks.deleteScene = vi.fn(() => Promise.resolve())
  hooks.runScene = vi.fn(() => Promise.resolve())
  return {
    useSceneListQuery: () => ({
      filters: hooks.filters,
      currentPage: ref(1),
      pageSize: ref(20),
      scenes: hooks.scenes,
      total: hooks.total,
      loading: hooks.loading,
      setFilter: hooks.setFilter,
      resetFilters: hooks.resetFilters,
      goToPage: hooks.goToPage,
      changePageSize: hooks.changePageSize,
      refresh: hooks.refresh
    }),
    useSceneMutations: () => ({
      setEnabled: hooks.setEnabled,
      deleteScene: hooks.deleteScene
    }),
    useSceneRunMutation: () => ({
      runScene: hooks.runScene
    })
  }
})

vi.mock('@/composables/useSceneExecutions', async () => {
  const { reactive, ref } = await import('vue')
  hooks.executionFilters = reactive({ sceneId: null, deviceId: null, status: '' })
  hooks.executions = ref([])
  hooks.executionTotal = ref(0)
  hooks.executionLoading = ref(false)
  hooks.hasActive = ref(false)
  hooks.setExecutionFilter = vi.fn()
  hooks.resetExecutionFilters = vi.fn()
  hooks.goToExecutionPage = vi.fn()
  hooks.changeExecutionPageSize = vi.fn()
  hooks.refreshExecutions = vi.fn()
  hooks.retryExecution = vi.fn(() => Promise.resolve())
  return {
    useSceneExecutionsQuery: () => ({
      filters: hooks.executionFilters,
      currentPage: ref(1),
      pageSize: ref(20),
      executions: hooks.executions,
      total: hooks.executionTotal,
      loading: hooks.executionLoading,
      hasActive: hooks.hasActive,
      setFilter: hooks.setExecutionFilter,
      resetFilters: hooks.resetExecutionFilters,
      goToPage: hooks.goToExecutionPage,
      changePageSize: hooks.changeExecutionPageSize,
      refresh: hooks.refreshExecutions
    }),
    useSceneExecutionRetryMutation: () => ({
      retryExecution: hooks.retryExecution
    })
  }
})

vi.mock('@/api/device', () => ({
  deviceApi: {
    getList: vi.fn(() =>
      Promise.resolve({ code: 200, data: { records: [{ id: 9, deviceName: '1号温控器', deviceKey: 'dev-9' }] } })
    )
  }
}))

vi.mock('@/api/scene', () => ({
  sceneApi: {
    listScenes: vi.fn(() =>
      Promise.resolve({ code: 200, data: { records: [{ id: 1, name: '高温联动' }], total: 1 } })
    )
  }
}))

import SceneCenter from '../SceneCenter.vue'

const SCENE_PROPERTY = {
  id: 1,
  name: '高温联动',
  triggerType: 'PROPERTY',
  triggerIdentifier: 'temperature',
  triggerOperator: 'GT',
  triggerThreshold: '40',
  conditions: [{ identifier: 'humidity' }, { identifier: 'smoke' }],
  cooldownSeconds: 300,
  lastTriggeredAt: '2026-05-01T10:00:00',
  enabled: 1
}

const SCENE_TIMER = {
  id: 2,
  name: '晨间巡检',
  triggerType: 'TIMER',
  timerCron: '0 8 * * *',
  conditions: [],
  cooldownSeconds: 0,
  lastTriggeredAt: null,
  enabled: 0
}

const EXECUTION_FAILED = {
  id: 11,
  sceneName: '高温联动',
  triggerType: 'PROPERTY',
  triggerSource: 'AUTO',
  triggerDeviceName: '1号温控器',
  triggerIdentifier: 'temperature',
  triggerValue: '45',
  finishedSteps: 1,
  totalSteps: 2,
  status: 'FAILED',
  createdAt: '2026-05-01T10:00:05'
}

const EXECUTION_SUCCESS = {
  id: 12,
  sceneName: '晨间巡检',
  triggerType: 'TIMER',
  triggerSource: 'MANUAL',
  triggerDeviceName: null,
  triggerDeviceKey: 'dev-2',
  triggerIdentifier: null,
  triggerValue: null,
  finishedSteps: 3,
  totalSteps: 3,
  status: 'SUCCESS',
  createdAt: '2026-05-01T08:00:00'
}

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
  emits: ['click'],
  setup(_, { slots, emit }) {
    return () => h('button', { class: 'stub-btn', onClick: () => emit('click') }, slots.default?.())
  }
}

const ElSwitchStub = {
  name: 'ElSwitch',
  props: ['modelValue'],
  emits: ['change'],
  setup(props, { emit }) {
    return () =>
      h('button', { class: 'stub-switch', onClick: () => emit('change', !props.modelValue) }, String(props.modelValue))
  }
}

const ElSelectStub = {
  name: 'ElSelect',
  props: ['modelValue', 'placeholder', 'clearable', 'filterable', 'disabled', 'multiple'],
  emits: ['update:modelValue', 'change'],
  template: '<div class="stub-select"><slot /></div>'
}

const ElOptionStub = {
  name: 'ElOption',
  props: ['label', 'value'],
  template: '<div class="stub-option">{{ label }}</div>'
}

const ElAlertStub = {
  name: 'ElAlert',
  props: ['title', 'type'],
  template: '<div class="stub-alert">{{ title }}</div>'
}

const ElTabsStub = {
  name: 'ElTabs',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div class="stub-tabs"><slot /></div>'
}

const ElTabPaneStub = {
  name: 'ElTabPane',
  props: ['label', 'name'],
  template: '<div class="stub-tab-pane"><slot /></div>'
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

const dialogStub = (name) => ({
  name,
  props: ['modelValue', 'scene', 'sceneId', 'triggerType', 'devices', 'executionId'],
  emits: ['update:modelValue', 'saved', 'retried'],
  template: `<div v-if="modelValue" class="stub-dialog" data-name="${name}"></div>`
})

const STUBS = {
  ElTable: ElTableStub,
  ElTableColumn: ElTableColumnStub,
  ElButton: ElButtonStub,
  ElSwitch: ElSwitchStub,
  ElSelect: ElSelectStub,
  ElOption: ElOptionStub,
  ElAlert: ElAlertStub,
  ElTabs: ElTabsStub,
  ElTabPane: ElTabPaneStub,
  ElInput: passthrough('ElInput'),
  ElInputNumber: passthrough('ElInputNumber'),
  ElTag: passthrough('ElTag'),
  ElPagination: passthrough('ElPagination'),
  SvgIcon: true,
  EmptyState: EmptyStateStub,
  SceneFormDialog: dialogStub('SceneFormDialog'),
  SceneTestDialog: dialogStub('SceneTestDialog'),
  SceneExecutionDrawer: dialogStub('SceneExecutionDrawer')
}

function buttonByText(wrapper, text) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function mountPage() {
  return mount(SceneCenter, { global: { stubs: STUBS } })
}

async function switchToExecutions(wrapper) {
  wrapper.findComponent(ElTabsStub).vm.$emit('update:modelValue', 'executions')
  await flushPromises()
}

describe('views/workbench/scene/SceneCenter', () => {
  let wrapper

  beforeEach(() => {
    hooks.scenes.value = []
    hooks.total.value = 0
    hooks.loading.value = false
    hooks.filters.triggerType = ''
    hooks.filters.enabled = ''
    hooks.filters.keyword = ''
    hooks.executions.value = []
    hooks.executionTotal.value = 0
    hooks.executionLoading.value = false
    hooks.hasActive.value = false
    confirmMock.mockResolvedValue(undefined)
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  it('无场景时渲染空态提示', async () => {
    wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.stub-empty').exists()).toBe(true)
    expect(wrapper.find('.stub-empty').text()).toContain('暂无场景，点击「新建场景」开始配置多设备联动')
  })

  it('拼接触发条件摘要并渲染冷却口径', async () => {
    hooks.scenes.value = [SCENE_PROPERTY, SCENE_TIMER]
    wrapper = mountPage()
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('高温联动')
    expect(text).toContain('temperature > 40')
    expect(text).toContain('定时 0 8 * * *')
    expect(text).toContain('300s')
    expect(text).toContain('不限')
    expect(text).toContain('属性触发')
    expect(text).toContain('定时触发')
  })

  it('触发源筛选即时回写 setFilter', async () => {
    wrapper = mountPage()
    await flushPromises()

    await wrapper.findAllComponents(ElSelectStub)[0].vm.$emit('update:modelValue', 'EVENT')

    expect(hooks.setFilter).toHaveBeenCalledWith('triggerType', 'EVENT')
  })

  it('启停开关以场景 id 提交 enabled 并提示结果', async () => {
    hooks.scenes.value = [SCENE_PROPERTY]
    wrapper = mountPage()
    await flushPromises()

    await wrapper.find('.stub-switch').trigger('click')
    await flushPromises()

    expect(hooks.setEnabled).toHaveBeenCalledWith(1, false)
    expect(messageMock.success).toHaveBeenCalledWith('场景已停用')
  })

  it('手动执行需二次确认后调用 runScene', async () => {
    hooks.scenes.value = [SCENE_PROPERTY]
    wrapper = mountPage()
    await flushPromises()

    await buttonByText(wrapper, '执行').trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalled()
    expect(hooks.runScene).toHaveBeenCalledWith(1)
    expect(messageMock.success).toHaveBeenCalledWith('已提交执行，请到「执行记录」查看进度')
  })

  it('删除场景需二次确认后调用 deleteScene', async () => {
    hooks.scenes.value = [SCENE_PROPERTY]
    wrapper = mountPage()
    await flushPromises()

    await buttonByText(wrapper, '删除').trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalled()
    expect(hooks.deleteScene).toHaveBeenCalledWith(1)
    expect(messageMock.success).toHaveBeenCalledWith('场景已删除')
  })

  it('点击「新建场景」打开表单弹窗', async () => {
    wrapper = mountPage()
    await flushPromises()
    expect(wrapper.find('.stub-dialog').exists()).toBe(false)

    await buttonByText(wrapper, '新建场景').trigger('click')
    await flushPromises()

    expect(wrapper.find('.stub-dialog').exists()).toBe(true)
  })

  it('执行记录页签展示记录、活动轮询提示与 FAILED 专属重试', async () => {
    hooks.executions.value = [EXECUTION_FAILED, EXECUTION_SUCCESS]
    hooks.executionTotal.value = 2
    hooks.hasActive.value = true
    wrapper = mountPage()
    await flushPromises()

    await switchToExecutions(wrapper)

    const text = wrapper.text()
    expect(wrapper.find('.stub-alert').text()).toContain('每 3 秒自动刷新')
    expect(text).toContain('失败')
    expect(text).toContain('成功')
    expect(text).toContain('自动')
    expect(text).toContain('手动')
    expect(text).toContain('1号温控器')
    expect(text).toContain('1/2')
    expect(text).toContain('3/3')
    // 仅 FAILED 行渲染「重试」
    expect(wrapper.findAll('button').filter((button) => button.text() === '重试')).toHaveLength(1)

    await buttonByText(wrapper, '重试').trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalled()
    expect(hooks.retryExecution).toHaveBeenCalledWith(11)
    expect(messageMock.success).toHaveBeenCalledWith('已提交重试')
  })

  it('无活动执行时不渲染轮询提示', async () => {
    hooks.executions.value = [EXECUTION_SUCCESS]
    hooks.hasActive.value = false
    wrapper = mountPage()
    await flushPromises()

    await switchToExecutions(wrapper)

    expect(wrapper.find('.stub-alert').exists()).toBe(false)
  })

  it('执行记录页签加载场景与设备下拉选项', async () => {
    wrapper = mountPage()
    await flushPromises()

    await switchToExecutions(wrapper)

    const text = wrapper.text()
    expect(text).toContain('高温联动')
    expect(text).toContain('1号温控器')
  })
})
