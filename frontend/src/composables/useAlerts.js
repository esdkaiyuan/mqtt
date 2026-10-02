import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { alertApi } from '@/api/alert'
import { unwrapResult } from '@/utils/result'

const DEFAULT_PAGE_SIZE = 20
const UNREAD_POLL_MS = 30 * 1000
const EMPTY_PAGE = { records: [], total: 0 }

/**
 * 失效告警相关缓存：记录列表、未读数、顶栏最近告警。
 * 确认 / 恢复 / 规则增删改都会改变这三者的结果，统一在此收敛，避免各处遗漏。
 */
function invalidateAlertCaches(queryClient) {
  queryClient.invalidateQueries({ queryKey: ['alerts'] })
  queryClient.invalidateQueries({ queryKey: ['alert-unread-count'] })
  queryClient.invalidateQueries({ queryKey: ['alert-recent'] })
}

/**
 * 告警记录分页查询（T-17 设计文档 §11.2）。
 *
 * 筛选即时生效（无「查询」按钮）：条件或页码变化都会命中不同缓存。
 * 「仅看未恢复」走服务端 `openOnly`（status <> RECOVERED），与精确 `status` 互斥。
 */
export function useAlertListQuery() {
  const filters = reactive({ status: '', sourceType: '', severity: '' })
  const openOnly = ref(false)
  const currentPage = ref(1)
  const pageSize = ref(DEFAULT_PAGE_SIZE)

  const params = computed(() => {
    const query = { pageNum: currentPage.value, pageSize: pageSize.value }
    if (filters.status) query.status = filters.status
    if (filters.sourceType) query.sourceType = filters.sourceType
    if (filters.severity) query.severity = filters.severity
    if (openOnly.value) query.openOnly = true
    return query
  })

  const listQuery = useQuery({
    queryKey: computed(() => ['alerts', params.value]),
    queryFn: async () => unwrapResult(await alertApi.list(params.value), EMPTY_PAGE)
  })

  const records = computed(() => listQuery.data.value?.records ?? [])
  const total = computed(() => listQuery.data.value?.total ?? 0)
  const loading = computed(() => listQuery.isFetching.value)

  /** 选择具体状态时取消「仅看未恢复」，避免两个条件叠加成空结果 */
  function setStatus(value) {
    filters.status = value
    if (value) openOnly.value = false
    currentPage.value = 1
  }

  /** 开启「仅看未恢复」时清空精确状态 */
  function setOpenOnly(value) {
    openOnly.value = value
    if (value) filters.status = ''
    currentPage.value = 1
  }

  function setFilter(key, value) {
    filters[key] = value
    currentPage.value = 1
  }

  function resetFilters() {
    filters.status = ''
    filters.sourceType = ''
    filters.severity = ''
    openOnly.value = false
    currentPage.value = 1
  }

  function goToPage(page) {
    if (page >= 1) currentPage.value = page
  }

  function changePageSize(size) {
    pageSize.value = size
    currentPage.value = 1
  }

  function refresh() {
    return listQuery.refetch()
  }

  return {
    filters,
    openOnly,
    currentPage,
    pageSize,
    records,
    total,
    loading,
    setStatus,
    setOpenOnly,
    setFilter,
    resetFilters,
    goToPage,
    changePageSize,
    refresh
  }
}

/**
 * 规则列表查询。`sourceType` 为空表示不过滤；`enabled` 取 '' / 'ENABLED' / 'DISABLED'。
 */
export function useAlertRulesQuery() {
  const sourceType = ref('')
  const enabled = ref('')

  const params = computed(() => {
    const query = {}
    if (sourceType.value) query.sourceType = sourceType.value
    if (enabled.value) query.enabled = enabled.value === 'ENABLED'
    return query
  })

  const rulesQuery = useQuery({
    queryKey: computed(() => ['alert-rules', params.value]),
    queryFn: async () => unwrapResult(await alertApi.listRules(params.value), [])
  })

  return {
    sourceType,
    enabled,
    rules: computed(() => rulesQuery.data.value ?? []),
    loading: computed(() => rulesQuery.isFetching.value),
    refresh: () => rulesQuery.refetch()
  }
}

/** 未读数：按 30s 轮询，与顶栏健康度轮询节奏一致。 */
export function useAlertUnreadCountQuery() {
  const unreadQuery = useQuery({
    queryKey: ['alert-unread-count'],
    queryFn: async () => unwrapResult(await alertApi.unreadCount(), 0),
    refetchInterval: UNREAD_POLL_MS
  })

  return {
    unreadCount: computed(() => unreadQuery.data.value ?? 0),
    loading: computed(() => unreadQuery.isFetching.value),
    refresh: () => unreadQuery.refetch()
  }
}

/** 顶栏最近活动告警：与未读数同频轮询，避免两处节奏不一致。 */
export function useAlertRecentQuery(limit = 10) {
  const recentQuery = useQuery({
    queryKey: ['alert-recent', limit],
    queryFn: async () => unwrapResult(await alertApi.recent(limit), []),
    refetchInterval: UNREAD_POLL_MS
  })

  return {
    recentAlerts: computed(() => recentQuery.data.value ?? []),
    loading: computed(() => recentQuery.isFetching.value),
    refresh: () => recentQuery.refetch()
  }
}

/** 确认告警；成功后失效列表与未读数。 */
export function useAckAlertMutation() {
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: (id) => alertApi.acknowledge(id),
    onSuccess: () => invalidateAlertCaches(queryClient)
  })

  return {
    acknowledging: computed(() => mutation.isPending.value),
    ackAlert: (id) => mutation.mutateAsync(id)
  }
}

/** 人工置恢复；成功后失效列表与未读数。 */
export function useRecoverAlertMutation() {
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: (id) => alertApi.recover(id),
    onSuccess: () => invalidateAlertCaches(queryClient)
  })

  return {
    recovering: computed(() => mutation.isPending.value),
    recoverAlert: (id) => mutation.mutateAsync(id)
  }
}

/** 规则增删改；成功后失效规则列表。 */
export function useAlertRuleMutations() {
  const queryClient = useQueryClient()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['alert-rules'] })

  const createMutation = useMutation({
    mutationFn: (payload) => alertApi.createRule(payload),
    onSuccess: invalidate
  })
  const updateMutation = useMutation({
    mutationFn: ({ id, payload }) => alertApi.updateRule(id, payload),
    onSuccess: invalidate
  })
  const deleteMutation = useMutation({
    mutationFn: (id) => alertApi.deleteRule(id),
    onSuccess: invalidate
  })

  return {
    saving: computed(() => createMutation.isPending.value || updateMutation.isPending.value),
    deleting: computed(() => deleteMutation.isPending.value),
    createRule: (payload) => createMutation.mutateAsync(payload),
    updateRule: (id, payload) => updateMutation.mutateAsync({ id, payload }),
    deleteRule: (id) => deleteMutation.mutateAsync(id)
  }
}