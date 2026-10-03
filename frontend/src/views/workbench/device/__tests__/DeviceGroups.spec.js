import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, h, inject, provide } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

// 共享响应式状态在 hoisted 容器中占位，ref 在 mock 工厂内按需创建，
// 避免 vi.mock 提升后访问尚未初始化的顶层 const。
const shared = vi.hoisted(() => ({
  tree: null,
  tags: null,
  refreshTree: vi.fn(),
  refreshTags: vi.fn()
}))

vi.mock('@/composables/useDeviceGroups', async () => {
  const { ref } = await import('vue')
  shared.tree = ref([])
  shared.tags = ref([])
  return {
    useDeviceGroupTreeQuery: () => ({ tree: shared.tree, loading: ref(false), refresh: shared.refreshTree }),
    useDeviceGroupMutations: () => ({
      saving: ref(false),
      createGroup: vi.fn(),
      updateGroup: vi.fn(),
      deleteGroup: vi.fn()
    }),
    useTagListQuery: () => ({ tags: shared.tags, loading: ref(false), refresh: shared.refreshTags }),
    useTagMutations: () => ({
      saving: ref(false),
      createTag: vi.fn(),
      updateTag: vi.fn(),
      deleteTag: vi.fn()
    })
  }
})

vi.mock('@/composables/useDeviceBatch', async () => {
  const { ref } = await import('vue')
  return {
    useDeviceBatch: () => ({
      running: ref(false),
      result: ref(null),
      resultVisible: ref(false),
      closeResult: vi.fn(),
      sendBatchCommands: vi.fn(),
      batchEnable: vi.fn(),
      batchDisable: vi.fn()
    })
  }
})

vi.mock('@/api/deviceGroup', () => ({
  deviceGroupApi: {
    pageDevices: vi.fn(),
    removeDevices: vi.fn()
  }
}))

import { deviceGroupApi } from '@/api/deviceGroup'
import DeviceGroups from '../DeviceGroups.vue'

const tree = shared.tree
const tags = shared.tags
const refreshTree = shared.refreshTree
const refreshTags = shared.refreshTags

const ROW_KEY = Symbol('table-row')

const RowProvider = {
  name: 'RowProvider',
  props: ['row'],
  setup(props, { slots }) {
    provide(
      ROW_KEY,
      computed(() => props.row)
    )
    return () => h('div', { class: 'stub-row' }, slots.default?.())
  }
}

const ElTableStub = {
  name: 'ElTable',
  props: ['data'],
  setup(props, { slots }) {
    return () => {
      if (!props.data || props.data.length === 0) {
        return h('div', { class: 'stub-table' }, slots.empty ? [slots.empty()] : [])
      }
      return h(
        'div',
        { class: 'stub-table' },
        props.data.map((row, index) =>
          h(RowProvider, { row, key: index }, { default: () => slots.default?.() })
        )
      )
    }
  }
}

const ElTableColumnStub = {
  name: 'ElTableColumn',
  props: ['label', 'prop'],
  setup(props, { slots }) {
    const row = inject(ROW_KEY, null)
    return () => {
      if (slots.default) {
        return h('div', { class: 'stub-cell' }, slots.default({ row: row?.value ?? null }))
      }
      return h('div', { class: 'stub-cell' }, props.prop ? String(row?.value?.[props.prop] ?? '') : props.label)
    }
  }
}

const ElTreeStub = {
  name: 'ElTree',
  props: ['data'],
  emits: ['node-click'],
  setup(props, { slots, emit }) {
    return () => {
      if (!props.data || props.data.length === 0) return null
      return h(
        'div',
        { class: 'stub-tree' },
        props.data.map((node) =>
          h(
            'div',
            {
              class: 'stub-tree-node',
              onClick: () => emit('node-click', node)
            },
            slots.default?.({ data: node })
          )
        )
      )
    }
  }
}

const ElButtonStub = {
  name: 'ElButton',
  props: ['type', 'link', 'size', 'disabled', 'loading'],
  emits: ['click'],
  template: '<button class="stub-button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
}

const ElTagStub = {
  name: 'ElTag',
  props: ['type', 'size', 'effect'],
  template: '<span class="stub-tag"><slot /></span>'
}

const ElDialogStub = {
  name: 'ElDialog',
  props: ['modelValue', 'title'],
  setup(props, { slots }) {
    return () => (props.modelValue ? h('div', { class: 'stub-dialog' }, slots.default?.()) : null)
  }
}

