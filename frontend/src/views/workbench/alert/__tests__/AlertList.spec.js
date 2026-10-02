import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, h, inject, provide } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

const { messageMock, confirmMock } = vi.hoisted(() => ({
  messageMock: { success: vi.fn(), warning: vi.fn(), info: vi.fn(), error: vi.fn() },
  confirmMock: vi.fn(() => Promise.resolve())
}))

vi.mock('element-plus', () => ({
  ElMessage: messageMock,
  ElMessageBox: { confirm: confirmMock }
}))

const hooks = vi.hoisted(() => ({
  filters: null,
  openOnly: null,
  currentPage: null,
  pageSize: null,
  records: null,
  total: null,
  loading: null,
  setStatus: null,
  setOpenOnly: null,
  setFilter: null,
  resetFilters: null,
  goToPage: null,
  changePageSize: null,
  refresh: null,
  acknowledging: null,
  ackAlert: null,
  recovering: null,
  recoverAlert: null
}))

vi.mock('@/composables/useAlerts', async () => {
  const { ref, reactive } = await import('vue')
  hooks.filters = reactive({ status: '', sourceType: '', severity: '' })
  hooks.openOnly = ref(false)
  hooks.currentPage = ref(1)
  hooks.pageSize = ref(20)
  hooks.records = ref([])
  hooks.total = ref(0)
  hooks.loading = ref(false)
  hooks.acknowledging = ref(false)
  hooks.recovering = ref(false)
  hooks.setStatus = vi.fn()
  hooks.setOpenOnly = vi.fn()
  hooks.setFilter = vi.fn()
  hooks.resetFilters = vi.fn()
  hooks.goToPage = vi.fn()
  hooks.changePageSize = vi.fn()
  hooks.refresh = vi.fn()
  hooks.ackAlert = vi.fn(() => Promise.resolve())
  hooks.recoverAlert = vi.fn(() => Promise.resolve())
  return {
    useAlertListQuery: () => ({
      filters: hooks.filters,
      openOnly: hooks.openOnly,
      currentPage: hooks.currentPage,
      pageSize: hooks.pageSize,
      records: hooks.records,
      total: hooks.total,
      loading: hooks.loading,
      setStatus: hooks.setStatus,
      setOpenOnly: hooks.setOpenOnly,
      setFilter: hooks.setFilter,
      resetFilters: hooks.resetFilters,
      goToPage: hooks.goToPage,
      changePageSize: hooks.changePageSize,
      refresh: hooks.refresh
    }),
    useAckAlertMutation: () => ({ acknowledging: hooks.acknowledging, ackAlert: hooks.ackAlert }),
    useRecoverAlertMutation: () => ({ recovering: hooks.recovering, recoverAlert: hooks.recoverAlert })
  }
})

vi.mock('@/api/alert', () => ({
  alertApi: {
    getDetail: vi.fn()
  }
}))

import { alertApi } from '@/api/alert'
import AlertList from '../AlertList.vue'

const OPEN_RECORD = {
  id: 7,
  deviceId: 3,
  deviceKey: 'dev-1',
  ruleName: '高温告警',
  sourceType: 'THRESHOLD',
  severity: 'CRITICAL',
  status: 'TRIGGERED',
  title: '温度超过 80',
  triggerValue: '86.5',
  triggerCount: 3,
  firstTriggeredAt: '2026-10-02T09:00:00.000',
  lastTriggeredAt: '2026-10-02T10:00:00.000'
}

const RECOVERED_RECORD = {
  ...OPEN_RECORD,
  id: 8,
  status: 'RECOVERED',
  title: '温度回落',
  recoveredAt: '2026-10-02T10:05:00.000'
}

/* ---------- Element Plus 最小替身 ---------- */
const ROW_KEY = Symbol('table-row')

const RowProvider = {
  name: 'RowProvider',
  props: ['row'],
  setup(props, { slots }) {
    provide(ROW_KEY, computed(() => props.row))
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
        props.data.map((row, index) => h(RowProvider, { row, key: index }, { default: () => slots.default?.() }))
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
      if (slots.default) return h('div', { class: 'stub-cell' }, slots.default({ row: row?.value ?? null }))
      return h('div', { class: 'stub-cell' }, props.prop ? String(row?.value?.[props.prop] ?? '') : '')
    }
  }
}

