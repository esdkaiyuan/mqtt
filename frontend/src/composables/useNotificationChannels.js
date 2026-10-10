import { computed } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { notificationApi } from '@/api/notification'
import { unwrapResult } from '@/utils/result'

const CHANNEL_KEY = ['notification-channels']

/** 渠道列表查询（T-27 设计文档 §10.1）；响应中的敏感字段已由后端脱敏。 */
export function useNotificationChannelsQuery() {
  const listQuery = useQuery({
    queryKey: CHANNEL_KEY,
    queryFn: async () => unwrapResult(await notificationApi.listChannels(), [])
  })

  return {
    channels: computed(() => listQuery.data.value ?? []),
    loading: computed(() => listQuery.isFetching.value),
    refresh: () => listQuery.refetch()
  }
}

/** 渠道增删改与连通性测试；增删改成功后失效渠道列表。 */
export function useNotificationChannelMutations() {
  const queryClient = useQueryClient()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: CHANNEL_KEY })

  const createMutation = useMutation({
    mutationFn: (payload) => notificationApi.createChannel(payload),
    onSuccess: invalidate
  })
  const updateMutation = useMutation({
    mutationFn: ({ id, payload }) => notificationApi.updateChannel(id, payload),
    onSuccess: invalidate
  })
  const deleteMutation = useMutation({
    mutationFn: (id) => notificationApi.deleteChannel(id),
    onSuccess: invalidate
  })
  const testMutation = useMutation({
    mutationFn: (id) => notificationApi.testChannel(id)
  })

  return {
    saving: computed(() => createMutation.isPending.value || updateMutation.isPending.value),
    deleting: computed(() => deleteMutation.isPending.value),
    testing: computed(() => testMutation.isPending.value),
    createChannel: (payload) => createMutation.mutateAsync(payload),
    updateChannel: (id, payload) => updateMutation.mutateAsync({ id, payload }),
    deleteChannel: (id) => deleteMutation.mutateAsync(id),
    testChannel: (id) => testMutation.mutateAsync(id)
  }
}