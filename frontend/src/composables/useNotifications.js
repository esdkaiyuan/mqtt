import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { notificationApi } from '@/api/notification'
import { unwrapResult } from '@/utils/result'

const DEFAULT_PAGE_SIZE = 20
const UNREAD_POLL_MS = 30 * 1000
const EMPTY_PAGE = { records: [], total: 0 }

/**
 * 失效站内信相关缓存：列表、未读数、顶栏最近条目。
 * 标记已读 / 删除 / 清空都会改变这三者，统一在此收敛，避免各处遗漏。
 */
function invalidateNotificationCaches(queryClient) {
  queryClient.invalidateQueries({ queryKey: ['notifications'] })
  queryClient.invalidateQueries({ queryKey: ['notification-unread-count'] })
  queryClient.invalidateQueries({ queryKey: ['notification-recent'] })
}

/**
 * 站内信分页查询（T-27 设计文档 §10.1）。
 *
 * 「仅看未读」走服务端 `unreadOnly`，与分页参数同键缓存；筛选即时生效。
 */
export function useNotificationListQuery() {
  const unreadOnly = ref(false)
  const currentPage = ref(1)
  const pageSize = ref(DEFAULT_PAGE_SIZE)

  const params = computed(() => {
    const query = { pageNum: currentPage.value, pageSize: pageSize.value }
    if (unreadOnly.value) query.unreadOnly = true
    return query
  })

  const listQuery = useQuery({
    queryKey: computed(() => ['notifications', params.value]),
    queryFn: async () => unwrapResult(await notificationApi.listNotifications(params.value), EMPTY_PAGE)
  })

  return {
    unreadOnly,
    currentPage,
    pageSize,
    notifications: computed(() => listQuery.data.value?.records ?? []),
    total: computed(() => listQuery.data.value?.total ?? 0),
    loading: computed(() => listQuery.isFetching.value),
    setUnreadOnly(value) {
      unreadOnly.value = value
      currentPage.value = 1
    },
    goToPage(page) {
      if (page >= 1) currentPage.value = page
    },
    changePageSize(size) {
      pageSize.value = size
      currentPage.value = 1
    },
    refresh: () => listQuery.refetch()
  }
}

/** 未读数：按 30s 轮询，与顶栏健康度节奏一致；失败回退 0 不阻塞渲染。 */
export function useNotificationUnreadCountQuery() {
  const unreadQuery = useQuery({
    queryKey: ['notification-unread-count'],
    queryFn: async () => unwrapResult(await notificationApi.unreadCount(), 0),
    refetchInterval: UNREAD_POLL_MS
  })

  return {
    unreadCount: computed(() => unreadQuery.data.value ?? 0),
    loading: computed(() => unreadQuery.isFetching.value),
    refresh: () => unreadQuery.refetch()
  }
}

/** 顶栏最近站内信：与未读数同频轮询，避免两处节奏不一致。 */
export function useNotificationRecentQuery(limit = 10) {
  const recentQuery = useQuery({
    queryKey: ['notification-recent', limit],
    queryFn: async () => unwrapResult(await notificationApi.listNotifications({ pageNum: 1, pageSize: limit }), EMPTY_PAGE),
    select: (page) => page?.records ?? [],
    refetchInterval: UNREAD_POLL_MS
  })

  return {
    recentNotifications: computed(() => recentQuery.data.value ?? []),
    loading: computed(() => recentQuery.isFetching.value),
    refresh: () => recentQuery.refetch()
  }
}

/** 已读 / 删除 / 清空操作；成功后失效列表与未读数。 */
export function useNotificationMutations() {
  const queryClient = useQueryClient()
  const invalidate = () => invalidateNotificationCaches(queryClient)

  const readMutation = useMutation({
    mutationFn: (id) => notificationApi.markRead(id),
    onSuccess: invalidate
  })
  const readAllMutation = useMutation({
    mutationFn: () => notificationApi.markAllRead(),
    onSuccess: invalidate
  })
  const deleteMutation = useMutation({
    mutationFn: (id) => notificationApi.deleteNotification(id),
    onSuccess: invalidate
  })
  const clearMutation = useMutation({
    mutationFn: () => notificationApi.clearRead(),
    onSuccess: invalidate
  })

  return {
    reading: computed(() => readMutation.isPending.value || readAllMutation.isPending.value),
    deleting: computed(() => deleteMutation.isPending.value || clearMutation.isPending.value),
    markRead: (id) => readMutation.mutateAsync(id),
    markAllRead: () => readAllMutation.mutateAsync(),
    deleteNotification: (id) => deleteMutation.mutateAsync(id),
    clearRead: () => clearMutation.mutateAsync()
  }
}