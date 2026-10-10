import { computed } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { notificationApi } from '@/api/notification'
import { unwrapResult } from '@/utils/result'

const ESCALATION_KEY = ['alert-escalations']

/** 升级策略列表查询（T-27 设计文档 §10.1）。 */
export function useAlertEscalationsQuery() {
  const listQuery = useQuery({
    queryKey: ESCALATION_KEY,
    queryFn: async () => unwrapResult(await notificationApi.listEscalations(), [])
  })

  return {
    escalations: computed(() => listQuery.data.value ?? []),
    loading: computed(() => listQuery.isFetching.value),
    refresh: () => listQuery.refetch()
  }
}

/** 升级策略增删改；成功后失效策略列表。 */
export function useAlertEscalationMutations() {
  const queryClient = useQueryClient()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ESCALATION_KEY })

  const createMutation = useMutation({
    mutationFn: (payload) => notificationApi.createEscalation(payload),
    onSuccess: invalidate
  })
  const updateMutation = useMutation({
    mutationFn: ({ id, payload }) => notificationApi.updateEscalation(id, payload),
    onSuccess: invalidate
  })
  const deleteMutation = useMutation({
    mutationFn: (id) => notificationApi.deleteEscalation(id),
    onSuccess: invalidate
  })

  return {
    saving: computed(() => createMutation.isPending.value || updateMutation.isPending.value),
    deleting: computed(() => deleteMutation.isPending.value),
    createEscalation: (payload) => createMutation.mutateAsync(payload),
    updateEscalation: (id, payload) => updateMutation.mutateAsync({ id, payload }),
    deleteEscalation: (id) => deleteMutation.mutateAsync(id)
  }
}