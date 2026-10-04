import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { sceneApi } from '@/api/scene'
import { unwrapResult } from '@/utils/result'

const DEFAULT_PAGE_SIZE = 20
const EMPTY_PAGE = { records: [], total: 0 }

/**
 * 失效场景相关缓存：场景定义列表与执行记录。
 * 增删改 / 启停 / 手动执行都会改变这两者，统一在此收敛，避免各处遗漏。
 */
function invalidateSceneCaches(queryClient) {
  queryClient.invalidateQueries({ queryKey: ['scenes'] })
  queryClient.invalidateQueries({ queryKey: ['scene-executions'] })
}

/**
 * 场景定义分页查询（T-23 §11.2）。
 *
 * 筛选即时生效（无「查询」按钮）：条件或页码变化都会命中不同缓存。
 * `enabled` 取 '' / 'ENABLED' / 'DISABLED'，仅 'ENABLED' 映射为服务端布尔 true。
 */
export function useSceneListQuery() {
  const filters = reactive({ triggerType: '', enabled: '', keyword: '' })
  const currentPage = ref(1)
  const pageSize = ref(DEFAULT_PAGE_SIZE)

  const params = computed(() => {
    const query = { pageNum: currentPage.value, pageSize: pageSize.value }
    if (filters.triggerType) query.triggerType = filters.triggerType
    if (filters.enabled) query.enabled = filters.enabled === 'ENABLED'
    if (filters.keyword) query.keyword = filters.keyword
    return query
  })

  const listQuery = useQuery({
    queryKey: computed(() => ['scenes', params.value]),
    queryFn: async () => unwrapResult(await sceneApi.listScenes(params.value), EMPTY_PAGE)
  })

  const scenes = computed(() => listQuery.data.value?.records ?? [])
  const total = computed(() => listQuery.data.value?.total ?? 0)
  const loading = computed(() => listQuery.isFetching.value)

  function setFilter(key, value) {
    filters[key] = value
    currentPage.value = 1
  }

  function resetFilters() {
    filters.triggerType = ''
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

  function refresh() {
    return listQuery.refetch()
  }

  return {
    filters,
    currentPage,
    pageSize,
    scenes,
    total,
    loading,
    setFilter,
    resetFilters,
    goToPage,
    changePageSize,
    refresh
  }
}

/** 场景定义详情查询（编辑时按需拉取含步骤流的完整配置）。 */
export function useSceneDetailQuery(id) {
  const sceneId = computed(() => id.value)
  const detailQuery = useQuery({
    queryKey: computed(() => ['scenes', 'detail', sceneId.value]),
    queryFn: async () => unwrapResult(await sceneApi.getScene(sceneId.value), null),
    enabled: computed(() => Boolean(sceneId.value))
  })

  return {
    scene: computed(() => detailQuery.data.value ?? null),
    loading: computed(() => detailQuery.isFetching.value),
    refresh: () => detailQuery.refetch()
  }
}

/** 场景增删改 / 启停；成功后失效列表与详情。 */
export function useSceneMutations() {
  const queryClient = useQueryClient()
  const invalidate = () => invalidateSceneCaches(queryClient)

  const createMutation = useMutation({
    mutationFn: (payload) => sceneApi.createScene(payload),
    onSuccess: invalidate
  })
  const updateMutation = useMutation({
    mutationFn: ({ id, payload }) => sceneApi.updateScene(id, payload),
    onSuccess: invalidate
  })
  const deleteMutation = useMutation({
    mutationFn: (id) => sceneApi.deleteScene(id),
    onSuccess: invalidate
  })
  const enabledMutation = useMutation({
    mutationFn: ({ id, enabled }) => sceneApi.setSceneEnabled(id, enabled),
    onSuccess: invalidate
  })

  return {
    saving: computed(() => createMutation.isPending.value || updateMutation.isPending.value),
    deleting: computed(() => deleteMutation.isPending.value),
    toggling: computed(() => enabledMutation.isPending.value),
    createScene: (payload) => createMutation.mutateAsync(payload),
    updateScene: (id, payload) => updateMutation.mutateAsync({ id, payload }),
    deleteScene: (id) => deleteMutation.mutateAsync(id),
    setEnabled: (id, enabled) => enabledMutation.mutateAsync({ id, enabled })
  }
}

/** 试运行（干跑，无副作用）：返回 §10.2 的结构化结果，失败回退 null。 */
export function useSceneTestMutation() {
  const mutation = useMutation({
    mutationFn: ({ id, payload }) => sceneApi.testScene(id, payload)
  })

  return {
    testing: computed(() => mutation.isPending.value),
    testScene: (id, payload) => mutation.mutateAsync({ id, payload }).then((res) => unwrapResult(res, null))
  }
}

/** 手动执行一次（真实触发）：成功后失效执行记录缓存。 */
export function useSceneRunMutation() {
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: (id) => sceneApi.runScene(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['scene-executions'] })
  })

  return {
    running: computed(() => mutation.isPending.value),
    runScene: (id) => mutation.mutateAsync(id).then((res) => unwrapResult(res, null))
  }
}
