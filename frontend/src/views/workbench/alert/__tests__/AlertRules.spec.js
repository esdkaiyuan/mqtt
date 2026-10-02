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

/** 规则查询 / 变更以可写容器暴露，便于逐用例设置数据与断言调用。 */
const hooks = vi.hoisted(() => ({
  sourceType: null,
  enabled: null,
  rules: null,
  loading: null,
  refresh: null,
  saving: null,
  createRule: null,
  updateRule: null,
  deleteRule: null
}))

vi.mock('@/composables/useAlerts', async () => {
  const { ref } = await import('vue')
  hooks.sourceType = ref('')
  hooks.enabled = ref('')
  hooks.rules = ref([])
  hooks.loading = ref(false)
  hooks.saving = ref(false)
  hooks.refresh = vi.fn()
  hooks.createRule = vi.fn(() => Promise.resolve())
  hooks.updateRule = vi.fn(() => Promise.resolve())
  hooks.deleteRule = vi.fn(() => Promise.resolve())
  return {
    useAlertRulesQuery: () => ({
      sourceType: hooks.sourceType,
      enabled: hooks.enabled,
      rules: hooks.rules,
      loading: hooks.loading,
      refresh: hooks.refresh
    }),
    useAlertRuleMutations: () => ({
      saving: hooks.saving,
      createRule: hooks.createRule,
      updateRule: hooks.updateRule,
      deleteRule: hooks.deleteRule
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

import AlertRules from '../AlertRules.vue'

const THRESHOLD_RULE = {
  id: 1,
  name: '高温告警',
  sourceType: 'THRESHOLD',
  severity: 'CRITICAL',
  identifier: 'temperature',
  operator: 'GT',
  thresholdValue: '80',
  deviceId: null,
  offlineSeconds: 0,
  suppressWindowSeconds: 300,
  enabled: 1
}

const OFFLINE_RULE = {
  id: 2,
  name: '设备离线',
  sourceType: 'OFFLINE',
  severity: 'WARNING',
  deviceId: null,
  offlineSeconds: 60,
  suppressWindowSeconds: 0,
  enabled: 0
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

const ElDrawerStub = {
  name: 'ElDrawer',
  props: ['modelValue', 'title'],
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('div', { class: 'stub-drawer' }, [
            h('div', { class: 'stub-drawer-title' }, props.title),
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

const STUBS = {
  ElTable: ElTableStub,
  ElTableColumn: ElTableColumnStub,
  ElButton: ElButtonStub,
  ElSwitch: ElSwitchStub,
  ElDrawer: ElDrawerStub,
  ElSelect: passthrough('ElSelect'),
  ElOption: passthrough('ElOption'),
  ElForm: passthrough('ElForm'),
  ElFormItem: passthrough('ElFormItem'),
  ElInput: passthrough('ElInput'),
  ElInputNumber: passthrough('ElInputNumber'),
  ElTag: passthrough('ElTag'),
  EmptyState: EmptyStateStub
}

function buttonByText(wrapper, text) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function mountPage() {
  return mount(AlertRules, { global: { stubs: STUBS } })
}

describe('views/workbench/alert/AlertRules', () => {
  let wrapper

  beforeEach(() => {
    hooks.rules.value = []
    hooks.sourceType.value = ''
    hooks.enabled.value = ''
    confirmMock.mockResolvedValue(undefined)
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  it('无规则时渲染空态提示', async () => {
    wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.stub-empty').exists()).toBe(true)
    expect(wrapper.find('.stub-empty').text()).toContain('暂无告警规则')
  })

  it('按来源类型拼装触发条件并渲染规则行', async () => {
    hooks.rules.value = [THRESHOLD_RULE, OFFLINE_RULE]
    wrapper = mountPage()
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('高温告警')
    expect(text).toContain('temperature > 80')
    expect(text).toContain('离线超过 60s')
    expect(text).toContain('全部设备')
    expect(text).toContain('300s')
    expect(text).toContain('默认')
  })

  it('启停开关以整条规则为基准提交 enabled', async () => {
    hooks.rules.value = [THRESHOLD_RULE]
    wrapper = mountPage()
    await flushPromises()

    await wrapper.find('.stub-switch').trigger('click')
    await flushPromises()

    expect(hooks.updateRule).toHaveBeenCalledTimes(1)
    const [id, payload] = hooks.updateRule.mock.calls[0]
    expect(id).toBe(1)
    expect(payload).toMatchObject({
      name: '高温告警',
      sourceType: 'THRESHOLD',
      identifier: 'temperature',
      operator: 'GT',
      thresholdValue: '80',
      enabled: 0
    })
  })

  it('删除规则需二次确认后调用接口', async () => {
    hooks.rules.value = [THRESHOLD_RULE]
    wrapper = mountPage()
    await flushPromises()

    await buttonByText(wrapper, '删除').trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalled()
    expect(hooks.deleteRule).toHaveBeenCalledWith(1)
    expect(messageMock.success).toHaveBeenCalled()
  })

  it('点击「新建规则」打开空表单抽屉', async () => {
    wrapper = mountPage()
    await flushPromises()
    expect(wrapper.find('.stub-drawer').exists()).toBe(false)

    await buttonByText(wrapper, '新建规则').trigger('click')

    expect(wrapper.find('.stub-drawer').exists()).toBe(true)
    expect(wrapper.find('.stub-drawer-title').text()).toBe('新建告警规则')
  })
})