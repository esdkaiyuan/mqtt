import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { deviceApi } from '@/api/device'
import { unwrapResult } from '@/utils/result'

const EMPTY_PAGE = { records: [], total: 0 }
const DEFAULT_PAGE_SIZE = 20
/** 默认时间窗：近 24 小时（后端跨度上限 31 天，见 T-20 设计文档 §6.1）。 */
const DEFAULT_RANGE_HOURS = 24

const pad = (value) => String(value).padStart(2, '0')

/** 与后端一致的时间格式：`yyyy-MM-dd HH:mm:ss`。 */
function formatDateTime(date) {
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

function defaultRange() {
  const end = new Date()
  const start = new Date(end.getTime() - DEFAULT_RANGE_HOURS * 60 * 60 * 1000)
  return [formatDateTime(start), formatDateTime(end)]
}

function defaultCriteria() {
  return { types: [], range: defaultRange(), keyword: '' }
}

/** 只把有值的筛选项放进请求参数；types 逗号分隔以匹配后端 `List<String>` 绑定。 */
function buildParams(criteria, pageNum, pageSize) {
  const params = { pageNum, pageSize }
  if (criteria.types?.length) params.types = criteria.types.join(',')
  if (criteria.range?.[0]) params.startTime = criteria.range[0]
  if (criteria.range?.[1]) params.endTime = criteria.range[1]
  if (criteria.keyword) params.keyword = criteria.keyword
  return params
}

/**
 * 设备日志时间线（T-20）。
 *
 * 聚合报文 / 命令 / 事件 / 状态变更四类日志，按发生时间倒序分页。
 * 默认查询「近 24 小时」，进入页面即可见数据；筛选表单是草稿，
 * 只有点击查询才写入已提交条件并触发请求。
 *
 * `deviceId` 取自路由参数，通常进入页面即有值，`enabled` 兜底避免空请求。
 *
 * @param {import('vue').Ref<number|string>} deviceId
 */
export function useDeviceLog(deviceId) {
  const form = reactive(defaultCriteria())
  const submitted = ref(defaultCriteria())
  // 只在点「查询」/「重置」时自增：条件未变也能重新拉取，而翻页不改变它，仍能命中缓存
  const searchSeq = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(DEFAULT_PAGE_SIZE)

  const queryParams = computed(() => buildParams(submitted.value, pageNum.value, pageSize.value))

  const logQuery = useQuery({
    queryKey: computed(() => ['device-logs', deviceId.value, queryParams.value, searchSeq.value]),
    queryFn: async () =>
      unwrapResult(await deviceApi.getDeviceLogs(deviceId.value, queryParams.value), EMPTY_PAGE),
    enabled: computed(() => Boolean(deviceId.value))
  })

  const items = computed(() => logQuery.data.value?.records ?? [])
  const total = computed(() => logQuery.data.value?.total ?? 0)
  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
  const loading = computed(() => logQuery.isFetching.value)

  function submit() {
    pageNum.value = 1
    submitted.value = { types: [...form.types], range: [...form.range], keyword: form.keyword }
    searchSeq.value += 1
  }

  function reset() {
    const next = defaultCriteria()
    form.types = next.types
    form.range = next.range
    form.keyword = next.keyword
    submitted.value = next
    pageNum.value = 1
    searchSeq.value += 1
  }

  function goToPage(page) {
    if (page < 1 || page > totalPages.value) return
    pageNum.value = page
  }

  return {
    form,
    items,
    total,
    totalPages,
    loading,
    pageNum,
    pageSize,
    submit,
    reset,
    goToPage
  }
}

export default useDeviceLog
