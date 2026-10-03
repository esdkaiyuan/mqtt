import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'

vi.mock('@/api/rule', () => ({
  ruleApi: {
    listRules: vi.fn(),
    getRule: vi.fn(),
    createRule: vi.fn(),
    updateRule: vi.fn(),
    deleteRule: vi.fn(),
    setRuleEnabled: vi.fn(),
    testRule: vi.fn(),
    listExecutions: vi.fn(),
    getExecution: vi.fn(),
    retryExecution: vi.fn()
  }
}))

import { ruleApi } from '@/api/rule'
import {
  useRuleListQuery,
  useRuleExecutionsQuery,
  useRuleMutations,
  useRuleTestMutation,
  useExecutionRetryMutation
} from '../useRules'

const RULE = {
  id: 1,
  name: '高温联动降温',
  sourceType: 'PROPERTY',
  actionType: 'SEND_COMMAND',
  identifier: 'temperature',
  operator: 'GT',
  thresholdValue: '40',
  enabled: 1
}

const EXECUTION = {
  id: 11,
  ruleId: 1,
  ruleName: '高温联动降温',
  deviceKey: 'dev-1',
  sourceType: 'PROPERTY',
  actionType: 'SEND_COMMAND',
  status: 'PENDING',
  attemptCount: 0
}

async function settle() {
  await flushPromises()
  await nextTick()
  await flushPromises()
}

describe('composables/useRules', () => {
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
    ruleApi.listRules.mockResolvedValue({ code: 200, data: { records: [RULE], total: 1 } })
    ruleApi.listExecutions.mockResolvedValue({ code: 200, data: { records: [EXECUTION], total: 1 } })
  })

  afterEach(() => {
    wrapper?.unmount()
    queryClient.clear()
    vi.clearAllMocks()
  })

  it('规则列表默认按第 1 页查询，空筛选不下发空串参数', async () => {
    mountHarness(useRuleListQuery)
    await settle()

    expect(ruleApi.listRules).toHaveBeenCalledWith({ pageNum: 1, pageSize: 20 })
    expect(state.rules.value).toEqual([RULE])
    expect(state.total.value).toBe(1)
  })

  it('筛选变更回第 1 页并映射启用态为布尔入参', async () => {
    mountHarness(useRuleListQuery)
    await settle()

    state.goToPage(3)
    await settle()
    expect(ruleApi.listRules).toHaveBeenLastCalledWith({ pageNum: 3, pageSize: 20 })

    state.setFilter('enabled', 'ENABLED')
    await settle()
    expect(state.currentPage.value).toBe(1)
    expect(ruleApi.listRules).toHaveBeenLastCalledWith({ pageNum: 1, pageSize: 20, enabled: true })

    state.setFilter('sourceType', 'EVENT')
    state.setFilter('actionType', 'FORWARD_HTTP')
    state.setFilter('keyword', '高温')
    await settle()
    expect(ruleApi.listRules).toHaveBeenLastCalledWith({
      pageNum: 1,
      pageSize: 20,
      sourceType: 'EVENT',
      actionType: 'FORWARD_HTTP',
      enabled: true,
      keyword: '高温'
    })
  })

  it('重置筛选清空全部条件并回到第 1 页', async () => {
    mountHarness(useRuleListQuery)
    await settle()

    state.setFilter('sourceType', 'EVENT')
    state.setFilter('enabled', 'DISABLED')
    await settle()

    state.resetFilters()
    await settle()

    expect(state.filters.sourceType).toBe('')
    expect(state.filters.enabled).toBe('')
    expect(state.currentPage.value).toBe(1)

    // 重置后换页尺寸：入参不应再带任何筛选条件
    state.changePageSize(50)
    await settle()
    expect(ruleApi.listRules).toHaveBeenLastCalledWith({ pageNum: 1, pageSize: 50 })
  })

  it('执行记录按规则 / 设备 / 状态过滤并标记待执行', async () => {
    mountHarness(useRuleExecutionsQuery)
    await settle()
    expect(ruleApi.listExecutions).toHaveBeenCalledWith({ pageNum: 1, pageSize: 20 })
    expect(state.hasPending.value).toBe(true)

    state.setFilter('ruleId', 1)
    state.setFilter('deviceId', 9)
    state.setFilter('status', 'FAILED')
    await settle()

    expect(ruleApi.listExecutions).toHaveBeenLastCalledWith({
      pageNum: 1,
      pageSize: 20,
      ruleId: 1,
      deviceId: 9,
      status: 'FAILED'
    })
  })

  it('规则增删改与启停后失效规则列表缓存', async () => {
    ruleApi.createRule.mockResolvedValue({ code: 200, data: { id: 2 } })
    ruleApi.updateRule.mockResolvedValue({ code: 200, data: { id: 1 } })
    ruleApi.deleteRule.mockResolvedValue({ code: 200, data: null })
    ruleApi.setRuleEnabled.mockResolvedValue({ code: 200, data: null })
    mountHarness(() => ({ list: useRuleListQuery(), mutations: useRuleMutations() }))
    await settle()
    expect(ruleApi.listRules).toHaveBeenCalledTimes(1)

    await state.mutations.createRule({ name: 'r1' })
    await settle()
    expect(ruleApi.listRules).toHaveBeenCalledTimes(2)

    await state.mutations.updateRule(1, { name: 'r2' })
    await settle()
    expect(ruleApi.updateRule).toHaveBeenCalledWith(1, { name: 'r2' })
    expect(ruleApi.listRules).toHaveBeenCalledTimes(3)

    await state.mutations.setEnabled(1, false)
    await settle()
    expect(ruleApi.setRuleEnabled).toHaveBeenCalledWith(1, false)
    expect(ruleApi.listRules).toHaveBeenCalledTimes(4)

    await state.mutations.deleteRule(1)
    await settle()
    expect(ruleApi.deleteRule).toHaveBeenCalledWith(1)
    expect(ruleApi.listRules).toHaveBeenCalledTimes(5)
  })

  it('试运行解包 Result 信封返回干跑结论', async () => {
    ruleApi.testRule.mockResolvedValue({
      code: 200,
      data: { matched: true, reason: '命中条件', actionExecutable: true }
    })
    mountHarness(useRuleTestMutation)
    await settle()

    const result = await state.testRule(1, { deviceId: 9, identifier: 'temperature', valueText: '50' })
    expect(ruleApi.testRule).toHaveBeenCalledWith(1, {
      deviceId: 9,
      identifier: 'temperature',
      valueText: '50'
    })
    expect(result).toMatchObject({ matched: true, reason: '命中条件' })
  })

  it('手动重试后失效执行记录缓存并重新拉取', async () => {
    ruleApi.retryExecution.mockResolvedValue({ code: 200, data: null })
    mountHarness(() => ({ executions: useRuleExecutionsQuery(), retry: useExecutionRetryMutation() }))
    await settle()
    expect(ruleApi.listExecutions).toHaveBeenCalledTimes(1)

    await state.retry.retryExecution(11)
    await settle()

    expect(ruleApi.retryExecution).toHaveBeenCalledWith(11)
    expect(ruleApi.listExecutions).toHaveBeenCalledTimes(2)
  })
})