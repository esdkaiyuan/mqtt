import { describe, expect, it } from 'vitest'
import { computed, h, inject, provide } from 'vue'
import { mount } from '@vue/test-utils'
import DevicePropertyPanel from '../DevicePropertyPanel.vue'

/**
 * 最小表格替身：按行渲染列插槽，避免为展示型用例拉起整套 Element Plus 表格。
 * 通过 provide/inject 把当前行传给列，等价于 el-table 的 `row` 作用域插槽。
 */
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
      return h(
        'div',
        { class: 'stub-cell' },
        props.prop ? String(row?.value?.[props.prop] ?? '') : ''
      )
    }
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
  EmptyState: EmptyStateStub
}

function mountPanel(props) {
  return mount(DevicePropertyPanel, {
    props,
    global: { stubs: STUBS, directives: { loading: {} } }
  })
}

describe('components/device/DevicePropertyPanel', () => {
  it('空态渲染占位提示', () => {
    const wrapper = mountPanel({ properties: [] })

    expect(wrapper.find('.stub-empty').exists()).toBe(true)
    expect(wrapper.find('.stub-empty').text()).toContain('暂无属性数据')
    expect(wrapper.findAll('.stub-cell')).toHaveLength(0)
  })

  it('有数据时逐行渲染标识符、数据类型与当前值', () => {
    const wrapper = mountPanel({
      properties: [
        { identifier: 'temperature', dataType: 'float', valueText: '26.5', reportedAt: '2026-10-02T10:00:00' },
        { identifier: 'humidity', dataType: 'int', valueText: '58', reportedAt: '2026-10-02T10:01:00' }
      ]
    })

    expect(wrapper.find('.stub-empty').exists()).toBe(false)
    const text = wrapper.text()
    expect(text).toContain('temperature')
    expect(text).toContain('26.5')
    expect(text).toContain('humidity')
    expect(text).toContain('58')
    expect(text).toContain('float')
  })

  it('上报时间缺失时回退为占位符', () => {
    const wrapper = mountPanel({
      properties: [{ identifier: 'battery', dataType: 'int', valueText: '88', reportedAt: null }]
    })

    expect(wrapper.text()).toContain('—')
  })
})