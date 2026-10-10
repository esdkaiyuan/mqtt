import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

const { messageMock, confirmMock } = vi.hoisted(() => ({
  messageMock: { success: vi.fn(), warning: vi.fn(), info: vi.fn(), error: vi.fn() },
  confirmMock: vi.fn(() => Promise.resolve())
}))

vi.mock('element-plus', () => ({
  ElMessage: messageMock,
  ElMessageBox: { confirm: confirmMock }
}))

const hooks = vi.hoisted(() => ({
  channels: null,
  loading: null,
  saving: null,
  createChannel: null,
  updateChannel: null,
  deleteChannel: null,
  testChannel: null
}))

vi.mock('@/composables/useNotificationChannels', async () => {
  const { ref } = await import('vue')
  hooks.channels = ref([])
  hooks.loading = ref(false)
  hooks.saving = ref(false)
  hooks.createChannel = vi.fn(() => Promise.resolve())
  hooks.updateChannel = vi.fn(() => Promise.resolve())
  hooks.deleteChannel = vi.fn(() => Promise.resolve())
  hooks.testChannel = vi.fn(() => Promise.resolve())
  return {
    useNotificationChannelsQuery: () => ({ channels: hooks.channels, loading: hooks.loading }),
    useNotificationChannelMutations: () => ({
      saving: hooks.saving,
      createChannel: hooks.createChannel,
      updateChannel: hooks.updateChannel,
      deleteChannel: hooks.deleteChannel,
      testChannel: hooks.testChannel
    })
  }
})

import NotificationChannels from '../NotificationChannels.vue'

const EMAIL_CHANNEL = {
  id: 1,
  channelType: 'EMAIL',
  name: '值班组邮箱',
  events: '["alert.triggered","alert.recovered"]',
  config: '{"host":"smtp.example.com","port":465,"from":"alert@example.com","to":"ops@example.com"}',
  isActive: 1,
  failureCount: 0,
  lastTriggeredAt: null
}

const SMS_CHANNEL = {
  id: 2,
  channelType: 'SMS',
  name: '短信网关',
  events: '["alert.triggered"]',
  config: '{"url":"https://sms.example.com/send","method":"POST","phones":"13800000000"}',
  isActive: 0,
  failureCount: 2,
  lastTriggeredAt: '2026-10-07T09:00:00.000'
}

/* ---------- Element Plus 最小替身 ---------- */
const ElButtonStub = {
  name: 'ElButton',
  emits: ['click'],
  setup(_, { slots, emit }) {
    return () => h('button', { class: 'stub-btn', onClick: () => emit('click') }, slots.default?.())
  }
}

const ElDialogStub = {
  name: 'ElDialog',
  props: ['modelValue', 'title'],
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('div', { class: 'stub-dialog' }, [
            h('div', { class: 'stub-dialog-title' }, props.title),
            slots.default?.(),
            slots.footer?.()
          ])
        : null
  }
}

const ElSelectStub = {
  name: 'ElSelect',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  setup(_, { slots }) {
    return () => h('div', { class: 'stub-select' }, slots.default?.())
  }
}

const ElFormItemStub = {
  name: 'ElFormItem',
  props: ['label'],
  setup(props, { slots }) {
    return () =>
      h('div', { class: 'stub-form-item' }, [
        h('span', { class: 'stub-form-label' }, props.label),
        slots.default?.()
      ])
  }
}

const passthrough = (name) => ({
  name,
  setup(_, { slots }) {
    return () => h('div', { class: `stub-${name}` }, slots.default?.())
  }
})

const PageHeaderStub = {
  name: 'PageHeader',
  props: ['title', 'desc'],
  setup(props, { slots }) {
    return () =>
      h('div', { class: 'stub-page-header' }, [
        h('span', { class: 'stub-page-title' }, props.title),
        slots.actions?.()
      ])
  }
}

const EmptyStateStub = {
  name: 'EmptyState',
  props: ['description', 'imageSize'],
  template: '<div class="stub-empty">{{ description }}</div>'
}

const STUBS = {
  ElButton: ElButtonStub,
  ElDialog: ElDialogStub,
  ElSelect: ElSelectStub,
  ElOption: passthrough('ElOption'),
  ElForm: passthrough('ElForm'),
  ElFormItem: ElFormItemStub,
  ElInput: passthrough('ElInput'),
  ElInputNumber: passthrough('ElInputNumber'),
  ElSwitch: passthrough('ElSwitch'),
  ElTag: passthrough('ElTag'),
  PageHeader: PageHeaderStub,
  EmptyState: EmptyStateStub
}

const findByText = (wrapper, tag, text) =>
  wrapper.findAll(tag).find((node) => node.text().includes(text))

describe('views/workbench/access/NotificationChannels', () => {
  let wrapper

  async function mountPage() {
    wrapper = mount(NotificationChannels, { global: { stubs: STUBS } })
    await flushPromises()
    return wrapper
  }

  beforeEach(() => {
    hooks.channels.value = []
    confirmMock.mockResolvedValue(undefined)
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  it('无渠道时渲染空态提示', async () => {
    await mountPage()

    expect(wrapper.find('.stub-empty').exists()).toBe(true)
    expect(wrapper.find('.stub-empty').text()).toContain('暂无通知渠道')
  })

  it('渲染渠道类型、订阅事件与收件人', async () => {
    hooks.channels.value = [EMAIL_CHANNEL, SMS_CHANNEL]
    await mountPage()

    const text = wrapper.text()
    expect(text).toContain('值班组邮箱')
    expect(text).toContain('邮件')
    expect(text).toContain('告警触发、告警恢复')
    expect(text).toContain('ops@example.com')

    expect(text).toContain('短信网关')
    expect(text).toContain('短信')
    expect(text).toContain('13800000000')
    expect(text).toContain('已停用')
  })

  it('新建渠道时按类型切换表单字段', async () => {
    await mountPage()

    await findByText(wrapper, 'button', '新建渠道').trigger('click')
    await nextTick()

    const dialog = wrapper.find('.stub-dialog')
    expect(dialog.exists()).toBe(true)
    expect(dialog.text()).toContain('SMTP 地址')

    const channelTypeSelect = wrapper.findAllComponents({ name: 'ElSelect' })[0]
    channelTypeSelect.vm.$emit('update:modelValue', 'SMS')
    await nextTick()

    const switched = wrapper.find('.stub-dialog').text()
    expect(switched).toContain('短信网关地址')
    expect(switched).not.toContain('SMTP 地址')
  })

  it('点击「测试发送」调用连通性测试接口', async () => {
    hooks.channels.value = [EMAIL_CHANNEL]
    await mountPage()

    await findByText(wrapper, 'button', '测试发送').trigger('click')
    await flushPromises()

    expect(hooks.testChannel).toHaveBeenCalledWith(1)
    expect(messageMock.success).toHaveBeenCalled()
  })
})