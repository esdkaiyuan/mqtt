import { describe, expect, it } from 'vitest'
import { computed, h, inject, provide } from 'vue'
import { mount } from '@vue/test-utils'
import DeviceCommandHistory from '../DeviceCommandHistory.vue'

/** 最小表格替身：按行渲染列插槽，等价于 el-table 的 `row` 作用域插槽。 */
const ROW_KEY = Symbol('table-row')

const RowProvider = {
  name: 'RowProvider',
  props: ['row'],
  setup(props, { slots }) {
    provide(
      ROW_KEY,
      computed(() => props.row)
    )
    return () => slots.default?.()
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
      return h('div', { class: 'stub-cell' }, props.prop ? String(row?.value?.[props.prop] ?? '') : '')
    }
  }
}

const ElTagStub = {
  name: 'ElTag',
  props: ['type', 'size'],
  template: '<span class="stub-tag" :data-type="type"><slot /></span>'
}

const ElButtonStub = {
  name: 'ElButton',
  props: ['link', 'type', 'size'],
  emits: ['click'],
  template: '<button class="stub-button" @click="$emit(\'click\')"><slot /></button>'
}

const ElPaginationStub = { name: 'ElPagination', template: '<div class="stub-pagination" />' }

const ElDialogStub = {
  name: 'ElDialog',
  props: ['modelValue', 'title'],
  setup(props, { slots }) {
    return () => (props.modelValue ? h('div', { class: 'stub-dialog' }, slots.default?.()) : null)
  }
}

const EmptyStateStub = {
  name: 'EmptyState',
  props: ['description', 'imageSize'],
  template: '<div class="stub-empty">{{ description }}</div>'
}

const STUBS = {
  ElTable: ElTableStub,
  ElTableColumn: ElTableColumnStub,
  ElTag: ElTagStub,
  ElButton: ElButtonStub,
  ElPagination: ElPaginationStub,
  ElDialog: ElDialogStub,
  EmptyState: EmptyStateStub
}

const ROWS = [
  {
    commandId: 'cmd-1',
    commandType: 'property_set',
    identifier: null,
    status: 'ACKED',
    callType: 'sync',
    params: '{"targetTemp":26}',
    result: '{"ok":true}',
    errorMessage: null,
    createdAt: '2026-10-02T10:00:00.000',
    finishedAt: '2026-10-02T10:00:01.500'
  },
  {
    commandId: 'cmd-2',
    commandType: 'service',
    identifier: 'reboot',
    status: 'TIMEOUT',
    callType: 'async',
    params: '{"delay":3}',
    result: null,
    errorMessage: '等待回执超时',
    createdAt: '2026-10-02T10:01:00.000',
    finishedAt: null
  }
]

function mountPanel(props) {
  return mount(DeviceCommandHistory, {
    props,
    global: { stubs: STUBS, directives: { loading: {} } }
  })
}

describe('components/device/DeviceCommandHistory', () => {
  it('空态渲染占位提示', () => {
    const wrapper = mountPanel({ records: [] })

    expect(wrapper.find('.stub-empty').text()).toContain('暂无命令记录')
    expect(wrapper.findAll('.stub-cell')).toHaveLength(0)
  })

  it('逐行渲染类型文案、标识符与耗时', () => {
    const wrapper = mountPanel({ records: ROWS, total: 2 })
    const text = wrapper.text()

    expect(text).toContain('属性设置')
    expect(text).toContain('服务调用')
    expect(text).toContain('reboot')
    expect(text).toContain('1.5 s')
    expect(text).toContain('—')
  })

  it('状态按语义着色', () => {
    const wrapper = mountPanel({ records: ROWS, total: 2 })
    const tags = wrapper.findAll('.stub-tag')

    expect(tags[0].attributes('data-type')).toBe('success')
    expect(tags[0].text()).toBe('ACKED')
    expect(tags[1].attributes('data-type')).toBe('warning')
    expect(tags[1].text()).toBe('TIMEOUT')
  })

  it('QUEUED 状态标注待补发提示', () => {
    const wrapper = mountPanel({ records: [{ ...ROWS[0], status: 'QUEUED' }], total: 1 })

    expect(wrapper.find('.stub-tag').attributes('data-type')).toBe('warning')
    expect(wrapper.find('.stub-tag').text()).toBe('QUEUED')
    expect(wrapper.find('.device-command-history__hint').text()).toBe('待补发')
  })

  it('毫秒级耗时按 ms 展示', () => {
    const wrapper = mountPanel({
      records: [{ ...ROWS[0], finishedAt: '2026-10-02T10:00:00.250' }],
      total: 1
    })

    expect(wrapper.text()).toContain('250 ms')
  })

  it('点击详情弹出美化后的参数与结果', async () => {
    const wrapper = mountPanel({ records: ROWS, total: 2 })
    expect(wrapper.find('.stub-dialog').exists()).toBe(false)

    await wrapper.findAllComponents({ name: 'ElButton' })[0].trigger('click')

    const dialog = wrapper.find('.stub-dialog')
    expect(dialog.exists()).toBe(true)
    expect(dialog.text()).toContain('cmd-1')
    expect(dialog.text()).toContain('"targetTemp": 26')
    expect(dialog.text()).toContain('"ok": true')
  })
})