const ElButtonStub = {
  name: 'ElButton',
  emits: ['click'],
  setup(_, { slots, emit }) {
    return () => h('button', { class: 'stub-btn', onClick: () => emit('click') }, slots.default?.())
  }
}

const ElCheckboxStub = {
  name: 'ElCheckbox',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  setup(props, { emit, slots }) {
    return () =>
      h(
        'label',
        { class: 'stub-checkbox', onClick: () => emit('update:modelValue', !props.modelValue) },
        slots.default?.()
      )
  }
}

const ElDrawerStub = {
  name: 'ElDrawer',
  props: ['modelValue', 'title'],
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('div', { class: 'stub-drawer' }, [
            h('div', { class: 'stub-drawer-title' }, props.title),
            slots.default?.(),
            slots.footer?.()
          ])
        : null
  }
}

const passthrough = (name) => ({
  name,
  setup(_, { slots }) {
    return () => h('div', { class: `stub-${name}` }, slots.default?.())
  }
})

const EmptyStateStub = {
  name: 'EmptyState',
  props: ['description', 'imageSize'],
  template: '<div class="stub-empty">{{ description }}</div>'
}

const STUBS = {
  ElTable: ElTableStub,
  ElTableColumn: ElTableColumnStub,
  ElButton: ElButtonStub,
  ElCheckbox: ElCheckboxStub,
  ElDrawer: ElDrawerStub,
  ElSelect: passthrough('ElSelect'),
  ElOption: passthrough('ElOption'),
  ElTag: passthrough('ElTag'),
  ElPagination: passthrough('ElPagination'),
  EmptyState: EmptyStateStub
}

describe('views/workbench/alert/AlertList', () => {
  let wrapper
  let router

  async function mountPage(query = {}) {
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/workbench/alerts', component: { template: '<div />' } },
        { path: '/workbench/devices/:id', component: { template: '<div />' } }
      ]
    })
    await router.push({ path: '/workbench/alerts', query })
    await router.isReady()
    wrapper = mount(AlertList, { global: { plugins: [router], stubs: STUBS } })
    await flushPromises()
    return wrapper
  }

  beforeEach(() => {
    hooks.records.value = []
    hooks.total.value = 0
    hooks.openOnly.value = false
    confirmMock.mockResolvedValue(undefined)
    alertApi.getDetail.mockResolvedValue({ code: 200, data: OPEN_RECORD })
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  it('无记录时渲染空态提示', async () => {
    await mountPage()

    expect(wrapper.find('.stub-empty').exists()).toBe(true)
    expect(wrapper.find('.stub-empty').text()).toContain('暂无告警记录')
  })

  it('渲染设备 / 规则 / 状态并生成设备跳转链接', async () => {
    hooks.records.value = [OPEN_RECORD, RECOVERED_RECORD]
    hooks.total.value = 2
    await mountPage()

    const text = wrapper.text()
    expect(text).toContain('dev-1')
    expect(text).toContain('高温告警')
    expect(text).toContain('待处理')
    expect(text).toContain('已恢复')
    expect(text).toContain('温度超过 80')

    expect(wrapper.findAll('a.alert-device-link')).toHaveLength(2)
  })

  it('仅未恢复记录展示确认按钮，点击后调用确认接口', async () => {
    hooks.records.value = [OPEN_RECORD, RECOVERED_RECORD]
    await mountPage()

    const ackButtons = wrapper.findAll('button').filter((button) => button.text().includes('确认'))
    expect(ackButtons).toHaveLength(1)

    await ackButtons[0].trigger('click')
    await flushPromises()

    expect(hooks.ackAlert).toHaveBeenCalledWith(7)
    expect(messageMock.success).toHaveBeenCalled()
  })

  it('「仅看未恢复」勾选后回调 setOpenOnly', async () => {
    await mountPage()

    await wrapper.find('.stub-checkbox').trigger('click')

    expect(hooks.setOpenOnly).toHaveBeenCalledWith(true)
  })

  it('路由携带 alertId 时拉取详情并打开抽屉', async () => {
    await mountPage({ alertId: '7' })

    expect(alertApi.getDetail).toHaveBeenCalledWith('7')
    expect(wrapper.find('.stub-drawer').exists()).toBe(true)
    expect(wrapper.find('.stub-drawer').text()).toContain('温度超过 80')
    expect(wrapper.find('.stub-drawer').text()).toContain('86.5')
  })
})