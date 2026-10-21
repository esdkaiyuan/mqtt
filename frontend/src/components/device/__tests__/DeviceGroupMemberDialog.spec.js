import { beforeEach, describe, expect, it, vi } from 'vitest'
import { h, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  message: { success: vi.fn(), warning: vi.fn(), info: vi.fn(), error: vi.fn() },
  confirm: vi.fn(() => Promise.resolve()),
  listMembers: vi.fn(),
  addMember: vi.fn(),
  updateMember: vi.fn(),
  removeMember: vi.fn()
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.message,
  ElMessageBox: { confirm: mocks.confirm }
}))

vi.mock('@/api/deviceGroup', () => ({
  deviceGroupApi: {
    listMembers: mocks.listMembers,
    addMember: mocks.addMember,
    updateMember: mocks.updateMember,
    removeMember: mocks.removeMember
  }
}))

import DeviceGroupMemberDialog from '../DeviceGroupMemberDialog.vue'

const ElDialogStub = {
  name: 'ElDialog',
  props: ['modelValue', 'title'],
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('div', { class: 'stub-dialog' }, [
            h('span', { class: 'stub-dialog__title' }, props.title),
            slots.default?.(),
            slots.footer?.()
          ])
        : null
  }
}
const ElInputStub = {
  name: 'ElInput',
  props: ['modelValue', 'placeholder'],
  emits: ['update:modelValue'],
  template:
    '<input class="stub-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
}
const ElSelectStub = {
  name: 'ElSelect',
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue', 'change'],
  setup(_, { slots }) {
    return () => h('div', { class: 'stub-select' }, slots.default?.())
  }
}
const ElOptionStub = {
  name: 'ElOption',
  props: ['label', 'value'],
  template: '<span class="stub-option" :data-value="value">{{ label }}</span>'
}
const ElButtonStub = {
  name: 'ElButton',
  props: ['type', 'link', 'loading', 'disabled'],
  emits: ['click'],
  template: '<button class="stub-button" @click="$emit(\'click\')"><slot /></button>'
}

const STUBS = {
  ElDialog: ElDialogStub,
  ElInput: ElInputStub,
  ElSelect: ElSelectStub,
  ElOption: ElOptionStub,
  ElButton: ElButtonStub
}

const GROUP = { id: 3, name: '华东厂区' }

function toResult(data) {
  return { code: 200, message: 'ok', data }
}

function mountDialog() {
  return mount(DeviceGroupMemberDialog, {
    props: { modelValue: true, group: GROUP },
    global: { stubs: STUBS }
  })
}

describe('components/device/DeviceGroupMemberDialog', () => {
  beforeEach(() => {
    mocks.listMembers.mockReset().mockResolvedValue(toResult([
      { id: 9, userId: 20, username: 'member', email: 'member@example.com', permission: 'VIEW' }
    ]))
    mocks.addMember.mockReset().mockResolvedValue(toResult({ id: 10 }))
    mocks.updateMember.mockReset().mockResolvedValue(toResult({ id: 9 }))
    mocks.removeMember.mockReset().mockResolvedValue(toResult(null))
    mocks.confirm.mockReset().mockResolvedValue(undefined)
    mocks.message.success.mockClear()
    mocks.message.warning.mockClear()
  })

  it('打开后加载并渲染共享成员', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    expect(mocks.listMembers).toHaveBeenCalledWith(3)
    expect(wrapper.text()).toContain('member')
    expect(wrapper.text()).toContain('member@example.com')
  })

  it('添加成员后刷新列表', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    await wrapper.find('.stub-input').setValue('new-user')
    const addButton = wrapper.findAll('.stub-button').find((button) => button.text().includes('添加'))
    await addButton.trigger('click')
    await flushPromises()

    expect(mocks.addMember).toHaveBeenCalledWith(3, { username: 'new-user', permission: 'VIEW' })
    expect(mocks.listMembers).toHaveBeenCalledTimes(2)
    expect(mocks.message.success).toHaveBeenCalledWith('成员已添加')
  })

  it('修改成员权限调用更新接口', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    const selects = wrapper.findAllComponents({ name: 'ElSelect' })
    // 第 0 个是新增权限，第 1 个是成员权限
    selects[1].vm.$emit('change', 'OPERATE')
    await nextTick()
    await flushPromises()

    expect(mocks.updateMember).toHaveBeenCalledWith(3, 9, { permission: 'OPERATE' })
  })

  it('移除成员需确认后调用删除接口', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    const removeButton = wrapper.findAll('.stub-button').find((button) => button.text().includes('移除'))
    await removeButton.trigger('click')
    await flushPromises()

    expect(mocks.confirm).toHaveBeenCalled()
    expect(mocks.removeMember).toHaveBeenCalledWith(3, 9)
  })
})
