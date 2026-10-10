import { describe, expect, it } from 'vitest'
import { h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import CreateDeviceDialog from '../CreateDeviceDialog.vue'

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

const ElInputStub = {
  name: 'ElInput',
  props: ['modelValue', 'placeholder', 'type', 'rows'],
  emits: ['update:modelValue'],
  template: '<input class="stub-input" />'
}

const ElSelectStub = {
  name: 'ElSelect',
  props: ['modelValue', 'placeholder', 'loading'],
  emits: ['update:modelValue'],
  setup(props, { slots }) {
    return () =>
      h('div', { class: 'stub-select', 'data-model': String(props.modelValue ?? '') }, slots.default?.())
  }
}

const ElOptionStub = {
  name: 'ElOption',
  props: ['label', 'value'],
  template: '<span class="stub-option" :data-value="value">{{ label }}</span>'
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
  ElInput: ElInputStub,
  ElSelect: ElSelectStub,
  ElOption: ElOptionStub,
  ElButton: ElButtonStub
}

const GATEWAY_A = { id: 10, deviceName: '车间网关', deviceKey: 'gw-1' }
const GATEWAY_B = { id: 11, deviceName: '楼宇网关', deviceKey: 'gw-2' }

function mountDialog(props = {}) {
  return mount(CreateDeviceDialog, {
    props: { visible: true, ...props },
    global: { stubs: STUBS }
  })
}

const formItem = (wrapper, label) =>
  wrapper.findAll('.stub-form-item').find((item) => item.attributes('data-label') === label)

const selectIn = (wrapper, label) => formItem(wrapper, label).findComponent({ name: 'ElSelect' })
const inputIn = (wrapper, label) => formItem(wrapper, label).findComponent({ name: 'ElInput' })
const submitButton = (wrapper) => wrapper.findAll('.stub-button').at(-1)

async function fillRequired(wrapper) {
  await inputIn(wrapper, '设备名称').vm.$emit('update:modelValue', '新设备')
  await inputIn(wrapper, '设备标识').vm.$emit('update:modelValue', 'dev-1')
  await inputIn(wrapper, 'MQTT Topic').vm.$emit('update:modelValue', 'device/dev-1/data')
  await selectIn(wrapper, '所属产品').vm.$emit('update:modelValue', 7)
  await selectIn(wrapper, '设备类型').vm.$emit('update:modelValue', 'sensor')
  await nextTick()
}

describe('components/device/CreateDeviceDialog', () => {
  it('当前产品下没有网关时不渲染父网关选择', () => {
    const wrapper = mountDialog({ gateways: [] })

    expect(formItem(wrapper, '父网关')).toBeUndefined()
  })

  it('存在网关候选时渲染父网关选择并列出候选', () => {
    const wrapper = mountDialog({ gateways: [GATEWAY_A, GATEWAY_B] })

    const options = formItem(wrapper, '父网关').findAll('.stub-option')
    expect(options).toHaveLength(2)
    expect(options[0].text()).toContain('车间网关')
    expect(options[0].text()).toContain('gw-1')
    expect(options[0].attributes('data-value')).toBe('10')
  })

  it('提交时透传所选父网关', async () => {
    const wrapper = mountDialog({ gateways: [GATEWAY_A] })
    await fillRequired(wrapper)
    await selectIn(wrapper, '父网关').vm.$emit('update:modelValue', 10)
    await nextTick()

    await submitButton(wrapper).trigger('click')

    const payload = wrapper.emitted('submit')[0][0]
    expect(payload.parentGatewayId).toBe(10)
    expect(payload.productId).toBe(7)
    expect(payload.deviceKey).toBe('dev-1')
  })

  it('未选父网关时提交留空，按普通设备创建', async () => {
    const wrapper = mountDialog({ gateways: [GATEWAY_A] })
    await fillRequired(wrapper)

    await submitButton(wrapper).trigger('click')

    expect(wrapper.emitted('submit')[0][0].parentGatewayId).toBe('')
  })

  it('切换所属产品时清空父网关选择并通知外部', async () => {
    const wrapper = mountDialog({ gateways: [GATEWAY_A, GATEWAY_B] })
    await selectIn(wrapper, '所属产品').vm.$emit('update:modelValue', 7)
    await selectIn(wrapper, '父网关').vm.$emit('update:modelValue', 10)
    await nextTick()
    expect(selectIn(wrapper, '父网关').attributes('data-model')).toBe('10')

    await selectIn(wrapper, '所属产品').vm.$emit('update:modelValue', 8)
    await nextTick()

    expect(selectIn(wrapper, '父网关').attributes('data-model')).toBe('')
    expect(wrapper.emitted('product-change')).toEqual([[7], [8]])
  })
})
