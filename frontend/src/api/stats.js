import api from './axios'

export const statsApi = {
  /**
   * 获取仪表盘概览数据
   */
  getOverview: () => api.get('/analytics/overview'),

  /**
   * 获取设备状态分布
   */
  getDeviceStatusDistribution: () => api.get('/analytics/devices/status'),

  /**
   * 获取设备类型分布
   */
  getDeviceTypeDistribution: () => api.get('/analytics/devices/type'),

  /**
   * 获取消息量趋势
   * @param {Number} days - 天数，默认7
   */
  getMessageTrend: (days = 7) => api.get('/analytics/messages/trend', { params: { days } })
}
