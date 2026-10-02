import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

import { ElMessage } from 'element-plus'
import DeviceControlPanel from '../DeviceControlPanel.vue'

/**
 * 轻量替身：控制面板的断言集中在「字段渲染 + 提交载荷」，
 * 不需要拉起整套 Element Plus 表单，用最小桩件把 v-model 与插槽打通即可。
 */
const STUBS = {
  ElAlert: {
    name: 'ElAlert',
    props: ['title', 'description', 'type'],
    template: '<div class="stub-alert">{{ title }}{{ description }}</div>'
  },
  ElForm: { name: 'ElForm', template: '<form class="stub-form"><slot /></form>' },
  ElFormItem: {
    name: 'ElFormItem',
    props: ['label', 'required'],
    template:
      '<div class="stub-form-item" :data-required="required ? \'yes\' : \'no\'">' +
      '<span class="stub-label">{{ label }}</span><slot /></div>'
  },
  ElRadioGroup: {
    name: 'ElRadioGroup',
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<div class="stub-radio-group"><slot /></div>'
  },
  ElRadioButton: {
    name: 'ElRadioButton',
    props: ['value'],
    template: '<span class="stub-radio-button"><slot /></span>'
  },
  ElButton: {
    name: 'ElButton',
    props: ['loading', 'disabled', 'type'],
    emits: ['click'],
    template:
      '<button class="stub-button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
  },
  ElInputNumber: {
    name: 'ElInputNumber',
    props: ['modelValue', 'min', 'max', 'step', 'precision'],
    emits: ['update:modelValue'],
    template: '<span class="stub-input-number" />'
  },
  ElSwitch: {
    name: 'ElSwitch',
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<span class="stub-switch" />'
  },
  ElSelect: {
    name: 'ElSelect',
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<span class="stub-select"><slot /></span>'
  },
  ElOption: {
    name: 'ElOption',
    props: ['value', 'label'],
    template: '<span class="stub-option">{{ label }}</span>'
  },
  ElDatePicker: {
    name: 'ElDatePicker',
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<span class="stub-date-picker" />'
  },
  ElInput: {
    name: 'ElInput',
    props: ['modelValue', 'maxlength', 'type', 'rows', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<span class="stub-input" />'
  }
}

const PROPERTY_CAPABILITY = {
  modeled: true,
  version: 1,
  properties: [
    { identifier: 'targetTemp', type: 'int', min: 16, max: 30, integer: true, enumKeys: [], textLength: null }
  ],
  services: []
}

const SERVICE_CAPABILITY = {
  modeled: true,
  version: 1,
  properties: [],
  services: [
    {
      identifier: 'reboot',
      callType: 'async',
      input: [
        { identifier: 'delay', type: 'int', min: 0, max: 60, integer: true, enumKeys: [], textLength: null, required: true }
      ]
    }
  ]
}

const NO_MODEL = { modeled: false, version: 0, properties: [], services: [] }

function mountPanel(props) {
  return mount(DeviceControlPanel, { props, global: { stubs: STUBS } })
}

function button(wrapper) {
  return wrapper.findComponent({ name: 'ElButton' })
}

describe('components/device/DeviceControlPanel', () => {
  it('未建模时给出引导提示且不渲染表单', () => {
    const wrapper = mountPanel({ capability: NO_MODEL, deviceKey: 'dev-1' })

    expect(wrapper.find('.stub-alert').text()).toContain('该产品未定义物模型')
    expect(wrapper.find('.stub-form').exists()).toBe(false)
  })

  it('展示下行 Topic 与可写属性字段，空值提交被拦截', async () => {
    const wrapper = mountPanel({ capability: PROPERTY_CAPABILITY, deviceKey: 'dev-1' })

    expect(wrapper.text()).toContain('device/dev-1/cmd/down')
    expect(wrapper.find('.stub-form-item').attributes('data-required')).toBe('no')
    expect(wrapper.text()).toContain('targetTemp')

    await button(wrapper).trigger('click')

    expect(ElMessage.warning).toHaveBeenCalledWith('请至少填写一个参数')
    expect(wrapper.emitted('send')).toBeUndefined()
  })

  it('填写属性值后按 property_set 异步下发', async () => {
    const wrapper = mountPanel({ capability: PROPERTY_CAPABILITY, deviceKey: 'dev-1' })

    wrapper.findComponent({ name: 'ElInputNumber' }).vm.$emit('update:modelValue', 26)
    await nextTick()
    await button(wrapper).trigger('click')

    expect(wrapper.emitted('send')).toEqual([
      [{ type: 'property_set', identifier: null, params: { targetTemp: 26 }, callType: 'async' }]
    ])
  })

  it('服务调用模式保留必填标记，缺失入参时拦截提交', async () => {
    const wrapper = mountPanel({ capability: SERVICE_CAPABILITY, deviceKey: 'dev-1' })

    wrapper.findAllComponents({ name: 'ElRadioGroup' })[0].vm.$emit('update:modelValue', 'service')
    await nextTick()

    // 第 1 项是服务选择器，第 2 项才是入参 delay
    const items = wrapper.findAll('.stub-form-item')
    expect(items[1].text()).toContain('delay')
    expect(items[1].attributes('data-required')).toBe('yes')

    await button(wrapper).trigger('click')

    expect(ElMessage.warning).toHaveBeenCalledWith('请填写必填参数：delay')
    expect(wrapper.emitted('send')).toBeUndefined()
  })

  it('服务调用填写入参并以同步方式下发', async () => {
    const wrapper = mountPanel({ capability: SERVICE_CAPABILITY, deviceKey: 'dev-1' })

    const groups = wrapper.findAllComponents({ name: 'ElRadioGroup' })
    groups[0].vm.$emit('update:modelValue', 'service')
    groups[1].vm.$emit('update:modelValue', 'sync')
    await nextTick()

    wrapper.findComponent({ name: 'ElInputNumber' }).vm.$emit('update:modelValue', 3)
    await nextTick()
    await button(wrapper).trigger('click')

    expect(wrapper.emitted('send')).toEqual([
      [{ type: 'service', identifier: 'reboot', params: { delay: 3 }, callType: 'sync' }]
    ])
  })

  it('结构化入参的非法 JSON 被拦截且不提交', async () => {
    const capability = {
      modeled: true,
      version: 1,
      properties: [],
      services: [
        {
          identifier: 'config',
          callType: 'async',
          input: [{ identifier: 'ext', type: 'struct', required: false }]
        }
      ]
    }
    const wrapper = mountPanel({ capability, deviceKey: 'dev-1' })

    wrapper.findAllComponents({ name: 'ElRadioGroup' })[0].vm.$emit('update:modelValue', 'service')
    await nextTick()

    wrapper.findComponent({ name: 'ElInput' }).vm.$emit('update:modelValue', 'not json')
    await nextTick()
    await button(wrapper).trigger('click')

    expect(ElMessage.warning).toHaveBeenCalledWith('「ext」不是合法的 JSON')
    expect(wrapper.emitted('send')).toBeUndefined()
  })
})