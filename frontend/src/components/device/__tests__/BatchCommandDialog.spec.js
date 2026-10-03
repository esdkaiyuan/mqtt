import { describe, expect, it } from 'vitest'
import { h } from 'vue'
import { mount } from '@vue/test-utils'
import BatchCommandDialog from '../BatchCommandDialog.vue'

const ElDialogStub = {
  name: 'ElDialog',
  props: ['modelValue', 'title'],
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('div', { class: 'stub-dialog' }, [slots.default?.(), slots.footer?.()])
        : null
  }
}

const ElAlertStub = {
  name: 'ElAlert',
  props: ['title', 'description', 'type'],
  template: '<div class="stub-alert">{{ title }}|{{ description }}</div>'
}

const ElFormStub = { name: 'ElForm', template: '<form class="stub-form"><slot /></form>' }
const ElFormItemStub = {
  name: 'ElFormItem',
  props: ['label'],
  template: '<div class="stub-form-item" :data-label="label"><span class="stub-label">{{ label }}</span><slot /></div>'
}

const ElRadioGroupStub = {
  name: 'ElRadioGroup',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div class="stub-radio-group"><slot /></div>'
}

const ElRadioButtonStub = {
  name: 'ElRadioButton',
  props: ['value'],
  template: '<span class="stub-radio">{{ value }}</span>'
}

const ElInputStub = {
  name: 'ElInput',
  props: ['modelValue', 'type'],
  emits: ['update:modelValue'],
  template:
    '<textarea v-if="type === \'textarea\'" class="stub-input stub-textarea" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' +
    '<input v-else class="stub-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
}

const ElButtonStub = {
  name: 'ElButton',
  props: ['type', 'loading'],
  emits: ['click'],
  template: '<button class="stub-button" @click="$emit(\'click\')"><slot /></button>'
}

const STUBS = {
  ElDialog: ElDialogStub,
  ElAlert: ElAlertStub,
  ElForm: ElFormStub,
  ElFormItem: ElFormItemStub,
  ElRadioGroup: ElRadioGroupStub,
  ElRadioButton: ElRadioButtonStub,
  ElInput: ElInputStub,
  ElButton: ElButtonStub
}

function mountDialog(props = {}) {
  return mount(BatchCommandDialog, {
    props: { visible: true, count: 3, ...props },
    global: { stubs: STUBS }
  })
}

const submitButton = (wrapper) => wrapper.findAll('.stub-button').at(-1)

describe('components/device/BatchCommandDialog', () => {
  it('打开时展示批量提示与目标台数', () => {
    const wrapper = mountDialog({ count: 5 })
    const alert = wrapper.find('.stub-alert').text()

    expect(alert).toContain('将对 5 台设备逐台下发')
    expect(alert).toContain('单台失败不影响其余')
  })

  it('默认属性设置模式，不展示服务标识符输入', () => {
    const wrapper = mountDialog()

    expect(wrapper.find('.stub-radio').text()).toBe('property_set')
    expect(wrapper.find('.stub-textarea').exists()).toBe(true)
    expect(wrapper.find('input.stub-input').exists()).toBe(false)
  })

  it('切换到服务调用后出现服务标识符输入', async () => {
    const wrapper = mountDialog()

    await wrapper.findComponent({ name: 'ElRadioGroup' }).vm.$emit('update:modelValue', 'service')

    expect(wrapper.find('input.stub-input').exists()).toBe(true)
    expect(wrapper.text()).toContain('服务标识符')
  })

  it('参数为空或非法 JSON 时不提交', async () => {
    const wrapper = mountDialog()

    await submitButton(wrapper).trigger('click')
    expect(wrapper.emitted('submit')).toBeUndefined()

    await wrapper.find('.stub-textarea').setValue('{bad json')
    await submitButton(wrapper).trigger('click')
    expect(wrapper.emitted('submit')).toBeUndefined()

    await wrapper.find('.stub-textarea').setValue('[1,2]')
    await submitButton(wrapper).trigger('click')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('属性设置提交参数对象且 identifier 为 null', async () => {
    const wrapper = mountDialog()

    await wrapper.find('.stub-textarea').setValue('{"temperature": 26}')
    await submitButton(wrapper).trigger('click')

    expect(wrapper.emitted('submit')[0][0]).toEqual({
      type: 'property_set',
      identifier: null,
      params: { temperature: 26 }
    })
  })

  it('服务调用缺少标识符时不提交', async () => {
    const wrapper = mountDialog()

    await wrapper.findComponent({ name: 'ElRadioGroup' }).vm.$emit('update:modelValue', 'service')
    await wrapper.find('.stub-textarea').setValue('{"delay": 3}')
    await submitButton(wrapper).trigger('click')

    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('服务调用提交标识符与入参', async () => {
    const wrapper = mountDialog()

    await wrapper.findComponent({ name: 'ElRadioGroup' }).vm.$emit('update:modelValue', 'service')
    await wrapper.find('input.stub-input').setValue('reboot')
    await wrapper.find('.stub-textarea').setValue('{"delay": 3}')
    await submitButton(wrapper).trigger('click')

    expect(wrapper.emitted('submit')[0][0]).toEqual({
      type: 'service',
      identifier: 'reboot',
      params: { delay: 3 }
    })
  })

  it('重新打开时清空上一次的草稿', async () => {
    const wrapper = mountDialog()
    await wrapper.find('.stub-textarea').setValue('{"temperature": 26}')

    await wrapper.setProps({ visible: false })
    await wrapper.setProps({ visible: true })

    expect(wrapper.find('.stub-textarea').element.value).toBe('')
  })
})