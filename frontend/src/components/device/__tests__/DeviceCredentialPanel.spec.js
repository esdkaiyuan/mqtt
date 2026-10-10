import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DeviceCredentialPanel from '../DeviceCredentialPanel.vue'

const ElButtonStub = {
  name: 'ElButton',
  props: ['type', 'loading', 'disabled'],
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

function mountPanel(props = {}) {
  return mount(DeviceCredentialPanel, {
    props: { authMode: 'X509', ...props },
    ...MOUNT_OPTIONS
  })
}

const buttonLabels = (wrapper) => wrapper.findAll('.stub-button').map((item) => item.text())

describe('components/device/DeviceCredentialPanel', () => {
  it('X.509 产品展示签发与吊销两个动作', () => {
    const wrapper = mountPanel({ authMode: 'X509' })

    expect(buttonLabels(wrapper)).toEqual(['签发证书', '吊销证书'])
    expect(wrapper.find('.auth-badge').text()).toBe('X.509 证书')
  })

  it('JWT 产品只展示签发令牌', () => {
    const wrapper = mountPanel({ authMode: 'JWT' })

    expect(buttonLabels(wrapper)).toEqual(['签发令牌'])
    expect(wrapper.find('.auth-badge').text()).toBe('JWT 令牌')
  })

  it('一机一密产品不展示任何签发动作', () => {
    const wrapper = mountPanel({ authMode: 'SECRET' })

    expect(buttonLabels(wrapper)).toEqual([])
    expect(wrapper.text()).toContain('一机一密')
  })

  it('认证方式未就绪时给出等待文案', () => {
    const wrapper = mountPanel({ authMode: '' })

    expect(wrapper.text()).toContain('正在获取产品认证方式')
  })

  it('无管理权限时动作置灰并提示', () => {
    const wrapper = mountPanel({ authMode: 'X509', canWrite: false })

    expect(wrapper.findAll('.stub-button').every((item) => item.attributes('disabled') !== undefined)).toBe(true)
    expect(wrapper.text()).toContain('仅管理员可签发或吊销设备凭据')
  })

  it('点击签发与吊销分别抛出事件', async () => {
    const wrapper = mountPanel({ authMode: 'X509' })

    await wrapper.findAll('.stub-button').at(0).trigger('click')
    await wrapper.findAll('.stub-button').at(1).trigger('click')

    expect(wrapper.emitted('issue-certificate')).toHaveLength(1)
    expect(wrapper.emitted('revoke-certificate')).toHaveLength(1)
  })

  it('含吊销结果时展示序列号与原因', () => {
    const wrapper = mountPanel({
      authMode: 'X509',
      revoked: { serial: 'A1B2C3D4E5F60718', revokedAt: '2026-10-06T10:00:00', reason: '固件重刷' }
    })

    expect(wrapper.text()).toContain('A1B2C3D4E5F60718')
    expect(wrapper.text()).toContain('固件重刷')
  })
})
