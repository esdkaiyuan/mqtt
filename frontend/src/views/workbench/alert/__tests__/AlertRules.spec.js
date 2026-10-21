import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, h, inject, nextTick, provide } from 'vue'
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

/** 分组 / 标签候选列表以可写容器暴露，供作用范围文案解析用例注入数据。 */
const scopeHooks = vi.hoisted(() => ({ groups: null, tags: null }))

vi.mock('@/composables/useDeviceGroups', async () => {
  const { ref } = await import('vue')
  scopeHooks.groups = ref([])
  scopeHooks.tags = ref([])
  return {
    useDeviceGroupListQuery: () => ({ groups: scopeHooks.groups, loading: ref(false) }),
    useTagListQuery: () => ({ tags: scopeHooks.tags, loading: ref(false) })
  }
})

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

const WINDOW_RULE = {
  ...THRESHOLD_RULE,
  id: 3,
  name: '高温均值告警',
  windowSeconds: 300,
  aggregateType: 'AVG',
  minSamples: 3
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

const ElSelectStub = {
  name: 'ElSelect',
  props: ['modelValue', 'placeholder', 'multiple'],
  emits: ['update:modelValue', 'change'],
  setup(_, { slots }) {
    return () => h('div', { class: 'stub-select' }, slots.default?.())
  }
}

const ElOptionStub = {
  name: 'ElOption',
  props: ['label', 'value'],
  setup(props) {
    return () => h('div', { class: 'stub-option' }, props.label)
  }
}

const ElInputStub = {
  name: 'ElInput',
  props: ['modelValue', 'placeholder', 'type'],
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('input', {
        class: 'stub-input',
        value: props.modelValue ?? '',
        placeholder: props.placeholder,
        onInput: (event) => emit('update:modelValue', event.target.value)
      })
  }
}

const ElFormItemStub = {
  name: 'ElFormItem',
  props: ['label'],
  setup(props, { slots }) {
    return () =>
      h('div', { class: 'stub-form-item' }, [
        props.label ? h('span', { class: 'stub-form-label' }, props.label) : null,
        slots.default?.()
      ])
  }
}

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
  ElSelect: ElSelectStub,
  ElOption: ElOptionStub,
  ElForm: passthrough('ElForm'),
  ElFormItem: ElFormItemStub,
  ElInput: ElInputStub,
  ElInputNumber: passthrough('ElInputNumber'),
  ElTag: passthrough('ElTag'),
  EmptyState: EmptyStateStub
}

function buttonByText(wrapper, text) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

