import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DeviceCard from '../DeviceCard.vue'

const ElCheckboxStub = {
  name: 'ElCheckbox',
  props: ['modelValue'],
  emits: ['change'],
  template: '<input class="stub-checkbox" type="checkbox" :checked="modelValue" @change="$emit(\'change\', $event.target.checked)" />'
}

const ElTagStub = {
  name: 'ElTag',
  props: ['type', 'size', 'effect'],
  template: '<span class="stub-tag"><slot /></span>'
}

const ElButtonStub = {
  name: 'ElButton',
  props: ['type', 'link'],
  emits: ['click'],
  template: '<button class="stub-button" @click="$emit(\'click\')"><slot /></button>'
}

const STUBS = {
  ElCheckbox: ElCheckboxStub,
  ElTag: ElTagStub,
  ElButton: ElButtonStub
}

const DEVICE = {
  id: 'd-1',
  deviceName: '温湿度传感器',
  deviceKey: 'sensor-1',
  deviceType: 'sensor',
  status: 'ONLINE',
  topic: 'device/sensor-1/data',
  description: '一号车间',
  groups: [{ id: 1, name: '华东厂区' }],
  tags: [{ id: 9, name: '重点设备', color: '#f56c6c' }]
}

function mountCard(props = {}) {
  return mount(DeviceCard, {
    props: { device: DEVICE, ...props },
    global: { stubs: STUBS }
  })
}

describe('components/device/DeviceCard', () => {
  it('渲染设备基础信息与中文状态 / 类型', () => {
    const wrapper = mountCard()
    const text = wrapper.text()

    expect(text).toContain('温湿度传感器')
    expect(text).toContain('sensor-1')
    expect(text).toContain('device/sensor-1/data')
    expect(text).toContain('传感器')
    expect(text).toContain('在线')
    expect(text).toContain('一号车间')
  })

  it('未开启选择时不渲染复选框', () => {
    const wrapper = mountCard()

    expect(wrapper.find('.stub-checkbox').exists()).toBe(false)
    expect(wrapper.find('.device-card--selected').exists()).toBe(false)
  })

  it('开启选择时勾选状态映射到选中样式', () => {
    const wrapper = mountCard({ selectable: true, selected: true })

    expect(wrapper.find('.stub-checkbox').exists()).toBe(true)
    expect(wrapper.classes()).toContain('device-card--selected')
  })

  it('勾选复选框抛出 toggle-select 并携带设备对象', async () => {
    const wrapper = mountCard({ selectable: true, selected: false })

    await wrapper.find('.stub-checkbox').trigger('change')

    expect(wrapper.emitted('toggle-select')[0][0]).toEqual(DEVICE)
  })

  it('渲染分组 chip 与带色标签', () => {
    const wrapper = mountCard()

    expect(wrapper.find('.group-chip').text()).toContain('华东厂区')
    const tag = wrapper.find('.stub-tag')
    expect(tag.text()).toBe('重点设备')
    expect(tag.attributes('style')).toContain('color: rgb(245, 108, 108)')
  })

  it('无分组与标签时不渲染 chips 容器', () => {
    const wrapper = mountCard({ device: { ...DEVICE, groups: [], tags: [] } })

    expect(wrapper.find('.device-card-chips').exists()).toBe(false)
  })

  it('查看 / 编辑 / 删除按钮各自抛出事件', async () => {
    const wrapper = mountCard()
    const buttons = wrapper.findAll('.stub-button')

    await buttons[0].trigger('click')
    await buttons[1].trigger('click')
    await buttons[2].trigger('click')

    expect(wrapper.emitted('view')[0][0]).toEqual(DEVICE)
    expect(wrapper.emitted('edit')[0][0]).toEqual(DEVICE)
    expect(wrapper.emitted('delete')[0][0]).toEqual(DEVICE)
  })
})