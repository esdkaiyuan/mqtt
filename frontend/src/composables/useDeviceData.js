import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { deviceApi } from '@/api/device'
import { unwrapResult } from '@/utils/result'

const EMPTY_PAGE = { records: [], total: 0 }

/**
 * 设备详情页的物模型派生数据（只读）。
 *
 * - 属性最新值：`device_property_latest` 中该设备每个标识符的最新一条；
 * - 事件记录：`device_event_record` 分页（按上报时间倒序）。
 *
 * `deviceKey` 由设备详情加载后才有值，`enabled` 保证未就绪时不发请求。
 *
 * @param {import('vue').Ref<string>} deviceKey
 * @param {import('vue').Ref<number>} page
 * @param {import('vue').Ref<number>} size
 */
export function useDeviceData(deviceKey, page, size) {
  const propertiesQuery = useQuery({
    queryKey: computed(() => ['device-properties', deviceKey.value]),
    queryFn: async () => unwrapResult(await deviceApi.getProperties(deviceKey.value), []),
    enabled: computed(() => Boolean(deviceKey.value))
  })

  const eventsQuery = useQuery({
    queryKey: computed(() => ['device-events', deviceKey.value, page.value, size.value]),
    queryFn: async () =>
      unwrapResult(
        await deviceApi.getEvents(deviceKey.value, { page: page.value, size: size.value }),
        EMPTY_PAGE
      ),
    enabled: computed(() => Boolean(deviceKey.value))
  })

  return {
    properties: computed(() => propertiesQuery.data.value ?? []),
    propertiesLoading: computed(() => propertiesQuery.isFetching.value),
    events: computed(() => eventsQuery.data.value?.records ?? []),
    eventsTotal: computed(() => eventsQuery.data.value?.total ?? 0),
    eventsLoading: computed(() => eventsQuery.isFetching.value)
  }
}

export default useDeviceData