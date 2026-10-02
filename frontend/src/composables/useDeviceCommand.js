import { computed } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { deviceApi } from '@/api/device'
import { unwrapResult } from '@/utils/result'

const EMPTY_CAPABILITY = { modeled: false, version: 0, properties: [], services: [] }
const EMPTY_PAGE = { records: [], total: 0 }

/**
 * 设备命令下发与服务调用的服务端状态（T-15）。
 *
 * - 可下发能力：`command-capability`，供控制面板生成动态表单；
 * - 命令记录：分页查询，按创建时间倒序；
 * - 下发：mutation，成功后失效记录查询以刷新列表。
 *
 * `deviceKey` 由设备详情加载后才有值，`enabled` 保证未就绪时不发请求。
 *
 * @param {import('vue').Ref<string>} deviceKey
 * @param {import('vue').Ref<number>} page
 * @param {import('vue').Ref<number>} size
 */
export function useDeviceCommand(deviceKey, page, size) {
  const queryClient = useQueryClient()

  const capabilityQuery = useQuery({
    queryKey: computed(() => ['device-command-capability', deviceKey.value]),
    queryFn: async () =>
      unwrapResult(await deviceApi.getCommandCapability(deviceKey.value), EMPTY_CAPABILITY),
    enabled: computed(() => Boolean(deviceKey.value))
  })

  const recordsQuery = useQuery({
    queryKey: computed(() => ['device-command-records', deviceKey.value, page.value, size.value]),
    queryFn: async () =>
      unwrapResult(
        await deviceApi.getCommandRecords(deviceKey.value, { page: page.value, size: size.value }),
        EMPTY_PAGE
      ),
    enabled: computed(() => Boolean(deviceKey.value))
  })

  const invokeMutation = useMutation({
    mutationFn: (payload) => deviceApi.sendCommand(deviceKey.value, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['device-command-records', deviceKey.value] })
    }
  })

  /** 下发命令；失败时错误提示已由 axios 拦截器给出，这里只负责抛出。 */
  async function sendCommand(payload) {
    return unwrapResult(await invokeMutation.mutateAsync(payload), null)
  }

  return {
    capability: computed(() => capabilityQuery.data.value ?? EMPTY_CAPABILITY),
    capabilityLoading: computed(() => capabilityQuery.isFetching.value),
    records: computed(() => recordsQuery.data.value?.records ?? []),
    recordsTotal: computed(() => recordsQuery.data.value?.total ?? 0),
    recordsLoading: computed(() => recordsQuery.isFetching.value),
    sending: computed(() => invokeMutation.isPending.value),
    sendCommand
  }
}

export default useDeviceCommand