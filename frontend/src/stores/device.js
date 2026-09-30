import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api/axios'

export const useDeviceStore = defineStore('device', () => {
  const devices = ref([])
  const totalDevices = ref(0)
  const loading = ref(false)

  const onlineCount = computed(() => devices.value.filter(d => d.status === 'ONLINE').length)
  const offlineCount = computed(() => totalDevices.value - onlineCount.value)

  async function fetchDevices(params = {}) {
    loading.value = true
    try {
      const result = await api.get('/devices', { params })
      // Interceptor returns Result.data which is the IPage object (dict)
      const page = result.data || result
      if (page && page.records) {
        devices.value = page.records
        totalDevices.value = page.total || 0
      } else {
        devices.value = []
        totalDevices.value = 0
      }
      return page
    } finally {
      loading.value = false
    }
  }

  async function createDevice(deviceData) {
    const result = await api.post('/devices', deviceData)
    // 创建接口返回 DeviceCreatedDTO（含一次性明文密钥），其字段与列表项
    // 不同（缺少 status/deviceType/topic），直接插入会导致表格行渲染异常，
    // 因此只返回该 DTO，由调用方刷新列表。
    const created = result.data || result
    ElMessage.success('设备创建成功')
    return created
  }

  async function updateDevice(deviceId, deviceData) {
    const result = await api.put(`/devices/${deviceId}`, deviceData)
    const updated = result.data || result
    const index = devices.value.findIndex(d => d.id === deviceId)
    if (index !== -1) {
      devices.value[index] = updated
    }
    ElMessage.success('设备更新成功')
    return updated
  }

  async function deleteDevice(deviceId) {
    await api.delete(`/devices/${deviceId}`)
    devices.value = devices.value.filter(d => d.id !== deviceId)
    totalDevices.value = Math.max(0, totalDevices.value - 1)
    ElMessage.success('设备已删除')
  }

  async function fetchOnlineDevices() {
    try {
      const result = await api.get('/devices/online')
      // result.data is the array of online devices
      return result.data || result || []
    } catch (error) {
      console.error('获取在线设备失败:', error)
      return []
    }
  }

  async function fetchDeviceById(deviceId) {
    const result = await api.get(`/devices/${deviceId}`)
    return result.data || result
  }

  async function fetchDeviceStatusHistory(deviceId) {
    const result = await api.get(`/devices/${deviceId}/status`)
    return result.data || result || []
  }

  return {
    devices,
    totalDevices,
    loading,
    onlineCount,
    offlineCount,
    fetchDevices,
    createDevice,
    updateDevice,
    deleteDevice,
    fetchOnlineDevices,
    fetchDeviceById,
    fetchDeviceStatusHistory
  }
})
