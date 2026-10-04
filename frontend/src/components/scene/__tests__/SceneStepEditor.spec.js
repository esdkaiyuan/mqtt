import { describe, expect, it, vi } from 'vitest'
import { nextTick, reactive } from 'vue'
import { mount } from '@vue/test-utils'
import SceneStepEditor from '../SceneStepEditor.vue'

/**
 * 覆盖步骤流的关键交互与校验分支：增删 / 上下移 / 序号归一、动作切换重置配置、
 * 转发动作锁定「触发设备」、TIMER 源锁定 FIXED（设计文档 §7.4）。
 */
const ElSelectStub = {
  name: 'ElSelect',
  props: ['modelValue', 'placeholder', 'disabled', 'multiple', 'filterable'],
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
const ElInputNumberStub = {
  name: 'ElInputNumber',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<input class="stub-number" :value="modelValue" />'
}
const ElSwitchStub = {
  name: 'ElSwitch',
  props: ['modelValue'],
  emits: ['update:modelValue', 'change'],
  template: '<button class="stub-switch" @click="$emit(\'change\', !modelValue)">{{ modelValue }}</button>'
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
  ElInputNumber: ElInputNumberStub,
  ElSwitch: ElSwitchStub,
  ElRadioGroup: ElRadioGroupStub,
  ElRadioButton: ElRadioButtonStub,
  ElButton: ElButtonStub,
  ElTreeSelect: {
    name: 'ElTreeSelect',
    props: ['modelValue', 'data', 'props', 'placeholder'],
    template: '<div class="stub-tree-select"><slot /></div>'
  },
  SvgIcon: true
}

const DEFAULT_TARGET_CONFIG = { deviceIds: [], productIds: [], groupIds: [], tagIds: [] }

function step(overrides = {}) {
  return {
    seq: 1,
    delaySeconds: 0,
    actionType: 'UPDATE_PROPERTY',
    targetType: 'TRIGGER',
    targetMode: 'device',
    targetConfig: { ...DEFAULT_TARGET_CONFIG },
    actionConfig: { identifier: '', value: '' },
    enabled: true,
    ...overrides
  }
}

function mountEditor(options = {}) {
  const steps = reactive(options.steps ?? [])
  const onUpdate = options.onUpdate ?? vi.fn()
  const wrapper = mount(SceneStepEditor, {
    props: {
      modelValue: steps,
      devices: options.devices ?? [],
      products: options.products ?? [],
      groupTree: options.groupTree ?? [],
      tags: options.tags ?? [],
      timerSource: options.timerSource ?? false,
      'onUpdate:modelValue': onUpdate
    },
    global: { stubs: STUBS }
  })
  return { wrapper, onUpdate, steps }
}

function itemButtons(wrapper, index) {
  return wrapper.findAll('.step-editor__item')[index].findAll('button')
}

describe('components/scene/SceneStepEditor', () => {
  it('无步骤时展示空态提示', () => {
    const { wrapper } = mountEditor({ steps: [] })

    expect(wrapper.find('.step-editor__hint').text()).toContain('请至少添加一个执行步骤。')
    expect(wrapper.findAll('.step-editor__item')).toHaveLength(0)
  })

  it('新增步骤按缺省值追加并归一序号', async () => {
    const { wrapper, onUpdate } = mountEditor({ steps: [] })

    await wrapper.find('.step-editor__head button').trigger('click')

    expect(onUpdate).toHaveBeenCalledTimes(1)
    const next = onUpdate.mock.calls[0][0]
    expect(next).toHaveLength(1)
    expect(next[0]).toMatchObject({
      seq: 1,
      delaySeconds: 0,
      actionType: 'UPDATE_PROPERTY',
      targetType: 'TRIGGER',
      targetMode: 'device',
      enabled: true,
      actionConfig: { identifier: '', value: '' }
    })
    expect(next[0].targetConfig).toEqual(DEFAULT_TARGET_CONFIG)
  })

  it('定时触发源下新增步骤默认目标为 FIXED', async () => {
    const { wrapper, onUpdate } = mountEditor({ steps: [], timerSource: true })

    await wrapper.find('.step-editor__head button').trigger('click')

    expect(onUpdate.mock.calls[0][0][0].targetType).toBe('FIXED')
  })

  it('达到 20 步上限时提示并禁用新增', async () => {
    const steps = Array.from({ length: 20 }, (_, index) => step({ seq: index + 1 }))
    const { wrapper, onUpdate } = mountEditor({ steps })

    expect(wrapper.find('.step-editor__hint').text()).toContain('每个场景最多 20 个步骤。')
    const headButton = wrapper.find('.step-editor__head button')
    expect(headButton.attributes('disabled')).toBeDefined()

    await headButton.trigger('click')
    expect(onUpdate).not.toHaveBeenCalled()
  })

  it('上移步骤交换顺序并重新归一序号', async () => {
    const steps = [
      step({ seq: 1, actionConfig: { identifier: 'a', value: '' } }),
      step({ seq: 2, actionConfig: { identifier: 'b', value: '' } }),
      step({ seq: 3, actionConfig: { identifier: 'c', value: '' } })
    ]
    const { wrapper, onUpdate } = mountEditor({ steps })

    // 第二项的「上移」
    await itemButtons(wrapper, 1)[0].trigger('click')

    const next = onUpdate.mock.calls[0][0]
    expect(next.map((item) => item.actionConfig.identifier)).toEqual(['b', 'a', 'c'])
    expect(next.map((item) => item.seq)).toEqual([1, 2, 3])
  })

  it('首项禁用上移、末项禁用下移', () => {
    const { wrapper } = mountEditor({ steps: [step({ seq: 1 }), step({ seq: 2 })] })

    expect(itemButtons(wrapper, 0)[0].attributes('disabled')).toBeDefined()
    expect(itemButtons(wrapper, 0)[1].attributes('disabled')).toBeUndefined()
    expect(itemButtons(wrapper, 1)[1].attributes('disabled')).toBeDefined()
  })

  it('删除步骤按索引移除并归一序号', async () => {
    const steps = [
      step({ seq: 1, actionConfig: { identifier: 'a', value: '' } }),
      step({ seq: 2, actionConfig: { identifier: 'b', value: '' } })
    ]
    const { wrapper, onUpdate } = mountEditor({ steps })

    await itemButtons(wrapper, 0)[2].trigger('click')

    const next = onUpdate.mock.calls[0][0]
    expect(next).toHaveLength(1)
    expect(next[0].actionConfig.identifier).toBe('b')
    expect(next[0].seq).toBe(1)
  })

  it('切换到下发命令会重建 actionConfig', async () => {
    const target = step()
    const { wrapper } = mountEditor({ steps: [target] })

    await wrapper.find('.step-editor__field--action').findComponent(ElSelectStub).vm.$emit('change', 'SEND_COMMAND')

    expect(target.actionType).toBe('SEND_COMMAND')
    expect(target.actionConfig).toEqual({ commandType: 'property_set', identifier: '', params: '' })
  })

  it('切换到转发动作锁定目标为触发设备并清空 targetConfig', async () => {
    const target = step()
    const { wrapper } = mountEditor({ steps: [target] })

    await wrapper.find('.step-editor__field--action').findComponent(ElSelectStub).vm.$emit('change', 'FORWARD_MQTT')

    expect(target.actionType).toBe('FORWARD_MQTT')
    expect(target.actionConfig).toEqual({ topic: '', qos: 1, payloadTemplate: '' })
    expect(target.targetType).toBe('TRIGGER')
    expect(target.targetConfig).toBeNull()
  })

  it('转发动作下目标选择器禁用且展示固定目标提示', async () => {
    const target = step({ actionType: 'FORWARD_MQTT', actionConfig: { topic: '', qos: 1, payloadTemplate: '' }, targetConfig: null })
    const { wrapper } = mountEditor({ steps: [target] })
    await nextTick()

    expect(wrapper.find('.step-editor__field--target').findComponent(ElSelectStub).props('disabled')).toBe(true)
    expect(wrapper.text()).toContain('转发类动作仅下发一次，目标固定为「触发设备」。')
    // 转发动作不渲染指定目标维度
    expect(wrapper.find('.step-editor__target').exists()).toBe(false)
  })

  it('定时触发源下非转发动作目标锁定 FIXED 并展示指定设备区', async () => {
    const target = step({ targetType: 'FIXED', targetMode: 'device' })
    const { wrapper } = mountEditor({ steps: [target], timerSource: true })
    await nextTick()

    expect(wrapper.find('.step-editor__field--target').findComponent(ElSelectStub).props('disabled')).toBe(true)
    expect(wrapper.text()).toContain('定时触发下无触发设备，目标必须指定具体设备。')
    expect(wrapper.find('.step-editor__target').exists()).toBe(true)
  })

  it('切换目标类型为指定设备后展示目标维度并可切换维度', async () => {
    const target = step()
    const { wrapper } = mountEditor({ steps: [target] })
    expect(wrapper.find('.step-editor__target').exists()).toBe(false)

    await wrapper.find('.step-editor__field--target').findComponent(ElSelectStub).vm.$emit('change', 'FIXED')
    await nextTick()

    expect(target.targetType).toBe('FIXED')
    const targetBlock = wrapper.find('.step-editor__target')
    expect(targetBlock.exists()).toBe(true)

    await targetBlock.findComponent(ElRadioGroupStub).vm.$emit('change', 'product')
    await nextTick()

    expect(target.targetMode).toBe('product')
    expect(target.targetConfig).toEqual(DEFAULT_TARGET_CONFIG)
  })
})
