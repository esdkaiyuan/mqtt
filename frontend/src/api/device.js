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
  getStatusHistory: (deviceId) => api.get(`/devices/${deviceId}/status`),

  // ---------- 物模型派生数据（T-14 只读） ----------

  /**
   * 属性最新值列表（按 deviceKey 归属校验）
   */
  getProperties: (deviceKey) => api.get(`/devices/${deviceKey}/properties`),

  /**
   * 事件记录分页（按 deviceKey 归属校验，size 上限 100）
   */
  getEvents: (deviceKey, params) => api.get(`/devices/${deviceKey}/events`, { params })
}
