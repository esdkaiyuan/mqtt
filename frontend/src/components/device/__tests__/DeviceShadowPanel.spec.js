import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

import { ElMessage } from 'element-plus'
import DeviceShadowPanel from '../DeviceShadowPanel.vue'

/**
 * 轻量替身：断言集中在「三列渲染 + 期望值提交载荷」，
 * 用最小桩件把 v-model 与插槽打通，避免拉起整套 Element Plus。
 */
const STUBS = {
  ElAlert: {
    name: 'ElAlert',
    props: ['title', 'description', 'type'],
    template: '<div class="stub-alert">{{ title }}{{ description }}</div>'
  },
  ElTag: {
    name: 'ElTag',
    props: ['type', 'size'],
    template: '<span class="stub-tag"><slot /></span>'
  },
  ElForm: { name: 'ElForm', template: '<form class="stub-form"><slot /></form>' },
  ElFormItem: {
    name: 'ElFormItem',
    props: ['label'],
    template: '<div class="stub-form-item"><span class="stub-label">{{ label }}</span><slot /></div>'
  },
  ElButton: {
    name: 'ElButton',
    props: ['loading', 'type'],
    emits: ['click'],
    template: '<button class="stub-button" @click="$emit(\'click\')"><slot /></button>'
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
    props: ['modelValue', 'type', 'rows', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<span class="stub-input" />'
  }
}

const CAPABILITY = {
  modeled: true,
  version: 1,
  properties: [
    { identifier: 'targetTemp', type: 'int', min: 16, max: 30, integer: true, enumKeys: [], textLength: null }
  ],
  services: []
}

const NO_MODEL = { modeled: false, version: 0, properties: [], services: [] }

const SHADOW = {
  modeled: true,
  version: 3,
  desired: { targetTemp: '26' },
  reported: { targetTemp: '24' },
  delta: { targetTemp: '26' },
  updatedAt: '2026-10-02T10:00:00.000'
}

const SHADOW_CONVERGED = { ...SHADOW, desired: { targetTemp: '24' }, delta: {} }

function mountPanel(props) {
  return mount(DeviceShadowPanel, { props, global: { stubs: STUBS } })
}

function buttons(wrapper) {
  return wrapper.findAllComponents({ name: 'ElButton' })
}

describe('components/device/DeviceShadowPanel', () => {
  it('未建模时给出引导提示且不渲染三列', () => {
    const wrapper = mountPanel({ shadow: { ...SHADOW, modeled: false }, capability: NO_MODEL })

    expect(wrapper.find('.stub-alert').text()).toContain('该产品未定义物模型')
    expect(wrapper.find('.shadow-column').exists()).toBe(false)
    expect(wrapper.find('.stub-form').exists()).toBe(false)
  })

  it('渲染 desired / reported / delta 三列与版本号，delta 行高亮', () => {
    const wrapper = mountPanel({ shadow: SHADOW, capability: CAPABILITY })

    expect(wrapper.text()).toContain('期望值 desired')
    expect(wrapper.text()).toContain('上报值 reported')
    expect(wrapper.text()).toContain('差异 delta')
    expect(wrapper.find('.stub-tag').text()).toContain('version 3')
    expect(wrapper.findAll('.shadow-column')).toHaveLength(3)
    // desired 与 reported 两侧的 targetTemp 均处于差异态
    expect(wrapper.findAll('.shadow-row--delta')).toHaveLength(2)
  })

  it('离线时提示期望值已保存待补发', () => {
    const wrapper = mountPanel({ shadow: SHADOW, capability: CAPABILITY, online: false })

    expect(wrapper.find('.stub-alert').text()).toContain('设备离线')
    expect(wrapper.find('.stub-alert').text()).toContain('自动下发')
  })

  it('填写属性值后下发期望值', async () => {
    const wrapper = mountPanel({ shadow: SHADOW, capability: CAPABILITY })

    wrapper.findComponent({ name: 'ElInputNumber' }).vm.$emit('update:modelValue', 28)
    await nextTick()
    // 第 1 个按钮是「按 delta 补发」，第 2 个是「下发期望值」
    await buttons(wrapper)[1].trigger('click')

    expect(wrapper.emitted('set-desired')).toEqual([[{ params: { targetTemp: 28 } }]])
  })

  it('按 delta 补发时把归一化文本还原为原生类型', async () => {
    const wrapper = mountPanel({ shadow: SHADOW, capability: CAPABILITY })

    expect(buttons(wrapper)[0].text()).toContain('按 delta 补发')
    expect(buttons(wrapper)[0].text()).toContain('1')

    await buttons(wrapper)[0].trigger('click')

    expect(wrapper.emitted('set-desired')).toEqual([[{ params: { targetTemp: 26 } }]])
  })

  it('delta 收敛时不显示补发按钮，空表单提交被拦截', async () => {
    const wrapper = mountPanel({ shadow: SHADOW_CONVERGED, capability: CAPABILITY })

    expect(wrapper.text()).toContain('已收敛，无差异')
    const labels = wrapper.findAllComponents({ name: 'ElButton' }).map((item) => item.text())
    expect(labels.some((text) => text.includes('补发'))).toBe(false)

    await buttons(wrapper)[0].trigger('click')

    expect(ElMessage.warning).toHaveBeenCalledWith('请至少填写一个期望值')
    expect(wrapper.emitted('set-desired')).toBeUndefined()
  })
})
