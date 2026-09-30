import { computed, onMounted, reactive, ref } from 'vue'
import { useDeviceStore } from '@/stores/device'

const DEFAULT_PAGE_SIZE = 10

/**
 * 设备列表的筛选 / 分页状态与数据加载。
 * 数据源仍是 device store（设备列表被多个页面复用），
 * 本 composable 只负责查询条件的组织与页码边界处理。
 */
export function useDeviceList() {
  const deviceStore = useDeviceStore()

  const filters = reactive({
    deviceName: '',
    deviceType: '',
    status: ''
  })

  const currentPage = ref(1)
  const pageSize = ref(DEFAULT_PAGE_SIZE)
  const totalPages = computed(() => Math.ceil(deviceStore.totalDevices / pageSize.value))
  const devices = computed(() => deviceStore.devices)
  const loading = computed(() => deviceStore.loading)

  async function loadDevices(page = 1) {
    currentPage.value = page
    await deviceStore.fetchDevices({
      pageNum: page,
      pageSize: pageSize.value,
      deviceName: filters.deviceName || undefined,
      deviceType: filters.deviceType || undefined,
      status: filters.status || undefined
    })
  }

  function handleSearch() {
    loadDevices(1)
  }

  function handleReset() {
    filters.deviceName = ''
    filters.deviceType = ''
    filters.status = ''
    loadDevices(1)
  }

  function goToPage(page) {
    if (page >= 1 && page <= totalPages.value) {
      loadDevices(page)
    }
  }

  onMounted(() => loadDevices())

  return {
    filters,
    currentPage,
    pageSize,
    totalPages,
    devices,
    loading,
    loadDevices,
    handleSearch,
    handleReset,
    goToPage
  }
}

export default useDeviceList
