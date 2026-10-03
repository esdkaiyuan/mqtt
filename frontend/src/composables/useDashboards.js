import { computed } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { dashboardApi } from '@/api/dashboard'
import { unwrapResult } from '@/utils/result'
import { DEFAULT_BUCKET } from '@/composables/usePropertyHistory'

/** 无数据时的兜底值。 */
const EMPTY_LIST = []

/** 看板列表查询键；变更后统一失效该键。 */
export const DASHBOARD_LIST_KEY = ['dashboards']

/** 图表类型白名单，与后端 `DashboardConfig.Panel.chartType` 校验一致。 */
export const CHART_TYPE_OPTIONS = [
  { label: '折线图', value: 'line' },
  { label: '柱状图', value: 'bar' }
]

/** 聚合方式白名单，与后端 `DashboardConfig.Panel.aggregation` 校验一致。 */
export const AGGREGATION_OPTIONS = [
  { label: '平均值', value: 'avg' },
  { label: '最小值', value: 'min' },
  { label: '最大值', value: 'max' },
  { label: '样本数', value: 'count' }
]

/** 新建面板的默认配置（rangeDays 默认 1 天，避免默认查询跨度过大）。 */
export function createDefaultPanel() {
  return {
    title: '新面板',
    deviceIds: [],
    identifier: '',
    bucket: DEFAULT_BUCKET,
    rangeDays: 1,
    chartType: 'line',
    aggregation: 'avg'
  }
}

/** 兜底把任意 config 规整为 `{ panels: [] }`，避免渲染层空指针。 */
export function normalizeConfig(config) {
  if (!config || typeof config !== 'object') return { panels: [] }
  return { panels: Array.isArray(config.panels) ? config.panels : [] }
}

/**
 * 看板列表与增删改（T-21）。
 *
 * 列表走 `useQuery`（键 `['dashboards']`）；create / update / remove 走 `useMutation`，
 * 成功后统一失效列表查询，保证面板数与更新时间即时刷新。
 * 错误提示已由 axios 拦截器给出（`6228` / `6229` / `6230`），这里只负责抛出。
 */
export function useDashboards() {
  const queryClient = useQueryClient()

  const listQuery = useQuery({
    queryKey: DASHBOARD_LIST_KEY,
    queryFn: async () => unwrapResult(await dashboardApi.list(), EMPTY_LIST)
  })

  function invalidateList() {
    return queryClient.invalidateQueries({ queryKey: DASHBOARD_LIST_KEY })
  }

  const createMutation = useMutation({
    mutationFn: (data) => dashboardApi.create(data),
    onSuccess: invalidateList
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }) => dashboardApi.update(id, data),
    onSuccess: (_data, variables) => {
      invalidateList()
      return queryClient.invalidateQueries({ queryKey: ['dashboard', variables.id] })
    }
  })

  const removeMutation = useMutation({
    mutationFn: (id) => dashboardApi.remove(id),
    onSuccess: invalidateList
  })

  async function create(data) {
    return unwrapResult(await createMutation.mutateAsync(data), null)
  }

  async function update(id, data) {
    return unwrapResult(await updateMutation.mutateAsync({ id, data }), null)
  }

  async function remove(id) {
    await removeMutation.mutateAsync(id)
  }

  return {
    dashboards: computed(() => listQuery.data.value ?? EMPTY_LIST),
    loading: computed(() => listQuery.isFetching.value),
    loadError: computed(() => listQuery.isError.value),
    refetch: () => listQuery.refetch(),
    saving: computed(() => createMutation.isPending.value || updateMutation.isPending.value),
    removing: computed(() => removeMutation.isPending.value),
    create,
    update,
    remove
  }
}

/**
 * 看板详情（含 config）与整份保存。
 *
 * @param {import('vue').Ref<string|number|null>} id 路由参数 id
 */
export function useDashboardDetail(id) {
  const queryClient = useQueryClient()

  const detailQuery = useQuery({
    queryKey: computed(() => ['dashboard', id.value]),
    queryFn: async () => unwrapResult(await dashboardApi.detail(id.value), null),
    enabled: computed(() => Boolean(id.value))
  })

  const saveMutation = useMutation({
    mutationFn: ({ name, config }) => dashboardApi.update(id.value, { name, config }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dashboard', id.value] })
      return queryClient.invalidateQueries({ queryKey: DASHBOARD_LIST_KEY })
    }
  })

  async function save(name, config) {
    return unwrapResult(await saveMutation.mutateAsync({ name, config }), null)
  }

  const detail = computed(() => detailQuery.data.value ?? null)

  return {
    detail,
    config: computed(() => normalizeConfig(detail.value?.config)),
    loading: computed(() => detailQuery.isFetching.value),
    loadError: computed(() => detailQuery.isError.value),
    refetch: () => detailQuery.refetch(),
    saving: computed(() => saveMutation.isPending.value),
    save
  }
}

export default useDashboards
