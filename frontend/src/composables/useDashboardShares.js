import { computed } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { dashboardShareApi } from '@/api/dashboardShare'
import { unwrapResult } from '@/utils/result'

const EMPTY = []
export const DASHBOARD_SHARE_LIST_KEY = ['dashboard-shares']

/** 看板分享列表与增删改 / 令牌轮换（T-46）。 */
export function useDashboardShares() {
  const queryClient = useQueryClient()

  const listQuery = useQuery({
    queryKey: DASHBOARD_SHARE_LIST_KEY,
    queryFn: async () => unwrapResult(await dashboardShareApi.list(), EMPTY)
  })

  function invalidate() {
    return queryClient.invalidateQueries({ queryKey: DASHBOARD_SHARE_LIST_KEY })
  }

  const createMutation = useMutation({
    mutationFn: (data) => dashboardShareApi.create(data),
    onSuccess: invalidate
  })
  const updateMutation = useMutation({
    mutationFn: ({ id, data }) => dashboardShareApi.update(id, data),
    onSuccess: invalidate
  })
  const removeMutation = useMutation({
    mutationFn: (id) => dashboardShareApi.remove(id),
    onSuccess: invalidate
  })
  const rotateMutation = useMutation({
    mutationFn: (id) => dashboardShareApi.rotate(id),
    onSuccess: invalidate
  })

  return {
    shares: computed(() => listQuery.data.value ?? EMPTY),
    loading: computed(() => listQuery.isFetching.value),
    loadError: computed(() => listQuery.isError.value),
    refetch: () => listQuery.refetch(),
    saving: computed(() =>
      createMutation.isPending.value ||
      updateMutation.isPending.value ||
      rotateMutation.isPending.value
    ),
    create: async (data) => unwrapResult(await createMutation.mutateAsync(data), null),
    update: async (id, data) => unwrapResult(await updateMutation.mutateAsync({ id, data }), null),
    remove: async (id) => removeMutation.mutateAsync(id),
    rotate: async (id) => unwrapResult(await rotateMutation.mutateAsync(id), null)
  }
}

export default useDashboardShares
