import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

const { messageMock } = vi.hoisted(() => ({
  messageMock: { success: vi.fn(), warning: vi.fn(), info: vi.fn(), error: vi.fn() }
}))

vi.mock('element-plus', () => ({ ElMessage: messageMock }))

/** 规则变更 / 试运行以可写容器暴露，便于逐用例断言提交体。 */
const hooks = vi.hoisted(() => ({
  saving: null,
  testing: null,
  createRule: null,
  updateRule: null,
  testRule: null
}))

vi.mock('@/composables/useRules', async () => {
  const { ref } = await import('vue')
  hooks.saving = ref(false)
  hooks.testing = ref(false)
  hooks.createRule = vi.fn(() => Promise.resolve())
  hooks.updateRule = vi.fn(() => Promise.resolve())
  hooks.testRule = vi.fn(() => Promise.resolve(null))
  return {
    useRuleMutations: () => ({
      saving: hooks.saving,
      createRule: hooks.createRule,
      updateRule: hooks.updateRule
    }),
    useRuleTestMutation: () => ({ testing: hooks.testing, testRule: hooks.testRule })
  }
})

vi.mock('@/api/device', () => ({
  deviceApi: {
    getList: vi.fn(() =>
      Promise.resolve({ code: 200, data: { records: [{ id: 9, deviceName: '1号温控器', deviceKey: 'dev-9' }] } })
    )
  }
}))

const scopeHooks = vi.hoisted(() => ({ groups: null, tags: null }))
const canvasHooks = vi.hoisted(() => ({ focusError: vi.fn(() => true) }))

vi.mock('@/composables/useDeviceGroups', async () => {
  const { ref } = await import('vue')
  scopeHooks.groups = ref([])
  scopeHooks.tags = ref([])
  return {
    useDeviceGroupListQuery: () => ({ groups: scopeHooks.groups, loading: ref(false) }),
    useTagListQuery: () => ({ tags: scopeHooks.tags, loading: ref(false) })
  }
})

import RuleEditor from '../RuleEditor.vue'

const TAG_RULE = {
  id: 5,
  name: '标签联动',
  sourceType: 'PROPERTY',
  identifier: 'temperature',
  operator: 'GT',
  thresholdValue: '40',
  actionType: 'UPDATE_PROPERTY',
  actionConfig: { identifier: 'switch', value: 'on' },
  scopeType: 'TAG',
  tagIds: [9],
  cooldownSeconds: 0,
  enabled: 1
}

/* ---------- Element Plus 最小替身 ---------- */
const ElDrawerStub = {
  name: 'ElDrawer',
  props: ['modelValue', 'title', 'size'],
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('div', { class: 'stub-drawer', 'data-size': props.size }, [
            h('div', { class: 'stub-drawer-title' }, props.title),
            slots.default?.(),
            slots.footer?.()
          ])
        : null
  }
}

const ElDialogStub = {
  name: 'ElDialog',
  props: ['modelValue', 'title'],
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('div', { class: 'stub-dialog' }, [h('div', { class: 'stub-dialog-title' }, props.title), slots.default?.()])
        : null
  }
}

const ElButtonStub = {
  name: 'ElButton',
  emits: ['click'],
  setup(_, { slots, emit }) {
    return () => h('button', { class: 'stub-btn', onClick: () => emit('click') }, slots.default?.())
  }
}

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

const ElRadioGroupStub = {
  name: 'ElRadioGroup',
  props: ['modelValue'],
  emits: ['update:modelValue', 'change'],
  setup(_, { slots }) {
    return () => h('div', { class: 'stub-radio-group' }, slots.default?.())
  }
}

