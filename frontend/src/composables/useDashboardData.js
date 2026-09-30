import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { statsApi } from '@/api/stats'
import { messageApi } from '@/api/message'
import { unwrapResult } from '@/utils/result'

const RECENT_LIMIT = 10

/**
 * 仪表盘的服务端状态。
 *
 * 五个查询各自缓存、各自重试；queryKey 以 `dashboard` 开头，
 * 后续页面的写操作可用 `invalidateQueries(['dashboard'])` 精准失效。
 *
 * @param {import('vue').Ref<number>} trendDays 趋势图天数（7/14/30），变更即重新查询
 */
export function useDashboardData(trendDays) {
  const overviewQuery = useQuery({
    queryKey: ['dashboard', 'overview'],
    queryFn: async () => unwrapResult(await statsApi.getOverview(), {})
  })

  const statusQuery = useQuery({
    queryKey: ['dashboard', 'device-status'],
    queryFn: async () => unwrapResult(await statsApi.getDeviceStatusDistribution(), [])
  })

  const typeQuery = useQuery({
    queryKey: ['dashboard', 'device-type'],
    queryFn: async () => unwrapResult(await statsApi.getDeviceTypeDistribution(), [])
  })

  const trendQuery = useQuery({
    queryKey: computed(() => ['dashboard', 'message-trend', trendDays.value]),
    queryFn: async () => unwrapResult(await statsApi.getMessageTrend(trendDays.value), [])
  })

  const recentQuery = useQuery({
    queryKey: ['dashboard', 'recent-messages', RECENT_LIMIT],
    queryFn: async () => unwrapResult(await messageApi.getRecent(RECENT_LIMIT), [])
  })

  const queries = [overviewQuery, statusQuery, typeQuery, trendQuery, recentQuery]

  return {
    overview: computed(() => overviewQuery.data.value ?? {}),
    statusData: computed(() => statusQuery.data.value ?? []),
    typeData: computed(() => typeQuery.data.value ?? []),
    trendData: computed(() => trendQuery.data.value ?? []),
    recentMessages: computed(() => recentQuery.data.value ?? []),
    loading: computed(() => queries.some((q) => q.isFetching.value)),
    refetchAll: () => Promise.all(queries.map((q) => q.refetch()))
  }
}

export default useDashboardData
