import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import HmiWidget from '../HmiWidget.vue'

const ElButton = {
  name: 'ElButton',
  props: ['disabled', 'type', 'size'],
  emits: ['click'],
  template: '<button class="stub-button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
}

const ElSwitch = {
  name: 'ElSwitch',
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue', 'change'],
  template:
    '<button class="stub-switch" :disabled="disabled" ' +
    '@click="$emit(\'update:modelValue\', !modelValue); $emit(\'change\', !modelValue)" />'
}

const STUBS = {
  ElButton,
  ElSwitch,
  ElSlider: { name: 'ElSlider', template: '<div class="stub-slider" />' },
  ElInputNumber: { name: 'ElInputNumber', template: '<div class="stub-input-number" />' },
  ElInput: { name: 'ElInput', template: '<div class="stub-input" />' },
  ElSelect: { name: 'ElSelect', template: '<div class="stub-select"><slot /></div>' },
  ElOption: { name: 'ElOption', template: '<span />' },
  BoardPanel: { name: 'BoardPanel', props: ['panel', 'deviceNames'], template: '<div class="stub-board-panel" />' }
}

function mountWidget(overrides = {}) {
  return mount(HmiWidget, {
    props: {
      widget: { id: 'w1', type: 'value', title: '温度', identifier: 'temperature', span: 3, unit: '°C' },
      property: { identifier: 'temperature', type: 'float', accessMode: 'r', enumKeys: [] },
      value: 26.5,
      ...overrides
    },
    global: { stubs: STUBS }
  })
}

describe('HmiWidget', () => {
  it('数值控件展示值与单位', () => {
    const wrapper = mountWidget()
    expect(wrapper.find('.value-display__number').text()).toBe('26.5')
    expect(wrapper.find('.value-display__unit').text()).toBe('°C')
  })

  it('开关变化时向上抛出归一化后的布尔值', async () => {
    const wrapper = mountWidget({
      widget: { id: 'w2', type: 'switch', title: '电源', identifier: 'power', span: 3 },
      property: { identifier: 'power', type: 'bool', accessMode: 'rw', enumKeys: [] },
      value: false
    })

    await wrapper.findComponent(ElSwitch).trigger('click')

    expect(wrapper.emitted('update')).toHaveLength(1)
    expect(wrapper.emitted('update')[0][0].identifier).toBe('power')
    expect(wrapper.emitted('update')[0][1]).toBe(true)
  })

  it('服务按钮抛出 command 事件', async () => {
    const wrapper = mountWidget({
      widget: { id: 'w3', type: 'button', title: '重启', identifier: 'reboot', span: 3 },
      property: null,
      service: { identifier: 'reboot', callType: 'async', input: [] }
    })

    await wrapper.findComponent(ElButton).trigger('click')

    expect(wrapper.emitted('command')).toHaveLength(1)
    expect(wrapper.emitted('command')[0][0].identifier).toBe('reboot')
  })

  it('预览模式禁用服务按钮并渲染趋势占位', () => {
    const wrapper = mountWidget({
      widget: { id: 'w4', type: 'button', title: '重启', identifier: 'reboot', span: 3 },
      service: null,
      preview: true
    })
    expect(wrapper.findComponent(ElButton).attributes('disabled')).toBeDefined()

    const chart = mountWidget({
      widget: { id: 'w5', type: 'chart', title: '温度趋势', identifier: 'temperature', span: 12 },
      preview: true
    })
    expect(chart.find('.chart-placeholder').text()).toBe('趋势图')
  })

  it('属性被移除时给出提示', () => {
    const wrapper = mountWidget({
      widget: { id: 'w6', type: 'value', title: '旧属性', identifier: 'legacy', span: 3 },
      property: null
    })
    expect(wrapper.find('.widget-warning').text()).toContain('属性已从物模型移除')
  })

  it('事件面板按事件类型与标识符展示最近事件', () => {
    const wrapper = mountWidget({
      widget: { id: 'w7', type: 'events', title: '最近事件', span: 12 },
      property: null,
      events: [
        { id: 1, identifier: 'overheat', eventType: 'alert', reportedAt: '2026-10-21T10:00:00' },
        { id: 2, identifier: 'boot', eventType: 'info', reportedAt: '2026-10-21T09:00:00' }
      ]
    })
    expect(wrapper.findAll('.events-list li')).toHaveLength(2)
    expect(wrapper.find('.event-type').text()).toBe('alert')
    expect(wrapper.text()).toContain('overheat')
  })
})
