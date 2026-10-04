import { computed, reactive, ref } from 'vue'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { sceneApi } from '@/api/scene'
import { unwrapResult } from '@/utils/result'

const DEFAULT_PAGE_SIZE = 20
const EXECUTION_POLL_MS = 3 * 1000
const EMPTY_PAGE = { records: [], total: 0 }
/** 非终态：仍在推进中的执行，需要轮询刷新。 */
const ACTIVE_STATUSES = new Set(['PENDING', 'RUNNING'])

function hasActiveExecution(page) {
  const records = page?.records ?? []
  return records.some((row) => ACTIVE_STATUSES.has(row.status))
}

/**
 * 场景执行记录分页查询（T-23 §11.4）。
 *
 * 仅当当前页存在 `PENDING` / `RUNNING` 记录时按 3s 轮询，全部终态后自动停轮询，
 * 避免场景执行列表长期空转。翻页保留上一页数据（`keepPreviousData`）防止抖动。
 */
export function useSceneExecutionsQuery() {
  const filters = reactive({ sceneId: null, deviceId: null, status: '' })
  const currentPage = ref(1)
  const pageSize = ref(DEFAULT_PAGE_SIZE)

  const params = computed(() => {
    const query = { pageNum: currentPage.value, pageSize: pageSize.value }
    if (filters.sceneId) query.sceneId = filters.sceneId
    if (filters.deviceId) query.deviceId = filters.deviceId
    if (filters.status) query.status = filters.status
    return query
  })

  const listQuery = useQuery({
    queryKey: computed(() => ['scene-executions', params.value]),
    queryFn: async () => unwrapResult(await sceneApi.listSceneExecutions(params.value), EMPTY_PAGE),
    placeholderData: keepPreviousData,
    refetchInterval: (query) => (hasActiveExecution(query.state.data) ? EXECUTION_POLL_MS : false)
  })

  const executions = computed(() => listQuery.data.value?.records ?? [])
  const total = computed(() => listQuery.data.value?.total ?? 0)
  const loading = computed(() => listQuery.isFetching.value)
  const hasActive = computed(() => hasActiveExecution(listQuery.data.value))

  function setFilter(key, value) {
    filters[key] = value
    currentPage.value = 1
  }

  function resetFilters() {
    filters.sceneId = null
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

  function refresh() {
    return listQuery.refetch()
  }

  return {
    filters,
    currentPage,
    pageSize,
    executions,
    total,
    loading,
    hasActive,
    setFilter,
    resetFilters,
    goToPage,
    changePageSize,
    refresh
  }
}

/**
 * 场景执行详情查询：抽屉打开时拉取触发快照 + 步骤明细（`{ execution, steps }`），
 * 非终态时按 3s 轮询，直至执行落 `SUCCESS` / `FAILED`。
 *
 * 注意后端返回的是 `SceneExecutionDetail` 包装体（`execution` + `steps`），
 * 因此轮询判定与对外暴露都需按包装结构取值。
 */
export function useSceneExecutionDetailQuery(id) {
  const executionId = computed(() => id.value)
  const detailQuery = useQuery({
    queryKey: computed(() => ['scene-executions', 'detail', executionId.value]),
    queryFn: async () => unwrapResult(await sceneApi.getSceneExecution(executionId.value), null),
    enabled: computed(() => Boolean(executionId.value)),
    refetchInterval: (query) => {
      const status = query.state.data?.execution?.status
      return ACTIVE_STATUSES.has(status) ? EXECUTION_POLL_MS : false
    }
  })

  return {
    execution: computed(() => detailQuery.data.value?.execution ?? null),
    steps: computed(() => detailQuery.data.value?.steps ?? []),
    loading: computed(() => detailQuery.isFetching.value),
    refresh: () => detailQuery.refetch()
  }
}

/** 手动重试（仅 `FAILED`）；成功后失效执行记录列表与详情。 */
export function useSceneExecutionRetryMutation() {
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: (id) => sceneApi.retrySceneExecution(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['scene-executions'] })
  })

  return {
    retrying: computed(() => mutation.isPending.value),
    retryExecution: (id) => mutation.mutateAsync(id)
  }
}
