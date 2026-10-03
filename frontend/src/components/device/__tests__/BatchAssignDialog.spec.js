import { describe, expect, it } from 'vitest'
import { h } from 'vue'
import { mount } from '@vue/test-utils'
import BatchAssignDialog from '../BatchAssignDialog.vue'

const ElDialogStub = {
  name: 'ElDialog',
  props: ['modelValue', 'title'],
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('div', { class: 'stub-dialog', 'data-title': props.title }, [
            slots.default?.(),
            slots.footer?.()
          ])
        : null
  }
}

const ElFormStub = { name: 'ElForm', template: '<form class="stub-form"><slot /></form>' }
const ElFormItemStub = {
  name: 'ElFormItem',
  props: ['label'],
  template: '<div class="stub-form-item" :data-label="label"><slot /></div>'
}

const ElSelectStub = {
  name: 'ElSelect',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div class="stub-select"><slot /></div>'
}

const ElOptionStub = {
  name: 'ElOption',
  props: ['label', 'value'],
  template: '<span class="stub-option">{{ label }}</span>'
}

const ElButtonStub = {
  name: 'ElButton',
  props: ['type', 'loading'],
  emits: ['click'],
  template: '<button class="stub-button" @click="$emit(\'click\')"><slot /></button>'
}

const STUBS = {
  ElDialog: ElDialogStub,
  ElForm: ElFormStub,
  ElFormItem: ElFormItemStub,
  ElSelect: ElSelectStub,
  ElOption: ElOptionStub,
  ElButton: ElButtonStub
}

const OPTIONS = [
  { id: 3, name: '华东厂区' },
  { id: 4, name: '重点设备', color: '#f56c6c' }
]

function mountDialog(props = {}) {
  return mount(BatchAssignDialog, {
    props: { visible: true, count: 2, options: OPTIONS, ...props },
    global: { stubs: STUBS }
  })
}

const submitButton = (wrapper) => wrapper.findAll('.stub-button').at(-1)

describe('components/device/BatchAssignDialog', () => {
  it('分组加入模式展示标题、标签与动作文案', () => {
    const wrapper = mountDialog({ mode: 'group', action: 'ADD' })

    expect(wrapper.find('.stub-dialog').attributes('data-title')).toBe('批量加入')
    expect(wrapper.find('.stub-form-item').attributes('data-label')).toBe('目标分组')
    expect(wrapper.text()).toContain('将对 2 台设备加入该分组')
    expect(submitButton(wrapper).text()).toBe('加入')
  })

  it('分组移出模式切换动作文案', () => {
    const wrapper = mountDialog({ mode: 'group', action: 'REMOVE' })

    expect(wrapper.find('.stub-dialog').attributes('data-title')).toBe('批量移出')
    expect(wrapper.text()).toContain('将对 2 台设备移出该分组')
    expect(submitButton(wrapper).text()).toBe('移出')
  })

  it('标签打标 / 去标模式切换单位与文案', () => {
    const add = mountDialog({ mode: 'tag', action: 'ADD' })
    expect(add.find('.stub-dialog').attributes('data-title')).toBe('批量打标签')
    expect(add.find('.stub-form-item').attributes('data-label')).toBe('目标标签')
    expect(add.text()).toContain('将对 2 台设备打标签该标签')

    const remove = mountDialog({ mode: 'tag', action: 'REMOVE' })
    expect(remove.find('.stub-dialog').attributes('data-title')).toBe('批量去标签')
    expect(submitButton(remove).text()).toBe('去标签')
  })

  it('渲染候选分组 / 标签选项', () => {
    const wrapper = mountDialog()

    const options = wrapper.findAll('.stub-option')
    expect(options).toHaveLength(2)
    expect(options[0].text()).toBe('华东厂区')
    expect(options[1].text()).toBe('重点设备')
  })

  it('未选择目标时不提交', async () => {
    const wrapper = mountDialog()

    await submitButton(wrapper).trigger('click')

    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('选择目标后提交其 id', async () => {
    const wrapper = mountDialog()

    await wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('update:modelValue', 3)
    await submitButton(wrapper).trigger('click')

    expect(wrapper.emitted('submit')[0][0]).toBe(3)
  })

  it('重新打开时清空上一次的选择', async () => {
    const wrapper = mountDialog()
    await wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('update:modelValue', 3)

    await wrapper.setProps({ visible: false })
    await wrapper.setProps({ visible: true })
    await submitButton(wrapper).trigger('click')

    expect(wrapper.emitted('submit')).toBeUndefined()
  })
})