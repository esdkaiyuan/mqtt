import { ref } from 'vue'
import { useDeviceStore } from '@/stores/device'
import { productApi } from '@/api/product'
import { deviceApi } from '@/api/device'

/** 父网关候选单页上限：同一产品下的网关数量远小于该值，取一页即可 */
export const GATEWAY_PAGE_SIZE = 100

/**
 * 设备增删改的对话框状态与动作。
 * 对话框的可见性、loading、表单草稿都在这里集中管理，
 * 页面壳只负责把 state 绑给组件、把事件接到动作上。
 */
export function useDeviceCrud() {
  const deviceStore = useDeviceStore()

  const showCreateDialog = ref(false)
  const createLoading = ref(false)
  const products = ref([])
  const productsLoading = ref(false)
  const gateways = ref([])
  const gatewaysLoading = ref(false)

  async function loadProducts() {
    productsLoading.value = true
    try {
      const result = await productApi.getList()
      const list = result.data || result || []
      products.value = list.filter(product => product.status === 'ENABLED')
    } catch (error) {
      products.value = []
    } finally {
      productsLoading.value = false
    }
  }

  /**
   * 拉取指定产品下可作为父网关的候选设备。
   * 只取节点类型为 GATEWAY 的行：普通设备与子设备不能作为父节点，
   * 跨产品挂载也会被服务端拒绝（6258），故必须按产品过滤。
   *
   * @param {number|string|null} productId 网关所属产品 ID
   */
  async function loadGateways(productId) {
    if (!productId) {
      gateways.value = []
      return gateways.value
    }
    gatewaysLoading.value = true
    try {
      const result = await deviceApi.getList({
        nodeType: 'GATEWAY',
        productId,
        pageNum: 1,
        pageSize: GATEWAY_PAGE_SIZE
      })
      const page = result?.data || result
      gateways.value = page?.records ?? []
      return gateways.value
    } catch (error) {
      gateways.value = []
      return gateways.value
    } finally {
      gatewaysLoading.value = false
    }
  }

  /** 切换所属产品：候选父网关随产品变化，须整体重取 */
  function handleProductChange(productId) {
    return loadGateways(productId)
  }

  function openCreateDialog() {
    showCreateDialog.value = true
    loadProducts()
  }

  const showCredentialDialog = ref(false)
  const createdCredential = ref({ username: '', deviceSecret: '' })

  /**
   * 把刚创建的设备挂载到所选网关。
   * 挂载失败不回滚设备：设备行与凭据已生成，可在网关详情页重新挂载。
   */
  async function attachToGateway(gatewayId, subDeviceId) {
    try {
      await deviceApi.attachSubDevice(gatewayId, subDeviceId)
    } catch (error) {
      ElMessage.warning('设备已创建，但挂载到所选网关失败，可在网关详情页重新挂载')
    }
  }

  /**
   * @returns {Promise<boolean>} 是否创建成功（失败原因由 axios 拦截器统一提示）
   */
  async function createDevice(form) {
    createLoading.value = true
    try {
      // CreateDeviceDTO 不含父网关字段，父子关系只能由创建成功后的挂载端点建立
      const { parentGatewayId, ...payload } = form
      const created = await deviceStore.createDevice(payload)
      if (parentGatewayId && created?.id) {
        await attachToGateway(parentGatewayId, created.id)
      }
      showCreateDialog.value = false
      createdCredential.value = {
        username: created?.username || '',
        deviceSecret: created?.deviceSecret || ''
      }
      showCredentialDialog.value = true
      return true
    } catch (error) {
      return false
    } finally {
      createLoading.value = false
    }
  }

  const showEditDialog = ref(false)
  const editLoading = ref(false)
  const editingDevice = ref(null)
  const parentGateway = ref(null)

  /**
   * 查询待编辑设备的所属网关。
   * 只有子设备才有父节点，其余节点类型直接跳过，避免无谓的失败请求；
   * 查询失败按无父网关处理，编辑对话框不显示该行即可。
   *
   * @param {object|null} device 列表行，含 id 与 nodeType
   */
  async function loadParentGateway(device) {
    parentGateway.value = null
    if (!device?.id || device.nodeType !== 'SUB') {
      return null
    }
    try {
      const result = await deviceApi.getParentDevice(device.id)
      parentGateway.value = result?.data || result || null
    } catch (error) {
      parentGateway.value = null
    }
    return parentGateway.value
  }

  function openEditDialog(device) {
    editingDevice.value = device
    showEditDialog.value = true
    loadParentGateway(device)
  }

  async function updateDevice(form) {
    editLoading.value = true
    try {
      await deviceStore.updateDevice(form.id, form)
      showEditDialog.value = false
      return true
    } catch (error) {
      return false
    } finally {
      editLoading.value = false
    }
  }

  async function deleteDevice(device) {
    try {
      await ElMessageBox.confirm(
        `确定要删除设备"${device.deviceName}"吗？此操作不可恢复。`,
        '确认删除',
        {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
      await deviceStore.deleteDevice(device.id)
    } catch (error) {
      // 用户取消确认对话框时 ElMessageBox 会 reject；接口失败亦由拦截器提示，这里静默即可
    }
  }

  return {
    showCreateDialog,
    createLoading,
    products,
    productsLoading,
    gateways,
    gatewaysLoading,
    loadGateways,
    handleProductChange,
    openCreateDialog,
    createDevice,
    showCredentialDialog,
    createdCredential,
    showEditDialog,
    editLoading,
    editingDevice,
    parentGateway,
    loadParentGateway,
    openEditDialog,
    updateDevice,
    deleteDevice
  }
}

export default useDeviceCrud
