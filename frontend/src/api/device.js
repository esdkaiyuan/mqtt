import api from './axios'

export const deviceApi = {
  /**
   * 创建设备
   */
  create: (data) => api.post('/devices', data),

  /**
   * 获取设备列表
   */
  getList: (params) => api.get('/devices', { params }),

  /**
   * 获取设备详情
   */
  getDetail: (deviceId) => api.get(`/devices/${deviceId}`),

  /**
   * 更新设备
   */
  update: (deviceId, data) => api.put(`/devices/${deviceId}`, data),

  /**
   * 删除设备
   */
  delete: (deviceId) => api.delete(`/devices/${deviceId}`),

  /**
   * 获取在线设备
   */
  getOnline: () => api.get('/devices/online'),

  /**
   * 获取设备状态历史
   */
  getStatusHistory: (deviceId) => api.get(`/devices/${deviceId}/status`)
}
