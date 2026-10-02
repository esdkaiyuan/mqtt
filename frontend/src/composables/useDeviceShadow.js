import { computed } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { deviceApi } from '@/api/device'
import { unwrapResult } from '@/utils/result'

const EMPTY_SHADOW = {
  modeled: false,
  version: 0,
  desired: {},
  reported: {},
  delta: {},
  updatedAt: null
}

/**
 * 设备影子的服务端状态（T-16）。
 *
 * - 影子查询：`device-shadow`，返回 desired / reported / delta 与版本号，
 *   影子不存在时后端返回空映射与 version=0，前端可安全渲染空态；
 * - 期望值写入：mutation，成功后失效影子查询与命令记录查询（离线写入会产生 QUEUED 记录）。
 *
 * `deviceKey` 由设备详情加载后才有值，`enabled` 保证未就绪时不发请求。
 *
 * @param {import('vue').Ref<string>} deviceKey
 */
export function useDeviceShadow(deviceKey) {
  const queryClient = useQueryClient()

  const shadowQuery = useQuery({
    queryKey: computed(() => ['device-shadow', deviceKey.value]),
    queryFn: async () =>
      unwrapResult(await deviceApi.getDeviceShadow(deviceKey.value), EMPTY_SHADOW),
    enabled: computed(() => Boolean(deviceKey.value))
  })

  const setDesiredMutation = useMutation({
    mutationFn: (params) => deviceApi.setDeviceShadowDesired(deviceKey.value, params),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['device-shadow', deviceKey.value] })
      queryClient.invalidateQueries({ queryKey: ['device-command-records', deviceKey.value] })
    }
  })

  /** 写入期望值；失败时错误提示已由 axios 拦截器给出，这里只负责抛出。 */
  async function setDesired(params) {
    return unwrapResult(await setDesiredMutation.mutateAsync(params), null)
  }

  const shadow = computed(() => shadowQuery.data.value ?? EMPTY_SHADOW)

  return {
    shadow,
    modeled: computed(() => shadow.value.modeled),
    version: computed(() => shadow.value.version ?? 0),
    desired: computed(() => shadow.value.desired ?? {}),
    reported: computed(() => shadow.value.reported ?? {}),
    delta: computed(() => shadow.value.delta ?? {}),
    updatedAt: computed(() => shadow.value.updatedAt ?? null),
    shadowLoading: computed(() => shadowQuery.isFetching.value),
    setting: computed(() => setDesiredMutation.isPending.value),
    setDesired
  }
}

export default useDeviceShadow
