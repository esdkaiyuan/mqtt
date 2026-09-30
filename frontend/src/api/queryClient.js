import { QueryClient } from '@tanstack/vue-query'

/**
 * 全局 QueryClient 单例。
 *
 * 分层约定：
 * - 只把「服务端状态」交给 Vue Query（列表、统计、详情等），
 *   纯 UI 状态（弹窗开关、表单草稿、连接状态）仍留在组件或 Pinia。
 * - staleTime 30s：仪表盘/列表这类后台数据不需要秒级新鲜度，
 *   短时间内切页直接复用缓存，避免重复请求。
 * - retry 1 次：与 axios 的 10s 超时配合，故障时不至于长时间卡住 UI。
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30 * 1000,
      gcTime: 5 * 60 * 1000,
      retry: 1,
      refetchOnWindowFocus: false
    },
    mutations: {
      retry: 0
    }
  }
})

export default queryClient
