import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { propertyHistoryApi } from '@/api/propertyHistory'
import { unwrapResult } from '@/utils/result'

/** 无数据时的兜底值：后端对每个「设备 × 属性」组合都会返回序列，故空态是空数组。 */
const EMPTY = []

/** 默认时间窗：近 24 小时（后端跨度上限见 app.property-history.max-range-days）。 */
export const DEFAULT_RANGE_HOURS = 24

/** 默认桶粒度。 */
export const DEFAULT_BUCKET = '5m'

/** 时间桶白名单，与后端 `PropertyHistoryConstants` 对齐（非法值后端返回 6227）。 */
export const BUCKET_OPTIONS = [
  { label: '1 分钟', value: '1m' },
  { label: '5 分钟', value: '5m' },
  { label: '15 分钟', value: '15m' },
  { label: '30 分钟', value: '30m' },
  { label: '1 小时', value: '1h' },
  { label: '6 小时', value: '6h' },
  { label: '1 天', value: '1d' }
]

/** 时间范围快捷项。 */
export const RANGE_PRESETS = [
  { label: '近 1 小时', hours: 1 },
  { label: '近 24 小时', hours: 24 },
  { label: '近 7 天', hours: 24 * 7 }
]

const pad = (value) => String(value).padStart(2, '0')

/** 与后端一致的时间格式：`yyyy-MM-dd HH:mm:ss`。 */
export function formatDateTime(date) {
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

/** 以「当前时刻」为终点往回推 hours 小时，返回 `[start, end]`。 */
export function rangeOfHours(hours) {
  const end = new Date()
  const start = new Date(end.getTime() - hours * 60 * 60 * 1000)
  return [formatDateTime(start), formatDateTime(end)]
}

/** 只把有值的筛选项放进请求参数；多值用逗号分隔以匹配后端 List 绑定。 */
export function buildPropertyHistoryParams(deviceIds, identifiers, criteria) {
  const params = {}
  if (deviceIds?.length) params.deviceIds = deviceIds.join(',')
  if (identifiers?.length) params.identifiers = identifiers.join(',')
  if (criteria.range?.[0]) params.startTime = criteria.range[0]
  if (criteria.range?.[1]) params.endTime = criteria.range[1]
  if (criteria.bucket) params.bucket = criteria.bucket
  return params
}

/**
 * 属性时序查询（Vue Query，照 useDeviceLog.js 模板）。
 *
 * - `deviceIds` / `identifiers` 由调用方以 computed 传入，二者都非空才发请求（`enabled`）；
 * - `form` 是工具栏直接绑定的草稿，`submitted` 是真正查询用的快照；
 * - `searchSeq` 只在点「查询 / 重置 / 快捷范围」时自增：条件未变也能重新拉取，
 *   而仅切换属性或设备时不改变它，仍能命中缓存。
 *
 * @param {import('vue').ComputedRef<Array<number|string>>} deviceIds
 * @param {import('vue').ComputedRef<string[]>} identifiers
 */
export function usePropertyHistory(deviceIds, identifiers) {
  const form = reactive({ range: rangeOfHours(DEFAULT_RANGE_HOURS), bucket: DEFAULT_BUCKET })
  const submitted = ref({ range: [...form.range], bucket: form.bucket })
  const searchSeq = ref(0)

  const queryParams = computed(() =>
    buildPropertyHistoryParams(deviceIds.value, identifiers.value, submitted.value)
  )

  const historyQuery = useQuery({
    queryKey: computed(() => ['property-history', queryParams.value, searchSeq.value]),
    queryFn: async () => unwrapResult(await propertyHistoryApi.query(queryParams.value), EMPTY),
    enabled: computed(() => Boolean(deviceIds.value?.length && identifiers.value?.length))
  })

  const series = computed(() => historyQuery.data.value ?? [])
  const loading = computed(() => historyQuery.isFetching.value)

  function commit() {
    submitted.value = { range: [...form.range], bucket: form.bucket }
    searchSeq.value += 1
  }

  /** 应用工具栏当前条件并重新查询。 */
  function submit() {
    commit()
  }

  /** 恢复默认时间窗与桶粒度并重新查询。 */
  function reset() {
    form.range = rangeOfHours(DEFAULT_RANGE_HOURS)
    form.bucket = DEFAULT_BUCKET
    commit()
  }

  /** 时间范围快捷：改成对应窗口后立即查询。 */
  function applyPreset(hours) {
    form.range = rangeOfHours(hours)
    commit()
  }

  return { form, series, loading, submit, reset, applyPreset }
}

export default usePropertyHistory
