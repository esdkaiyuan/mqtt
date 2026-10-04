import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import SceneConditionEditor from '../SceneConditionEditor.vue'

/**
 * 只保留「结构 + 事件」的最小替身，聚焦条件组逻辑：AND/OR 切换、条件项增删、
 * TIMER 触发源下条件设备必填的校验分支（设计文档 §7.3）。
 */
const ElSelectStub = {
  name: 'ElSelect',
  props: ['modelValue', 'placeholder', 'clearable', 'filterable'],
  emits: ['update:modelValue', 'change'],
  template: '<div class="stub-select"><slot /></div>'
}
const ElOptionStub = {
  name: 'ElOption',
  props: ['label', 'value'],
  template: '<span class="stub-option">{{ label }}</span>'
}
const ElInputStub = {
  name: 'ElInput',
  props: ['modelValue', 'placeholder'],
  emits: ['update:modelValue'],
  template: '<input class="stub-input" :value="modelValue" />'
}
const ElRadioGroupStub = {
  name: 'ElRadioGroup',
  props: ['modelValue'],
  emits: ['update:modelValue', 'change'],
  template: '<div class="stub-radio-group"><slot /></div>'
}
const ElRadioButtonStub = {
  name: 'ElRadioButton',
  props: ['value'],
  template: '<label class="stub-radio"><slot /></label>'
}
const ElButtonStub = {
  name: 'ElButton',
  props: ['disabled'],
  emits: ['click'],
  template: '<button class="stub-button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
}

const STUBS = {
  ElSelect: ElSelectStub,
  ElOption: ElOptionStub,
  ElInput: ElInputStub,
  ElRadioGroup: ElRadioGroupStub,
  ElRadioButton: ElRadioButtonStub,
  ElButton: ElButtonStub,
  SvgIcon: true
}

function mountEditor(options = {}) {
  const contracts = options.conditions ?? []
  const onUpdate = options.onUpdate ?? vi.fn()
  const onLogicUpdate = options.onLogicUpdate ?? vi.fn()
  const wrapper = mount(SceneConditionEditor, {
    props: {
      modelValue: contracts,
      logic: options.logic ?? 'AND',
      devices: options.devices ?? [],
      timerSource: options.timerSource ?? false,
      'onUpdate:modelValue': onUpdate,
      'onUpdate:logic': onLogicUpdate
    },
    global: { stubs: STUBS }
  })
  return { wrapper, onUpdate, onLogicUpdate }
}

function condition(overrides = {}) {
  return { deviceId: null, identifier: '', operator: 'GT', threshold: '', ...overrides }
}

describe('components/scene/SceneConditionEditor', () => {
  it('无附加条件时展示空态提示', () => {
    const { wrapper } = mountEditor({ conditions: [] })

    expect(wrapper.find('.condition-editor__hint').exists()).toBe(true)
    expect(wrapper.find('.condition-editor__hint').text()).toContain('未配置附加条件，触发匹配后直接执行步骤。')
    expect(wrapper.findAll('.condition-editor__item')).toHaveLength(0)
  })

  it('渲染 AND / OR 两个逻辑选项', () => {
    const { wrapper } = mountEditor({ conditions: [] })

    const text = wrapper.text()
    expect(text).toContain('满足全部条件')
    expect(text).toContain('满足任一条件')
  })

  it('切换逻辑回写 logic 模型', async () => {
    const { wrapper, onLogicUpdate } = mountEditor({ conditions: [], logic: 'AND' })

    await wrapper.findComponent(ElRadioGroupStub).vm.$emit('update:modelValue', 'OR')

    expect(onLogicUpdate).toHaveBeenCalledTimes(1)
    expect(onLogicUpdate).toHaveBeenCalledWith('OR')
  })

  it('新增条件按默认值追加一项', async () => {
    const { wrapper, onUpdate } = mountEditor({ conditions: [] })

    await wrapper.find('.condition-editor__head button').trigger('click')

    expect(onUpdate).toHaveBeenCalledTimes(1)
    const next = onUpdate.mock.calls[0][0]
    expect(next).toHaveLength(1)
    expect(next[0]).toEqual({ deviceId: null, identifier: '', operator: 'GT', threshold: '' })
  })

  it('在已有条件后继续追加而不覆盖', async () => {
    const first = condition({ identifier: 'temperature', threshold: '40' })
    const { wrapper, onUpdate } = mountEditor({ conditions: [first] })

    await wrapper.find('.condition-editor__head button').trigger('click')

    const next = onUpdate.mock.calls[0][0]
    expect(next).toHaveLength(2)
    expect(next[0]).toEqual(first)
    expect(next[1]).toEqual({ deviceId: null, identifier: '', operator: 'GT', threshold: '' })
  })

  it('删除条件按索引移除', async () => {
    const first = condition({ identifier: 'a' })
    const second = condition({ identifier: 'b' })
    const third = condition({ identifier: 'c' })
    const { wrapper, onUpdate } = mountEditor({ conditions: [first, second, third] })

    const items = wrapper.findAll('.condition-editor__item')
    expect(items).toHaveLength(3)

    // 第二项内的「删除」按钮
    await items[1].find('button').trigger('click')

    expect(onUpdate).toHaveBeenCalledTimes(1)
    const next = onUpdate.mock.calls[0][0]
    expect(next).toHaveLength(2)
    expect(next.map((item) => item.identifier)).toEqual(['a', 'c'])
  })

  it('非定时触发源下条件设备可空并提示「触发设备（默认）」', () => {
    const { wrapper } = mountEditor({ conditions: [condition()], timerSource: false })

    expect(wrapper.find('.condition-editor__item-hint').exists()).toBe(false)
    const deviceSelect = wrapper.findAllComponents(ElSelectStub)[0]
    expect(deviceSelect.props('placeholder')).toBe('触发设备（默认）')
    expect(deviceSelect.props('clearable')).toBe(true)
  })

  it('定时触发源下未选设备时提示条件设备必填', () => {
    const { wrapper } = mountEditor({ conditions: [condition({ deviceId: null })], timerSource: true })

    const hint = wrapper.find('.condition-editor__item-hint')
    expect(hint.exists()).toBe(true)
    expect(hint.text()).toContain('定时触发下条件设备必填。')

    const deviceSelect = wrapper.findAllComponents(ElSelectStub)[0]
    expect(deviceSelect.props('placeholder')).toBe('请选择设备（必填）')
    expect(deviceSelect.props('clearable')).toBe(false)
  })

  it('定时触发源下已选设备时不再提示必填', () => {
    const { wrapper } = mountEditor({
      conditions: [condition({ deviceId: 9 })],
      timerSource: true
    })

    expect(wrapper.find('.condition-editor__item-hint').exists()).toBe(false)
  })

  it('条件设备的候选来自传入的 devices', () => {
    const { wrapper } = mountEditor({
      conditions: [condition()],
      devices: [
        { id: 1, deviceName: '1号温控器', deviceKey: 'dev-1' },
        { id: 2, deviceKey: 'dev-2' }
      ]
    })

    const options = wrapper.findAllComponents(ElOptionStub)
    const labels = options.map((node) => node.props('label'))
    // deviceName 优先，缺省回退 deviceKey
    expect(labels).toContain('1号温控器')
    expect(labels).toContain('dev-2')
  })
})
