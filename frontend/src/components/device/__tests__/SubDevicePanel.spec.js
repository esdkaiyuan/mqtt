import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SubDevicePanel from '../SubDevicePanel.vue'

const ElButtonStub = {
  name: 'ElButton',
  props: ['type', 'loading', 'disabled', 'size', 'plain'],
  emits: ['click'],
  template:
    '<button class="stub-button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
}

const MOUNT_OPTIONS = {
  global: {
    stubs: { ElButton: ElButtonStub },
    directives: { loading: {} }
  }
}

const SUB_ONLINE = {
  id: 21,
  deviceKey: 'sensor-1',
  deviceName: '温度子设备',
  status: 'ONLINE',
  lastSeen: '2026-10-06T09:00:00'
}

const SUB_OFFLINE = {
  id: 22,
  deviceKey: 'sensor-2',
  deviceName: '湿度子设备',
  status: 'OFFLINE',
  lastSeen: null
}

function mountPanel(props = {}) {
  return mount(SubDevicePanel, {
    props: { subDevices: [SUB_ONLINE, SUB_OFFLINE], ...props },
    ...MOUNT_OPTIONS
  })
}

const buttonLabels = (wrapper) => wrapper.findAll('.stub-button').map((item) => item.text())

describe('components/device/SubDevicePanel', () => {
  it('始终提供挂载既有设备动作', () => {
    const wrapper = mountPanel()

    expect(buttonLabels(wrapper)[0]).toBe('挂载既有设备')
  })

  it('逐行展示子设备键、名称与在线状态', () => {
    const wrapper = mountPanel()

    const rows = wrapper.findAll('.sub-device-item')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('sensor-1')
    expect(rows[0].text()).toContain('温度子设备')
    expect(rows[0].find('.status-badge').text()).toBe('在线')
    expect(rows[1].find('.status-badge').text()).toBe('离线')
  })

  it('子设备无最后上报时间时以占位符展示', () => {
    const wrapper = mountPanel()

    const rows = wrapper.findAll('.sub-device-item')
    expect(rows[0].find('.sub-device-time').text()).not.toBe('-')
    expect(rows[1].find('.sub-device-time').text()).toBe('-')
  })

  it('空列表展示空态文案', () => {
    const wrapper = mountPanel({ subDevices: [] })

    expect(wrapper.text()).toContain('该设备下暂无子设备')
    expect(wrapper.findAll('.sub-device-item')).toHaveLength(0)
  })

  it('点击挂载既有设备抛出 attach 事件', async () => {
    const wrapper = mountPanel()

    await wrapper.findAll('.stub-button').at(0).trigger('click')

    expect(wrapper.emitted('attach')).toHaveLength(1)
  })

  it('点击行内详情抛出 view 事件并带回子设备', async () => {
    const wrapper = mountPanel()

    await wrapper.findAll('.sub-device-item').at(0).findAll('.stub-button').at(0).trigger('click')

    expect(wrapper.emitted('view')[0][0]).toEqual(SUB_ONLINE)
  })

  it('点击行内解绑抛出 detach 事件并带回子设备', async () => {
    const wrapper = mountPanel()

    await wrapper.findAll('.sub-device-item').at(1).findAll('.stub-button').at(1).trigger('click')

    expect(wrapper.emitted('detach')[0][0]).toEqual(SUB_OFFLINE)
  })

  it('无写权限时挂载与解绑动作全部置灰', () => {
    const wrapper = mountPanel({ canWrite: false })

    const buttons = wrapper.findAll('.stub-button')
    const writeButtons = buttons.filter((item) => ['挂载既有设备', '解绑'].includes(item.text()))
    const readButtons = buttons.filter((item) => item.text() === '详情')

    expect(writeButtons).toHaveLength(3)
    expect(writeButtons.every((item) => item.attributes('disabled') !== undefined)).toBe(true)
    expect(readButtons.every((item) => item.attributes('disabled') === undefined)).toBe(true)
    expect(wrapper.text()).toContain('仅管理员可挂载或解绑子设备')
  })

  it('挂载等待态透传至挂载动作', () => {
    const wrapper = mountPanel({ attaching: true })

    expect(wrapper.findAll('.stub-button').at(0).attributes('disabled')).toBeUndefined()
  })
})
