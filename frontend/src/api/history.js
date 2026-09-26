import api from './axios'

export const historyApi = {
  /**
   * 查询历史记录（分页+过滤）
   */
  query: (params) => api.get('/history', { params }),

  /**
   * 查询指定设备的历史记录
   */
  getDeviceHistory: (deviceId, params) => api.get(`/history/device/${deviceId}`, { params })
}
