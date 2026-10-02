import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ThingModelTypeEditor from '../ThingModelTypeEditor.vue'
import { createDataType } from '@/utils/thingModel'

/**
 * 只保留「结构 + 事件」的最小替身，避免为纯逻辑用例拉起整套 Element Plus 组件。
 */
const ElSelectStub = {
  name: 'ElSelect',
  props: ['modelValue'],
  emits: ['update:modelValue', 'change'],
  template: '<div class="stub-select"><slot /></div>'
}
const ElOptionStub = {
  name: 'ElOption',
  props: ['label', 'value'],
  template: '<span class="stub-option">{{ label }}</span>'
}
const ElFormItemStub = {
  name: 'ElFormItem',
  props: ['label'],
  template: '<div class="stub-form-item"><span class="stub-label">{{ label }}</span><slot /></div>'
}
const ElInputStub = {
  name: 'ElInput',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<input class="stub-input" :value="modelValue" />'
}
const ElInputNumberStub = {
  name: 'ElInputNumber',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<input class="stub-number" :value="modelValue" />'
}
const ElButtonStub = {
  name: 'ElButton',
  emits: ['click'],
  template: '<button class="stub-button" @click="$emit(\'click\')"><slot /></button>'
}

const STUBS = {
  ElSelect: ElSelectStub,
  ElOption: ElOptionStub,
  ElFormItem: ElFormItemStub,
  ElInput: ElInputStub,
  ElInputNumber: ElInputNumberStub,
  ElButton: ElButtonStub
}

function mountEditor(model, depth = 1, onUpdate = () => {}) {
  return mount(ThingModelTypeEditor, {
    props: { modelValue: model, depth, 'onUpdate:modelValue': onUpdate },
    global: { stubs: STUBS }
  })
}

describe('components/product/ThingModelTypeEditor', () => {
  it('整数类型渲染 min / max / step / unit 四个约束字段', () => {
    const wrapper = mountEditor(createDataType('int'))
    const labels = wrapper.findAll('.stub-label').map((node) => node.text())

    expect(labels).toContain('最小值')
    expect(labels).toContain('最大值')
    expect(labels).toContain('步长')
    expect(labels).toContain('单位')
  })

  it('文本类型只渲染最大长度', () => {
    const wrapper = mountEditor(createDataType('text'))
    const labels = wrapper.findAll('.stub-label').map((node) => node.text())

    expect(labels).toContain('最大长度')
    expect(labels).not.toContain('最小值')
  })

  it('枚举类型渲染枚举项并可新增', async () => {
    const model = createDataType('enum')
    const wrapper = mountEditor(model)

    expect(wrapper.text()).toContain('枚举项')
    const before = Object.keys(model.specs).length

    await wrapper.findAll('.stub-button')[0].trigger('click')

    expect(Object.keys(model.specs).length).toBe(before + 1)
  })

  it('结构体类型递归渲染成员编辑器', () => {
    const wrapper = mountEditor(createDataType('struct'))

    expect(wrapper.text()).toContain('成员')
    // 根编辑器 + 至少一个递归子编辑器
    expect(wrapper.findAll('.type-editor').length).toBeGreaterThanOrEqual(2)
  })

  it('数组类型递归渲染元素类型编辑器', () => {
    const wrapper = mountEditor(createDataType('array'))

    expect(wrapper.text()).toContain('元素类型')
    expect(wrapper.findAll('.type-editor').length).toBeGreaterThanOrEqual(2)
  })

  it('切换类型会以新类型的默认约束回写模型', async () => {
    const onUpdate = vi.fn()
    const wrapper = mountEditor(createDataType('int'), 1, onUpdate)

    await wrapper.findComponent(ElSelectStub).vm.$emit('change', 'bool')

    expect(onUpdate).toHaveBeenCalledWith({ type: 'bool' })
  })

  it('递归到第 5 层后不再提供 struct / array 选项', () => {
    const wrapper = mountEditor(createDataType('int'), 5)
    const text = wrapper.text()

    expect(text).toContain('整数 int')
    expect(text).not.toContain('结构体 struct')
    expect(text).not.toContain('数组 array')
  })
})