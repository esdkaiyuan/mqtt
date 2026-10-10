import { ref } from 'vue'
import { deviceApi } from '@/api/device'
import { unwrapResult } from '@/utils/result'

/** 子设备一次拉取的上限，与后端 pageSize 最大值 100 对齐 */
export const SUB_DEVICE_PAGE_SIZE = 100

/** 拓扑角色展示标签，与后端 DeviceNodeType 常量一一对应 */
export const NODE_TYPE_LABELS = {
  DIRECT: '普通设备',
  GATEWAY: '网关',
  SUB: '子设备'
}

/**
 * 设备拓扑（T-26）的服务端动作。
 *
 * - 子设备列表：网关详情面板的数据源；
 * - 候选设备：挂载对话框的数据源，按网关所属产品取普通设备；
 * - 挂载 / 解绑：成功后重载列表，保证面板与服务端一致；
 * - 角色变更：切换拓扑角色，取值由 DeviceNodeType 约束；
 * - 父网关查询：子设备详情页头部展示所属网关。
 *
 * 数据访问统一经 `@/api/*`，响应经 `unwrapResult` 解包；
 * 设备 ID 未就绪时（详情页首次渲染）直接短路，避免打出无意义的 404。
 *
 * @param {import('vue').Ref<number|string|null>} deviceId 当前设备 ID，网关动作时即网关 ID
 */
export function useDeviceTopology(deviceId) {
  const subDevices = ref([])
  const parent = ref(null)
  const candidates = ref([])

  const subDevicesLoading = ref(false)
  const parentLoading = ref(false)
  const candidatesLoading = ref(false)
  const attaching = ref(false)
  const detaching = ref(false)
  const nodeTypeChanging = ref(false)

  async function loadSubDevices() {
    if (!deviceId.value) {
      subDevices.value = []
      return subDevices.value
    }
    subDevicesLoading.value = true
    try {
      const page = unwrapResult(
        await deviceApi.listSubDevices(deviceId.value, {
          pageNum: 1,
          pageSize: SUB_DEVICE_PAGE_SIZE
        }),
        null
      )
      subDevices.value = page?.records ?? []
      return subDevices.value
    } finally {
      subDevicesLoading.value = false
    }
  }

  /**
   * 拉取可挂载到当前网关的候选设备。
   *
   * 服务端跨产品挂载会被拒（6258），故按网关所属产品过滤，
   * 且只取仍为普通设备（DIRECT）的行，网关与子设备不再作为候选。
   *
   * @param {number|string|null} productId 网关所属产品 ID
   */
  async function loadCandidates(productId) {
    if (!productId) {
      candidates.value = []
      return candidates.value
    }
    candidatesLoading.value = true
    try {
      const page = unwrapResult(
        await deviceApi.getList({
          nodeType: 'DIRECT',
          productId,
          pageNum: 1,
          pageSize: SUB_DEVICE_PAGE_SIZE
        }),
        null
      )
      candidates.value = page?.records ?? []
      return candidates.value
    } finally {
      candidatesLoading.value = false
    }
  }

  async function attachSubDevice(subDeviceId) {
    if (!deviceId.value || !subDeviceId) return null
    attaching.value = true
    try {
      const device = unwrapResult(await deviceApi.attachSubDevice(deviceId.value, subDeviceId), null)
      await loadSubDevices()
      return device
    } finally {
      attaching.value = false
    }
  }

  async function detachSubDevice(subDeviceId) {
    if (!deviceId.value || !subDeviceId) return false
    detaching.value = true
    try {
      await deviceApi.detachSubDevice(deviceId.value, subDeviceId)
      await loadSubDevices()
      return true
    } finally {
      detaching.value = false
    }
  }

  async function changeNodeType(nodeType) {
    if (!deviceId.value || !nodeType) return null
    nodeTypeChanging.value = true
    try {
      return unwrapResult(await deviceApi.changeNodeType(deviceId.value, nodeType), null)
    } finally {
      nodeTypeChanging.value = false
    }
  }

  async function loadParent() {
    if (!deviceId.value) {
      parent.value = null
      return null
    }
    parentLoading.value = true
    try {
      parent.value = unwrapResult(await deviceApi.getParentDevice(deviceId.value), null)
      return parent.value
    } finally {
      parentLoading.value = false
    }
  }

  return {
    subDevices,
    parent,
    candidates,
    subDevicesLoading,
    parentLoading,
    candidatesLoading,
    attaching,
    detaching,
    nodeTypeChanging,
    loadSubDevices,
    loadCandidates,
    attachSubDevice,
    detachSubDevice,
    changeNodeType,
    loadParent
  }
}

export default useDeviceTopology