const ElFormStub = { name: 'ElForm', template: '<form><slot /></form>' }
const ElFormItemStub = { name: 'ElFormItem', props: ['label'], template: '<div><slot /></div>' }
const ElSelectStub = {
  name: 'ElSelect',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div class="stub-select"><slot /></div>'
}
const ElOptionStub = { name: 'ElOption', props: ['label', 'value'], template: '<span />' }
const ElInputStub = {
  name: 'ElInput',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<input class="stub-input" :value="modelValue" />'
}
const ElColorPickerStub = {
  name: 'ElColorPicker',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<span class="stub-color-picker" />'
}
const ElPaginationStub = { name: 'ElPagination', template: '<div class="stub-pagination" />' }

const PageHeaderStub = {
  name: 'PageHeader',
  props: ['title', 'desc'],
  template: '<div class="stub-page-header"><slot name="actions" /></div>'
}

const EmptyStateStub = {
  name: 'EmptyState',
  props: ['description'],
  template: '<div class="stub-empty">{{ description }}</div>'
}

const STUBS = {
  ElTable: ElTableStub,
  ElTableColumn: ElTableColumnStub,
  ElTree: ElTreeStub,
  ElButton: ElButtonStub,
  ElTag: ElTagStub,
  ElDialog: ElDialogStub,
  ElForm: ElFormStub,
  ElFormItem: ElFormItemStub,
  ElSelect: ElSelectStub,
  ElOption: ElOptionStub,
  ElInput: ElInputStub,
  ElColorPicker: ElColorPickerStub,
  ElPagination: ElPaginationStub,
  PageHeader: PageHeaderStub,
  EmptyState: EmptyStateStub,
  BatchCommandDialog: true,
  BatchResultDrawer: true
}

const TREE = [
  { id: 1, name: '华东厂区', description: '总部', deviceCount: 2, sortOrder: 0, parentId: null },
  { id: 2, name: '一号车间', description: '', deviceCount: 0, sortOrder: 1, parentId: null }
]

function mountPage() {
  return mount(DeviceGroups, {
    global: { stubs: STUBS, directives: { loading: {} } }
  })
}

describe('views/workbench/device/DeviceGroups', () => {
  beforeEach(() => {
    tree.value = TREE
    tags.value = [{ id: 9, name: '重点设备', color: '#f56c6c' }]
    refreshTree.mockClear()
    refreshTags.mockClear()
    deviceGroupApi.pageDevices.mockResolvedValue({
      code: 200,
      data: { records: [{ id: 'd-1', deviceName: '温湿度传感器', deviceKey: 'sensor-1', status: 'ONLINE', tags: [] }], total: 1 }
    })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('挂载时拉取分组树与标签列表', () => {
    mountPage()

    expect(refreshTree).toHaveBeenCalledTimes(1)
    expect(refreshTags).toHaveBeenCalledTimes(1)
  })

  it('渲染分组树节点名称与设备数', () => {
    const wrapper = mountPage()
    const nodes = wrapper.findAll('.stub-tree-node')

    expect(nodes).toHaveLength(2)
    expect(nodes[0].text()).toContain('华东厂区')
    expect(nodes[0].text()).toContain('2')
    expect(nodes[1].text()).toContain('一号车间')
  })

  it('无分组时展示空态提示', () => {
    tree.value = []
    const wrapper = mountPage()

    expect(wrapper.find('.stub-tree').exists()).toBe(false)
    expect(wrapper.text()).toContain('暂无分组，点击「新建根分组」开始')
  })

  it('点击节点按分组分页加载设备并渲染明细', async () => {
    const wrapper = mountPage()

    await wrapper.findAll('.stub-tree-node')[0].trigger('click')
    await flushPromises()

    expect(deviceGroupApi.pageDevices).toHaveBeenCalledWith(1, { page: 1, size: 10 })
    expect(wrapper.text()).toContain('温湿度传感器')
    expect(wrapper.text()).toContain('sensor-1')
  })

  it('未选择分组时提示从左侧选择', () => {
    const wrapper = mountPage()

    expect(wrapper.text()).toContain('从左侧选择分组查看其设备')
  })

  it('选中含设备的分组后批量操作可用', async () => {
    const wrapper = mountPage()

    await wrapper.findAll('.stub-tree-node')[0].trigger('click')
    await flushPromises()

    const commandButton = wrapper
      .findAll('.stub-button')
      .find((button) => button.text().includes('批量下发命令'))
    expect(commandButton).toBeTruthy()
    expect(commandButton.attributes('disabled')).toBeUndefined()
  })

  it('渲染标签列表并带展示色', () => {
    const wrapper = mountPage()

    expect(wrapper.find('.tag-item__name').text()).toBe('重点设备')
    expect(wrapper.find('.tag-item__dot').attributes('style')).toContain('rgb(245, 108, 108)')
  })

  it('无标签时展示空态提示', () => {
    tags.value = []
    const wrapper = mountPage()

    expect(wrapper.text()).toContain('暂无标签，点击「新建标签」开始')
  })
})