/** 按选项文案定位下拉，作用范围选择器含「按分组」，分组多选含分组名，彼此不重名。 */
function findSelectByOption(wrapper, text) {
  return wrapper.findAllComponents({ name: 'ElSelect' }).find((select) => select.text().includes(text))
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
    scopeHooks.groups.value = [
      { id: 1, name: '华东厂区' },
      { id: 2, name: '一号车间' }
    ]
    scopeHooks.tags.value = [{ id: 9, name: '重点设备' }]
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

  it('窗口阈值展示聚合摘要并按原配置提交', async () => {
    hooks.rules.value = [WINDOW_RULE]
    wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('300s 均值 · temperature > 80')

    await buttonByText(wrapper, '编辑').trigger('click')
    await nextTick()
    const drawer = wrapper.find('.stub-drawer')
    expect(drawer.text()).toContain('统计窗口')
    expect(drawer.text()).toContain('聚合方式')
    expect(drawer.text()).toContain('最少样本数')

    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()
    expect(hooks.updateRule.mock.calls[0][1]).toMatchObject({
      windowSeconds: 300,
      aggregateType: 'AVG',
      minSamples: 3
    })
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

  it('作用范围默认全部设备，切档后展示对应条件控件', async () => {
    wrapper = mountPage()
    await flushPromises()

    await buttonByText(wrapper, '新建规则').trigger('click')
    await nextTick()

    const drawer = wrapper.find('.stub-drawer')
    expect(drawer.text()).toContain('作用范围')
    expect(drawer.text()).not.toContain('作用分组')

    const scopeSelect = findSelectByOption(wrapper, '按分组')
    expect(scopeSelect).toBeTruthy()

    scopeSelect.vm.$emit('update:modelValue', 'GROUP')
    await nextTick()
    expect(drawer.text()).toContain('作用分组')
    expect(drawer.text()).toContain('含子分组')
    expect(drawer.text()).not.toContain('作用标签')

    scopeSelect.vm.$emit('update:modelValue', 'TAG')
    await nextTick()
    expect(drawer.text()).toContain('作用标签')
    expect(drawer.text()).not.toContain('作用分组')

    scopeSelect.vm.$emit('update:modelValue', 'DEVICE')
    await nextTick()
    expect(drawer.text()).toContain('作用设备')
  })

  it('按分组作用时提交体携带 scopeType 与 groupIds', async () => {
    wrapper = mountPage()
    await flushPromises()

    await buttonByText(wrapper, '新建规则').trigger('click')
    await nextTick()

    await wrapper.find('input[placeholder="如：高温告警"]').setValue('分组高温告警')
    await wrapper.find('input[placeholder="如：temperature"]').setValue('temperature')
    await wrapper.find('input[placeholder="如：80"]').setValue('80')

    findSelectByOption(wrapper, '按分组').vm.$emit('update:modelValue', 'GROUP')
    await nextTick()
    findSelectByOption(wrapper, '华东厂区').vm.$emit('update:modelValue', [1, 2])
    await nextTick()

    await buttonByText(wrapper, '创建').trigger('click')
    await flushPromises()

    expect(hooks.createRule).toHaveBeenCalledTimes(1)
    expect(hooks.createRule.mock.calls[0][0]).toMatchObject({
      name: '分组高温告警',
      scopeType: 'GROUP',
      deviceId: null,
      groupIds: [1, 2],
      tagIds: []
    })
  })

  it('作用范围缺失必填项时拦截提交', async () => {
    wrapper = mountPage()
    await flushPromises()

    await buttonByText(wrapper, '新建规则').trigger('click')
    await nextTick()

    await wrapper.find('input[placeholder="如：高温告警"]').setValue('空分组告警')
    findSelectByOption(wrapper, '按分组').vm.$emit('update:modelValue', 'GROUP')
    await nextTick()

    await buttonByText(wrapper, '创建').trigger('click')
    await flushPromises()

    expect(hooks.createRule).not.toHaveBeenCalled()
    expect(messageMock.warning).toHaveBeenCalledWith('请选择至少一个作用分组')
  })

  it('启停开关保留原作用范围字段', async () => {
    hooks.rules.value = [{ ...THRESHOLD_RULE, scopeType: 'GROUP', groupIds: [1, 2] }]
    wrapper = mountPage()
    await flushPromises()

    await wrapper.find('.stub-switch').trigger('click')
    await flushPromises()

    expect(hooks.updateRule.mock.calls[0][1]).toMatchObject({
      scopeType: 'GROUP',
      deviceId: null,
      groupIds: [1, 2],
      enabled: 0
    })
  })

  it('列表按作用范围档位渲染文案', async () => {
    hooks.rules.value = [
      { ...THRESHOLD_RULE, id: 11, scopeType: 'GROUP', groupIds: [1, 2] },
      { ...THRESHOLD_RULE, id: 12, scopeType: 'TAG', tagIds: [9] },
      { ...THRESHOLD_RULE, id: 13, scopeType: 'DEVICE', deviceId: 9 },
      { ...THRESHOLD_RULE, id: 14, scopeType: 'ALL' }
    ]
    wrapper = mountPage()
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('分组：华东厂区、一号车间')
    expect(text).toContain('标签：重点设备')
    expect(text).toContain('1号温控器')
    expect(text).toContain('全部设备')
  })
})