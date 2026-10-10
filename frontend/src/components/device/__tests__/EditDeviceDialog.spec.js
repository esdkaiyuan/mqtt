import { describe, expect, it } from 'vitest'
import { h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import EditDeviceDialog from '../EditDeviceDialog.vue'

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
  props: ['modelValue', 'placeholder'],
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
  props: ['type', 'loading', 'link'],
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

const DEVICE = {
  id: 5,
  deviceName: '温度传感器',
  deviceType: 'sensor',
  topic: 'device/t1/data',
  description: '车间'
}

const GATEWAY = { id: 10, deviceName: '车间网关', deviceKey: 'gw-1' }

const formItem = (wrapper, label) =>
  wrapper.findAll('.stub-form-item').find((item) => item.attributes('data-label') === label)

// 对话框在 visible 由 false 翻为 true 时才把设备草稿拷进表单，故先关后开
async function openDialog(props = {}) {
  const wrapper = mount(EditDeviceDialog, {
    props: { visible: false, device: DEVICE, ...props },
    global: { stubs: STUBS }
  })
  await wrapper.setProps({ visible: true })
  await nextTick()
  return wrapper
}

const submitButton = (wrapper) => wrapper.findAll('.stub-button').at(-1)

describe('components/device/EditDeviceDialog', () => {
  it('设备不属于任何网关时不渲染所属网关行', async () => {
    const wrapper = await openDialog({ parentGateway: null })

    expect(formItem(wrapper, '所属网关')).toBeUndefined()
  })

  it('设备已挂在网关下时展示网关名称与标识', async () => {
    const wrapper = await openDialog({ parentGateway: GATEWAY })

    const row = formItem(wrapper, '所属网关')
    expect(row.text()).toContain('车间网关')
    expect(row.text()).toContain('gw-1')
  })

  it('点击查看网关抛出 view-gateway 事件并带上网关 id', async () => {
    const wrapper = await openDialog({ parentGateway: GATEWAY })

    await formItem(wrapper, '所属网关').findComponent({ name: 'ElButton' }).trigger('click')

    expect(wrapper.emitted('view-gateway')).toEqual([[10]])
  })

  it('打开时把设备草稿拷进表单，提交只透传可编辑字段', async () => {
    const wrapper = await openDialog({ parentGateway: GATEWAY })

    await submitButton(wrapper).trigger('click')

    const payload = wrapper.emitted('submit')[0][0]
    expect(payload).toEqual({
      id: 5,
      deviceName: '温度传感器',
      deviceType: 'sensor',
      topic: 'device/t1/data',
      description: '车间'
    })
  })
})
