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
  getEvents: (deviceKey, params) => api.get(`/devices/${deviceKey}/events`, { params }),

  // ---------- 命令下发与服务调用（T-15） ----------

  /**
   * 可下发能力：rw 属性与服务入参，供前端生成动态表单
   * 无物模型时 modeled=false 且两个集合为空
   */
  getCommandCapability: (deviceKey) => api.get(`/devices/${deviceKey}/command-capability`),

  /**
   * 下发命令：type=property_set / service，callType=sync 等待回执到终态
   */
  sendCommand: (deviceKey, data) => api.post(`/devices/${deviceKey}/commands`, data),

  /**
   * 命令记录分页（按创建时间倒序）
   */
  getCommandRecords: (deviceKey, params) => api.get(`/devices/${deviceKey}/commands`, { params })
}
