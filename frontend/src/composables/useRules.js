import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ruleApi } from '@/api/rule'
import { unwrapResult } from '@/utils/result'

const DEFAULT_PAGE_SIZE = 20
const EMPTY_PAGE = { records: [], total: 0 }

/**
 * 失效规则相关缓存：规则列表与执行记录。
 * 规则增删改 / 启停会改变可触发的规则集，手动重试会改变执行记录状态，统一在此收敛。
 */
function invalidateRuleCaches(queryClient) {
  queryClient.invalidateQueries({ queryKey: ['rules'] })
  queryClient.invalidateQueries({ queryKey: ['rule-executions'] })
}

/**
 * 规则分页查询（T-19 设计文档 §11）。
 *
 * 筛选即时生效（无「查询」按钮）：条件或页码变化都会命中不同缓存。
 * `enabled` 取 '' / 'ENABLED' / 'DISABLED'，映射为布尔入参。
 */
export function useRuleListQuery() {
  const filters = reactive({ sourceType: '', actionType: '', enabled: '', keyword: '' })
  const currentPage = ref(1)
  const pageSize = ref(DEFAULT_PAGE_SIZE)

  const params = computed(() => {
    const query = { pageNum: currentPage.value, pageSize: pageSize.value }
    if (filters.sourceType) query.sourceType = filters.sourceType
    if (filters.actionType) query.actionType = filters.actionType
    if (filters.enabled) query.enabled = filters.enabled === 'ENABLED'
    if (filters.keyword) query.keyword = filters.keyword
    return query
  })

  const listQuery = useQuery({
    queryKey: computed(() => ['rules', params.value]),
    queryFn: async () => unwrapResult(await ruleApi.listRules(params.value), EMPTY_PAGE)
  })

  function setFilter(key, value) {
    filters[key] = value || ''
    currentPage.value = 1
  }

  function resetFilters() {
    filters.sourceType = ''
    filters.actionType = ''
    filters.enabled = ''
    filters.keyword = ''
    currentPage.value = 1
  }

  function goToPage(page) {
    if (page >= 1) currentPage.value = page
  }

  function changePageSize(size) {
    pageSize.value = size
    currentPage.value = 1
  }

  return {
    filters,
    currentPage,
    pageSize,
    rules: computed(() => listQuery.data.value?.records ?? []),
    total: computed(() => listQuery.data.value?.total ?? 0),
    loading: computed(() => listQuery.isFetching.value),
    setFilter,
    resetFilters,
    goToPage,
    changePageSize,
    refresh: () => listQuery.refetch()
  }
}

/** 执行记录分页查询：可按规则 / 设备 / 状态过滤，按创建时间倒序。 */
export function useRuleExecutionsQuery() {
  const filters = reactive({ ruleId: null, deviceId: null, status: '' })
  const currentPage = ref(1)
  const pageSize = ref(DEFAULT_PAGE_SIZE)

  const params = computed(() => {
    const query = { pageNum: currentPage.value, pageSize: pageSize.value }
    if (filters.ruleId) query.ruleId = filters.ruleId
    if (filters.deviceId) query.deviceId = filters.deviceId
    if (filters.status) query.status = filters.status
    return query
  })

  const listQuery = useQuery({
    queryKey: computed(() => ['rule-executions', params.value]),
    queryFn: async () => unwrapResult(await ruleApi.listExecutions(params.value), EMPTY_PAGE)
  })

  function setFilter(key, value) {
    filters[key] = value || null
    currentPage.value = 1
  }

  function resetFilters() {
    filters.ruleId = null
    filters.deviceId = null
    filters.status = ''
    currentPage.value = 1
  }

  function goToPage(page) {
    if (page >= 1) currentPage.value = page
  }

  function changePageSize(size) {
    pageSize.value = size
    currentPage.value = 1
  }

  return {
    filters,
    currentPage,
    pageSize,
    records: computed(() => listQuery.data.value?.records ?? []),
    total: computed(() => listQuery.data.value?.total ?? 0),
    loading: computed(() => listQuery.isFetching.value),
    hasPending: computed(() => (listQuery.data.value?.records ?? []).some((row) => row.status === 'PENDING')),
    setFilter,
    resetFilters,
    goToPage,
    changePageSize,
    refresh: () => listQuery.refetch()
  }
}

/** 规则增删改 / 启停；成功后失效规则列表与执行记录。 */
export function useRuleMutations() {
  const queryClient = useQueryClient()
  const invalidate = () => invalidateRuleCaches(queryClient)

  const createMutation = useMutation({
    mutationFn: (payload) => ruleApi.createRule(payload),
    onSuccess: invalidate
  })
  const updateMutation = useMutation({
    mutationFn: ({ id, payload }) => ruleApi.updateRule(id, payload),
    onSuccess: invalidate
  })
  const deleteMutation = useMutation({
    mutationFn: (id) => ruleApi.deleteRule(id),
    onSuccess: invalidate
  })
  const enabledMutation = useMutation({
    mutationFn: ({ id, enabled }) => ruleApi.setRuleEnabled(id, enabled),
    onSuccess: invalidate
  })

  return {
    saving: computed(() => createMutation.isPending.value || updateMutation.isPending.value),
    deleting: computed(() => deleteMutation.isPending.value),
    toggling: computed(() => enabledMutation.isPending.value),
    createRule: (payload) => createMutation.mutateAsync(payload),
    updateRule: (id, payload) => updateMutation.mutateAsync({ id, payload }),
    deleteRule: (id) => deleteMutation.mutateAsync(id),
    setEnabled: (id, enabled) => enabledMutation.mutateAsync({ id, enabled })
  }
}

/** 试运行（干跑，无副作用）。 */
export function useRuleTestMutation() {
  const mutation = useMutation({
    mutationFn: ({ id, payload }) => ruleApi.testRule(id, payload)
  })

  return {
    testing: computed(() => mutation.isPending.value),
    testRule: (id, payload) => mutation.mutateAsync({ id, payload }).then((res) => unwrapResult(res, null))
  }
}

/** 手动重试执行记录；成功后失效执行记录查询。 */
export function useExecutionRetryMutation() {
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: (id) => ruleApi.retryExecution(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['rule-executions'] })
  })

  return {
    retrying: computed(() => mutation.isPending.value),
    retryExecution: (id) => mutation.mutateAsync(id)
  }
}