const ElRadioButtonStub = {
  name: 'ElRadioButton',
  props: ['value'],
  setup(props, { slots }) {
    return () => h('button', { type: 'button', class: 'stub-radio-button', 'data-value': props.value }, slots.default?.())
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

const passthrough = (name) => ({
  name,
  setup(_, { slots }) {
    return () => h('div', { class: `stub-${name}` }, slots.default?.())
  }
})

const STUBS = {
  ElDrawer: ElDrawerStub,
  ElDialog: ElDialogStub,
  ElButton: ElButtonStub,
  ElSelect: ElSelectStub,
  ElOption: ElOptionStub,
  ElInput: ElInputStub,
  ElRadioGroup: ElRadioGroupStub,
  ElRadioButton: ElRadioButtonStub,
  ElForm: passthrough('ElForm'),
  ElFormItem: ElFormItemStub,
  ElInputNumber: passthrough('ElInputNumber'),
  ElSwitch: passthrough('ElSwitch'),
  ElTag: passthrough('ElTag'),
  RuleOrchestrationView: {
    name: 'RuleOrchestrationView',
    props: ['form', 'devices', 'groups', 'tags'],
    setup(_, { expose }) {
      expose({ focusError: canvasHooks.focusError })
      return () => h('div', { class: 'stub-rule-orch' })
    }
  }
}

function buttonByText(wrapper, text) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

/** 按选项文案定位下拉，作用范围选择器含「按标签」，标签多选含标签名，彼此不重名。 */
function findSelectByOption(wrapper, text) {
  return wrapper.findAllComponents({ name: 'ElSelect' }).find((select) => select.text().includes(text))
}

function viewToggle(wrapper) {
  return wrapper.findAllComponents({ name: 'ElRadioGroup' }).find((group) => group.text().includes('画布'))
}

async function switchView(wrapper, mode) {
  viewToggle(wrapper).vm.$emit('update:modelValue', mode)
  await nextTick()
}

function mountEditor(rule = null) {
  return mount(RuleEditor, { props: { modelValue: true, rule }, global: { stubs: STUBS } })
}

describe('views/workbench/rule/RuleEditor', () => {
  let wrapper

  beforeEach(() => {
    scopeHooks.groups.value = [{ id: 1, name: '华东厂区' }]
    scopeHooks.tags.value = [{ id: 9, name: '重点设备' }]
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  it('作用范围默认全部设备，切档后展示对应条件控件', async () => {
    wrapper = mountEditor()
    await flushPromises()

    const drawer = wrapper.find('.stub-drawer')
    expect(drawer.text()).toContain('作用范围')
    expect(drawer.text()).not.toContain('作用分组')

    findSelectByOption(wrapper, '按分组').vm.$emit('update:modelValue', 'GROUP')
    await nextTick()
    expect(drawer.text()).toContain('作用分组')
    expect(drawer.text()).toContain('含子分组')

    findSelectByOption(wrapper, '按分组').vm.$emit('update:modelValue', 'TAG')
    await nextTick()
    expect(drawer.text()).toContain('作用标签')
    expect(drawer.text()).not.toContain('作用分组')
  })

  it('按标签作用时提交体携带 scopeType 与 tagIds', async () => {
    wrapper = mountEditor()
    await flushPromises()

    await wrapper.find('input[placeholder="如：高温联动降温"]').setValue('标签联动')
    await wrapper.find('input[placeholder="如：temperature"]').setValue('temperature')
    await wrapper.find('input[placeholder="如：40"]').setValue('40')
    await wrapper.find('input[placeholder="如：switch"]').setValue('switch')
    await wrapper.find('input[placeholder="如：on"]').setValue('on')

    findSelectByOption(wrapper, '按标签').vm.$emit('update:modelValue', 'TAG')
    await nextTick()
    findSelectByOption(wrapper, '重点设备').vm.$emit('update:modelValue', [9])
    await nextTick()

    await buttonByText(wrapper, '创建').trigger('click')
    await flushPromises()

    expect(hooks.createRule).toHaveBeenCalledTimes(1)
    expect(hooks.createRule.mock.calls[0][0]).toMatchObject({
      scopeType: 'TAG',
      deviceId: null,
      groupIds: [],
      tagIds: [9]
    })
  })

  it('编辑标签档规则时回显作用范围与标签', async () => {
    wrapper = mountEditor(TAG_RULE)
    await flushPromises()

    expect(findSelectByOption(wrapper, '按标签').props('modelValue')).toBe('TAG')
    expect(findSelectByOption(wrapper, '重点设备').props('modelValue')).toEqual([9])
  })

  it('作用范围缺失必填项时拦截提交', async () => {
    wrapper = mountEditor()
    await flushPromises()

    await wrapper.find('input[placeholder="如：高温联动降温"]').setValue('空标签规则')
    findSelectByOption(wrapper, '按标签').vm.$emit('update:modelValue', 'TAG')
    await nextTick()

    await buttonByText(wrapper, '创建').trigger('click')
    await flushPromises()

    expect(hooks.createRule).not.toHaveBeenCalled()
    expect(messageMock.warning).toHaveBeenCalledWith('请选择至少一个作用标签')
  })

  it('切换表单与画布时保留同一份规则字段', async () => {
    wrapper = mountEditor()
    await flushPromises()

    await wrapper.find('input[placeholder="如：高温联动降温"]').setValue('画布规则')
    await wrapper.find('input[placeholder="如：temperature"]').setValue('temperature')
    await switchView(wrapper, 'canvas')
    await flushPromises()

    expect(wrapper.find('.stub-rule-orch').exists()).toBe(true)
    expect(wrapper.find('.stub-drawer').attributes('data-size')).toBe('min(880px, 92vw)')
    expect(wrapper.findComponent({ name: 'RuleOrchestrationView' }).props('form').name).toBe('画布规则')
    await switchView(wrapper, 'form')
    await nextTick()
    expect(wrapper.find('input[placeholder="如：高温联动降温"]').element.value).toBe('画布规则')
    expect(wrapper.find('input[placeholder="如：temperature"]').element.value).toBe('temperature')
  })

  it('画布视图提交与表单视图提交的 payload 逐字段一致', async () => {
    wrapper = mountEditor()
    await flushPromises()
    await wrapper.find('input[placeholder="如：高温联动降温"]').setValue('温度联动')
    await wrapper.find('input[placeholder="如：temperature"]').setValue('temperature')
    await wrapper.find('input[placeholder="如：40"]').setValue('40')
    await wrapper.find('input[placeholder="如：switch"]').setValue('switch')
    await wrapper.find('input[placeholder="如：on"]').setValue('on')

    await buttonByText(wrapper, '创建').trigger('click')
    await flushPromises()
    const formPayload = hooks.createRule.mock.calls[0][0]

    await switchView(wrapper, 'canvas')
    await flushPromises()
    await buttonByText(wrapper, '创建').trigger('click')
    await flushPromises()

    expect(hooks.createRule.mock.calls[1][0]).toEqual(formPayload)
  })

  it('画布视图校验失败时复用表单错误并定位对应节点', async () => {
    wrapper = mountEditor()
    await flushPromises()
    await wrapper.find('input[placeholder="如：高温联动降温"]').setValue('缺少目标的规则')
    await wrapper.find('input[placeholder="如：temperature"]').setValue('temperature')
    await wrapper.find('input[placeholder="如：40"]').setValue('40')
    await switchView(wrapper, 'canvas')

    await buttonByText(wrapper, '创建').trigger('click')
    await flushPromises()

    expect(hooks.createRule).not.toHaveBeenCalled()
    expect(messageMock.warning).toHaveBeenCalledWith('请填写目标属性标识符')
    expect(canvasHooks.focusError).toHaveBeenCalledWith('请填写目标属性标识符')
  })
})
