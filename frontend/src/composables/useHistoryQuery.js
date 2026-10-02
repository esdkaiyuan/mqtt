import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { useDeviceStore } from '@/stores/device'
import { historyApi } from '@/api/history'
import { unwrapResult } from '@/utils/result'

const DEFAULT_PAGE_SIZE = 20
const EMPTY_PAGE = { records: [], total: 0 }

/** 只把有值的筛选项放进请求参数，避免空串被后端当作有效条件。 */
function buildParams(criteria, pageNum, pageSize) {
  const params = { pageNum, pageSize }
  if (criteria.deviceId) params.deviceId = criteria.deviceId
  if (criteria.topic) params.topic = criteria.topic
  if (criteria.startTime) params.startTime = criteria.startTime
  if (criteria.endTime) params.endTime = criteria.endTime
  return params
}

/**
 * 历史数据查询页的服务端状态。
 *
 * 表单是「草稿」，只有点击查询时才写入 `submitted` 并触发请求；
 * queryKey 由已提交的筛选条件 + 分页共同构成，翻页/改每页条数都会
 * 命中不同缓存，回退页码可直接复用结果。
 */
export function useHistoryQuery() {
  const deviceStore = useDeviceStore()
  const route = useRoute()
  const deviceOptions = computed(() => deviceStore.devices)

  const form = reactive({ deviceId: '', topic: '', startTime: '', endTime: '' })
  const submitted = ref(null)
  const searchSeq = ref(0)
  const currentPage = ref(1)
  const pageSize = ref(DEFAULT_PAGE_SIZE)

  const hasSearched = computed(() => submitted.value !== null)
  const queryParams = computed(() =>
    submitted.value === null
      ? null
      : buildParams(submitted.value, currentPage.value, pageSize.value)
  )

  // searchSeq 只在点「查询」时自增：条件未变也能重新拉取，
  // 而翻页 / 改每页条数不改变它，因此仍能命中缓存。
  const historyQuery = useQuery({
    queryKey: computed(() => ['history', queryParams.value ?? 'idle', searchSeq.value]),
    queryFn: async () => unwrapResult(await historyApi.query(queryParams.value), EMPTY_PAGE),
    enabled: computed(() => queryParams.value !== null)
  })

  const records = computed(() => historyQuery.data.value?.records ?? [])
  const total = computed(() => historyQuery.data.value?.total ?? 0)
  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
  const loading = computed(() => historyQuery.isFetching.value)

  function submit() {
    currentPage.value = 1
    submitted.value = { ...form }
    searchSeq.value += 1
  }

  function reset() {
    form.deviceId = ''
    form.topic = ''
    form.startTime = ''
    form.endTime = ''
    submitted.value = null
    currentPage.value = 1
  }

  function goToPage(page) {
    if (page < 1 || page > totalPages.value) return
    currentPage.value = page
  }

  function changePageSize(size) {
    pageSize.value = size
    currentPage.value = 1
  }

  onMounted(() => {
    deviceStore.fetchDevices({ pageNum: 1, pageSize: 100 })
    // 从设备详情右栏「查询历史数据」进入时带 deviceId，直接代入并触发一次查询
    const { deviceId } = route.query
    if (typeof deviceId === 'string' && deviceId) {
      form.deviceId = /^\d+$/.test(deviceId) ? Number(deviceId) : deviceId
      submit()
    }
  })

  return {
    form,
    deviceOptions,
    hasSearched,
    records,
    total,
    totalPages,
    loading,
    currentPage,
    pageSize,
    submit,
    reset,
    goToPage,
    changePageSize
  }
}

export default useHistoryQuery
