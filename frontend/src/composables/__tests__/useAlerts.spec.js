import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'

vi.mock('@/api/alert', () => ({
  alertApi: {
    list: vi.fn(),
    listRules: vi.fn(),
    createRule: vi.fn(),
    updateRule: vi.fn(),
    deleteRule: vi.fn(),
    getDetail: vi.fn(),
    acknowledge: vi.fn(),
    recover: vi.fn(),
    unreadCount: vi.fn(),
    recent: vi.fn()
  }
}))

import { alertApi } from '@/api/alert'
import {
  useAlertListQuery,
  useAlertRulesQuery,
  useAlertUnreadCountQuery,
  useAlertRecentQuery,
  useAckAlertMutation,
  useRecoverAlertMutation,
  useAlertRuleMutations
} from '../useAlerts'

const RECORD = {
  id: 7,
  deviceKey: 'dev-1',
  ruleName: '高温告警',
  sourceType: 'THRESHOLD',
  severity: 'CRITICAL',
  status: 'TRIGGERED',
  title: '温度超过 80',
  triggerCount: 3,
  lastTriggeredAt: '2026-10-02T10:00:00.000'
}

async function settle() {
  await flushPromises()
  await nextTick()
  await flushPromises()
}

describe('composables/useAlerts', () => {
  let queryClient
  let state
  let wrapper

  function mountHarness(setup) {
    const Harness = defineComponent({
      setup() {
        state = setup()
        return () => h('div')
      }
    })
    wrapper = mount(Harness, {
      global: { plugins: [[VueQueryPlugin, { queryClient }]] }
    })
    return wrapper
  }

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: Infinity, gcTime: Infinity } }
    })
    alertApi.list.mockResolvedValue({ code: 200, data: { records: [RECORD], total: 1 } })
    alertApi.listRules.mockResolvedValue({ code: 200, data: [] })
    alertApi.unreadCount.mockResolvedValue({ code: 200, data: 5 })
    alertApi.recent.mockResolvedValue({ code: 200, data: [RECORD] })
    alertApi.acknowledge.mockResolvedValue({ code: 200, data: null })
    alertApi.recover.mockResolvedValue({ code: 200, data: null })
  })

  afterEach(() => {
    wrapper?.unmount()
    queryClient.clear()
    vi.clearAllMocks()
  })

  it('列表默认按第 1 页查询，空筛选不下发空串参数', async () => {
    mountHarness(useAlertListQuery)
    await settle()

    expect(alertApi.list).toHaveBeenCalledWith({ pageNum: 1, pageSize: 20 })
    expect(state.records.value).toEqual([RECORD])
    expect(state.total.value).toBe(1)
  })

  it('「仅看未恢复」下发 openOnly 并清空精确状态', async () => {
    mountHarness(useAlertListQuery)
    await settle()

    state.setStatus('TRIGGERED')
    await settle()
    expect(alertApi.list).toHaveBeenLastCalledWith({ pageNum: 1, pageSize: 20, status: 'TRIGGERED' })

    state.setOpenOnly(true)
    await settle()

    expect(state.filters.status).toBe('')
    expect(alertApi.list).toHaveBeenLastCalledWith({ pageNum: 1, pageSize: 20, openOnly: true })
  })

  it('选择精确状态时取消「仅看未恢复」，回到第 1 页', async () => {
    mountHarness(useAlertListQuery)
    await settle()

    state.setOpenOnly(true)
    await settle()
    state.goToPage(3)
    await settle()

    state.setStatus('ACKNOWLEDGED')
    await settle()

    expect(state.openOnly.value).toBe(false)
    expect(state.currentPage.value).toBe(1)
    expect(alertApi.list).toHaveBeenLastCalledWith({
      pageNum: 1,
      pageSize: 20,
      status: 'ACKNOWLEDGED'
    })
  })

  it('规则列表按启用状态映射为布尔入参', async () => {
    mountHarness(useAlertRulesQuery)
    await settle()
    expect(alertApi.listRules).toHaveBeenCalledWith({})

    state.enabled.value = 'ENABLED'
    await settle()
    expect(alertApi.listRules).toHaveBeenLastCalledWith({ enabled: true })

    state.sourceType.value = 'OFFLINE'
    await settle()
    expect(alertApi.listRules).toHaveBeenLastCalledWith({ sourceType: 'OFFLINE', enabled: true })
  })

  it('未读数与最近告警解包 Result 信封', async () => {
    mountHarness(() => ({
      unread: useAlertUnreadCountQuery(),
      recent: useAlertRecentQuery(5)
    }))
    await settle()

    expect(state.unread.unreadCount.value).toBe(5)
    expect(alertApi.recent).toHaveBeenCalledWith(5)
    expect(state.recent.recentAlerts.value).toEqual([RECORD])
  })

  it('确认告警后失效列表缓存并重新拉取', async () => {
    mountHarness(() => ({ list: useAlertListQuery(), ack: useAckAlertMutation() }))
    await settle()
    expect(alertApi.list).toHaveBeenCalledTimes(1)

    await state.ack.ackAlert(7)
    await settle()

    expect(alertApi.acknowledge).toHaveBeenCalledWith(7)
    expect(alertApi.list).toHaveBeenCalledTimes(2)
  })

  it('恢复告警后失效列表缓存并重新拉取', async () => {
    mountHarness(() => ({ list: useAlertListQuery(), recover: useRecoverAlertMutation() }))
    await settle()

    await state.recover.recoverAlert(7)
    await settle()

    expect(alertApi.recover).toHaveBeenCalledWith(7)
    expect(alertApi.list).toHaveBeenCalledTimes(2)
  })

  it('规则增删改后失效规则列表缓存', async () => {
    alertApi.createRule.mockResolvedValue({ code: 200, data: { id: 1 } })
    alertApi.updateRule.mockResolvedValue({ code: 200, data: { id: 1 } })
    alertApi.deleteRule.mockResolvedValue({ code: 200, data: null })
    mountHarness(() => ({ rules: useAlertRulesQuery(), mutations: useAlertRuleMutations() }))
    await settle()
    expect(alertApi.listRules).toHaveBeenCalledTimes(1)

    await state.mutations.createRule({ name: 'r1' })
    await settle()
    expect(alertApi.listRules).toHaveBeenCalledTimes(2)

    await state.mutations.updateRule(1, { name: 'r2' })
    await settle()
    expect(alertApi.updateRule).toHaveBeenCalledWith(1, { name: 'r2' })
    expect(alertApi.listRules).toHaveBeenCalledTimes(3)

    await state.mutations.deleteRule(1)
    await settle()
    expect(alertApi.deleteRule).toHaveBeenCalledWith(1)
    expect(alertApi.listRules).toHaveBeenCalledTimes(4)
  })
})