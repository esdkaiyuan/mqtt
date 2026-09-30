import { ref } from 'vue'
import { useDeviceStore } from '@/stores/device'
import { productApi } from '@/api/product'

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

  function openCreateDialog() {
    showCreateDialog.value = true
    loadProducts()
  }

  const showCredentialDialog = ref(false)
  const createdCredential = ref({ username: '', deviceSecret: '' })

  /**
   * @returns {Promise<boolean>} 是否创建成功（失败原因由 axios 拦截器统一提示）
   */
  async function createDevice(form) {
    createLoading.value = true
    try {
      const created = await deviceStore.createDevice({ ...form })
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

  function openEditDialog(device) {
    editingDevice.value = device
    showEditDialog.value = true
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
    openCreateDialog,
    createDevice,
    showCredentialDialog,
    createdCredential,
    showEditDialog,
    editLoading,
    editingDevice,
    openEditDialog,
    updateDevice,
    deleteDevice
  }
}

export default useDeviceCrud
