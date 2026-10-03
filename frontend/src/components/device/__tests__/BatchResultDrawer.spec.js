import { describe, expect, it } from 'vitest'
import { computed, h, inject, provide } from 'vue'
import { mount } from '@vue/test-utils'
import BatchResultDrawer from '../BatchResultDrawer.vue'

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

const ElDrawerStub = {
  name: 'ElDrawer',
  props: ['modelValue', 'title'],
  setup(props, { slots }) {
    return () => (props.modelValue ? h('div', { class: 'stub-drawer' }, slots.default?.()) : null)
  }
}

const EmptyStateStub = {
  name: 'EmptyState',
  props: ['description', 'imageSize'],
  template: '<div class="stub-empty">{{ description }}</div>'
}

const STUBS = {
  ElDrawer: ElDrawerStub,
  ElTable: ElTableStub,
  ElTableColumn: ElTableColumnStub,
  ElTag: ElTagStub,
  EmptyState: EmptyStateStub
}

const RESULT = {
  total: 3,
  succeeded: 2,
  failed: 1,
  items: [
    { deviceKey: 'sensor-1', deviceName: '温湿度传感器', success: true, status: 'SENT' },
    { deviceKey: 'sensor-2', deviceName: '光照传感器', success: true, status: 'SENT' },
    { deviceKey: 'gateway-1', deviceName: '网关一号', success: false, error: '物模型无此属性' }
  ]
}

function mountDrawer(props) {
  return mount(BatchResultDrawer, { props, global: { stubs: STUBS } })
}

describe('components/device/BatchResultDrawer', () => {
  it('抽屉关闭时不渲染内容', () => {
    const wrapper = mountDrawer({ visible: false, result: RESULT })
    expect(wrapper.find('.stub-drawer').exists()).toBe(false)
  })

  it('无结果时展示占位提示', () => {
    const wrapper = mountDrawer({ visible: true, result: null })

    expect(wrapper.find('.stub-drawer').exists()).toBe(true)
    expect(wrapper.find('.stub-empty').text()).toBe('暂无批量结果')
  })

  it('汇总目标 / 成功 / 失败台数', () => {
    const wrapper = mountDrawer({ visible: true, result: RESULT })
    const summary = wrapper.find('.batch-summary').text()

    expect(summary).toContain('目标 3 台')
    expect(summary).toContain('成功 2 台')
    expect(summary).toContain('失败 1 台')
  })

  it('逐行渲染设备、成功标记与失败原因', () => {
    const wrapper = mountDrawer({ visible: true, result: RESULT })

    expect(wrapper.findAll('.stub-tag')).toHaveLength(3)
    expect(wrapper.text()).toContain('温湿度传感器')
    expect(wrapper.text()).toContain('sensor-1')
    expect(wrapper.text()).toContain('网关一号')

    const tags = wrapper.findAll('.stub-tag')
    expect(tags[0].attributes('data-type')).toBe('success')
    expect(tags[2].attributes('data-type')).toBe('danger')
    expect(wrapper.text()).toContain('物模型无此属性')
  })

  it('设备名缺失时回退展示 deviceKey', () => {
    const wrapper = mountDrawer({
      visible: true,
      result: { total: 1, succeeded: 1, failed: 0, items: [{ deviceKey: 'd-only', success: true }] }
    })

    expect(wrapper.find('.batch-device__name').text()).toBe('d-only')
  })
})