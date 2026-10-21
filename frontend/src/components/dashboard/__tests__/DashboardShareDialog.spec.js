import { beforeEach, describe, expect, it, vi } from 'vitest'
import { h, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  message: { success: vi.fn(), warning: vi.fn(), info: vi.fn(), error: vi.fn() },
  confirm: vi.fn(() => Promise.resolve())
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.message,
  ElMessageBox: { confirm: mocks.confirm }
}))

const hooks = vi.hoisted(() => ({
  shares: null,
  loading: null,
  saving: null,
  create: null,
  update: null,
  remove: null,
  rotate: null
}))

vi.mock('@/composables/useDashboardShares', async () => {
  const { ref: rf } = await import('vue')
  hooks.shares = rf([])
  hooks.loading = rf(false)
  hooks.saving = rf(false)
  hooks.create = vi.fn(() => Promise.resolve({ path: '/display/new-token', token: 'new-token' }))
  hooks.update = vi.fn(() => Promise.resolve({ id: 7 }))
  hooks.remove = vi.fn(() => Promise.resolve())
  hooks.rotate = vi.fn(() => Promise.resolve({ path: '/display/rotated', token: 'rotated' }))
  return {
    useDashboardShares: () => ({
      shares: hooks.shares,
      loading: hooks.loading,
      saving: hooks.saving,
      create: hooks.create,
      update: hooks.update,
      remove: hooks.remove,
      rotate: hooks.rotate
    })
  }
})

import DashboardShareDialog from '../DashboardShareDialog.vue'

const ElDialogStub = {
  name: 'ElDialog',
  props: ['modelValue', 'title'],
  emits: ['update:modelValue'],
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('div', { class: 'stub-dialog' }, [h('span', { class: 'stub-dialog__title' }, props.title), slots.default?.()])
        : null
  }
}
const passthrough = (name) => ({
  name,
  setup(_, { slots }) {
    return () => h('div', { class: `stub-${name}` }, slots.default?.())
  }
})
const ElInputStub = {
  name: 'ElInput',
  props: ['modelValue', 'placeholder', 'readonly'],
  emits: ['update:modelValue'],
  template:
    '<input class="stub-input" :value="modelValue" :readonly="readonly" @input="$emit(\'update:modelValue\', $event.target.value)" />'
}
const ElInputNumberStub = {
  name: 'ElInputNumber',
  props: ['modelValue', 'min', 'max'],
  emits: ['update:modelValue'],
  template: '<input class="stub-number" :value="modelValue" />'
}
const ElSelectStub = {
  name: 'ElSelect',
  props: ['modelValue', 'multiple', 'filterable', 'placeholder'],
  emits: ['update:modelValue'],
  setup(_, { slots }) {
    return () => h('div', { class: 'stub-select' }, slots.default?.())
  }
}
const ElOptionStub = {
  name: 'ElOption',
  props: ['label', 'value'],
  template: '<div class="stub-option">{{ label }}</div>'
}
const ElSwitchStub = {
  name: 'ElSwitch',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<button class="stub-switch" @click="$emit(\'update:modelValue\', !modelValue)" />'
}
const ElButtonStub = {
  name: 'ElButton',
  props: ['type', 'size', 'link', 'loading', 'disabled'],
  emits: ['click'],
  template: '<button class="stub-button" @click="$emit(\'click\')"><slot /></button>'
}
const ElTagStub = {
  name: 'ElTag',
  props: ['type', 'size'],
  template: '<span class="stub-tag"><slot /></span>'
}

const STUBS = {
  ElDialog: ElDialogStub,
  ElForm: passthrough('ElForm'),
  ElFormItem: passthrough('ElFormItem'),
  ElInput: ElInputStub,
  ElInputNumber: ElInputNumberStub,
  ElSelect: ElSelectStub,
  ElOption: ElOptionStub,
  ElSwitch: ElSwitchStub,
  ElButton: ElButtonStub,
  ElTag: ElTagStub,
  SvgIcon: true
}

function buttonByText(wrapper, text) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

async function mountDialog(props = {}) {
  const wrapper = mount(DashboardShareDialog, {
    props: { modelValue: false, dashboards: [], presetDashboardId: null, ...props },
    global: { stubs: STUBS }
  })
  await wrapper.setProps({ modelValue: true })
  await nextTick()
  return wrapper
}

describe('components/dashboard/DashboardShareDialog', () => {
  beforeEach(() => {
    hooks.shares.value = []
    hooks.loading.value = false
    hooks.saving.value = false
    hooks.create.mockClear()
    hooks.update.mockClear()
    hooks.remove.mockClear()
    hooks.rotate.mockClear()
    mocks.message.warning.mockClear()
    mocks.message.success.mockClear()
    mocks.confirm.mockReset().mockResolvedValue(undefined)
  })

  it('预选看板并提交创建分享', async () => {
    const wrapper = await mountDialog({
      dashboards: [{ id: 3, name: '机房' }],
      presetDashboardId: 3
    })

    await buttonByText(wrapper, '创建分享').trigger('click')
    await flushPromises()

    expect(hooks.create).toHaveBeenCalledWith({
      name: '机房 大屏',
      dashboardIds: [3],
      intervalSeconds: 15,
      enabled: true
    })
    expect(wrapper.find('.share-link input').element.value).toContain('/display/new-token')
  })

  it('名称为空时提示且不提交', async () => {
    const wrapper = await mountDialog()

    await buttonByText(wrapper, '创建分享').trigger('click')
    await flushPromises()

    expect(mocks.message.warning).toHaveBeenCalledWith('请填写分享名称')
    expect(hooks.create).not.toHaveBeenCalled()
  })

  it('轮换令牌后展示新链接', async () => {
    hooks.shares.value = [{
      id: 7,
      name: '产线大屏',
      dashboardIds: [3],
      intervalSeconds: 20,
      enabled: true,
      tokenPreview: 'abcd1234'
    }]
    const wrapper = await mountDialog()

    await buttonByText(wrapper, '轮换令牌').trigger('click')
    await flushPromises()

    expect(mocks.confirm).toHaveBeenCalled()
    expect(hooks.rotate).toHaveBeenCalledWith(7)
    expect(wrapper.find('.share-link input').element.value).toContain('/display/rotated')
  })
})
