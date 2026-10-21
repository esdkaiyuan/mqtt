import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, h, inject, provide } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

const { messageMock, confirmMock, listVersionsMock, restoreVersionMock } = vi.hoisted(() => ({
  messageMock: { success: vi.fn(), warning: vi.fn(), info: vi.fn(), error: vi.fn() },
  confirmMock: vi.fn(() => Promise.resolve()),
  listVersionsMock: vi.fn(),
  restoreVersionMock: vi.fn()
}))

vi.mock('element-plus', () => ({
  ElMessage: messageMock,
  ElMessageBox: { confirm: confirmMock }
}))

vi.mock('@/api/scene', () => ({
  sceneApi: {
    listSceneVersions: listVersionsMock,
    restoreSceneVersion: restoreVersionMock
  }
}))

import SceneVersionDialog from '../SceneVersionDialog.vue'

const ROW_KEY = Symbol('row')
const RowProvider = {
  name: 'RowProvider',
  props: ['row', 'index'],
  setup(props, { slots }) {
    provide(ROW_KEY, computed(() => ({ row: props.row, index: props.index })))
    return () => slots.default?.({ row: props.row, $index: props.index })
  }
}
const ElTableStub = {
  name: 'ElTable',
  props: ['data'],
  setup(props, { slots }) {
    return () => h('div', { class: 'stub-table' }, (props.data || []).map((row, index) =>
      h(RowProvider, { row, index, key: index }, { default: (scope) => slots.default?.(scope) })
    ))
  }
}
const ElTableColumnStub = {
  name: 'ElTableColumn',
  props: ['label', 'prop'],
  setup(props, { slots }) {
    const context = inject(ROW_KEY, null)
    return () => h('div', { class: 'stub-cell' },
      slots.default
        ? slots.default({ row: context?.value?.row, $index: context?.value?.index })
        : (context?.value?.row?.[props.prop] ?? ''))
  }
}
const ElTagStub = {
  name: 'ElTag',
  props: ['type', 'size', 'effect'],
  template: '<span class="stub-tag"><slot /></span>'
}
const ElButtonStub = {
  name: 'ElButton',
  props: ['disabled', 'link', 'type'],
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
}
const ElDialogStub = {
  name: 'ElDialog',
  props: ['modelValue', 'title', 'width'],
  emits: ['update:modelValue'],
  template: '<div v-if="modelValue" class="stub-dialog"><slot /><slot name="footer" /></div>'
}
const EmptyStateStub = {
  name: 'EmptyState',
  props: ['description'],
  template: '<div class="stub-empty">{{ description }}</div>'
}

const STUBS = {
  ElDialog: ElDialogStub,
  ElTable: ElTableStub,
  ElTableColumn: ElTableColumnStub,
  ElTag: ElTagStub,
  ElButton: ElButtonStub,
  EmptyState: EmptyStateStub
}

const SCENE = { id: 5, name: '高温联动' }
const VERSIONS = [
  { id: 72, versionNo: 3, changeType: 'RESTORE', sceneName: '高温联动', triggerType: 'PROPERTY', stepCount: 2, createdAt: '2026-10-22T10:00:00' },
  { id: 71, versionNo: 2, changeType: 'UPDATE', sceneName: '高温联动', triggerType: 'PROPERTY', stepCount: 1, createdAt: '2026-10-22T09:00:00' },
  { id: 70, versionNo: 1, changeType: 'CREATE', sceneName: '高温联动', triggerType: 'PROPERTY', stepCount: 1, createdAt: '2026-10-22T08:00:00' }
]

function mountDialog() {
  return mount(SceneVersionDialog, {
    props: { modelValue: true, scene: SCENE },
    global: { stubs: STUBS }
  })
}

describe('components/scene/SceneVersionDialog', () => {
  beforeEach(() => {
    listVersionsMock.mockResolvedValue({ code: 200, data: VERSIONS })
    restoreVersionMock.mockResolvedValue({ code: 200, data: { id: 5 } })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('打开后加载版本并标记当前版本', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    expect(listVersionsMock).toHaveBeenCalledWith(5)
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('title')).toContain('版本历史')
    expect(wrapper.text()).toContain('#3')
    expect(wrapper.text()).toContain('当前')
    expect(wrapper.text()).toContain('创建')
    expect(wrapper.text()).toContain('保存')
    expect(wrapper.text()).toContain('回滚')
    wrapper.unmount()
  })

  it('当前版本不可回滚，历史版本回滚后刷新并通知外部', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    const restoreButtons = wrapper.findAll('button').filter((button) => button.text().includes('回滚'))
    expect(restoreButtons).toHaveLength(3)
    expect(restoreButtons[0].attributes('disabled')).toBeDefined()
    expect(restoreButtons[1].attributes('disabled')).toBeUndefined()

    await restoreButtons[1].trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalled()
    expect(restoreVersionMock).toHaveBeenCalledWith(5, 71)
    expect(messageMock.success).toHaveBeenCalledWith('已回滚到版本 #2')
    expect(listVersionsMock).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('restored')).toHaveLength(1)
    wrapper.unmount()
  })

  it('无版本时展示空态', async () => {
    listVersionsMock.mockResolvedValue({ code: 200, data: [] })
    const wrapper = mountDialog()
    await flushPromises()

    expect(wrapper.find('.stub-empty').text()).toContain('暂无版本记录')
    wrapper.unmount()
  })
})
